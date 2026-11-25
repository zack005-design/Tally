"""Assistant v2: train (CPU or CUDA), select on development data, quantise to int8, gate, and export.

GPU (recommended, e.g. a 6 GB laptop card):
    python train_assistant_v2.py --run-dir runs/assistant-v2 --size large --export
CPU smoke test:
    python train_assistant_v2.py --run-dir runs/v2-smoke --size tiny --steps 600 --train-count 20000
Re-gate a finished run after a validator change (no training; thresholds are chosen again on development data):
    python train_assistant_v2.py --run-dir runs/assistant-v2 --regate --export

The gate runs on the int8 model exactly as shipped. Per first step, the answer threshold is the lowest with zero
wrong development answers (floor 0.5; a step never predicted on development data is never answered). The held-out
test is scored once afterwards; any wrong answer blocks --export. Follow-ups are evaluated with their previous turn.
"""
import argparse
import base64
import copy
import hashlib
import io
import json
import math
from pathlib import Path
import random
import time
import torch
from torch.nn import functional as F
from assistant_data_v2 import make_corpus, language_vocabulary, anonymise
from assistant_model import TinyLM, build_vocabulary, encode, generate, export, load, quantise_
from assistant_validate import valid

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
SIZES = {  # width, layers, heads, feed-forward
    'tiny': (128, 3, 4, 512),      # ~0.7M, pipeline checks only
    'small': (256, 6, 8, 1024),    # ~4.9M, CPU-friendly (v1 size)
    'medium': (384, 8, 8, 1536),   # ~14M
    'large': (512, 8, 8, 2048),    # ~25M, about 25 MB as int8
}
HEADS = ['spend', 'income', 'count', 'average', 'top', 'largest', 'budget', 'forecast', 'saveplan', 'savetime', 'whatif', 'help']
FILLERS = ['please', 'just', 'tell', 'me', 'so', 'far', 'my', 'the', 'total', 'how', 'much', 'can', 'you', 'in', 'on']
SYNONYMS = {'spend': ['spent', 'spending'], 'spent': ['spend'], 'biggest': ['largest'], 'largest': ['biggest'],
            'earn': ['receive'], 'receive': ['earn'], 'money': ['cash'], 'compare': ['vs'], 'vs': ['compare'],
            'expenses': ['spending'], 'spending': ['expenses'], 'more': ['higher'], 'higher': ['more']}


RULE_CASES = [
    ('', 'biggest spending areas this month', 'top this_month ; answer'),
    ('', 'biggest spending areas this month', 'largest this_month ; answer'),
    ('', 'biggest single expense this month', 'largest this_month ; answer'),
    ('', 'WORD1 kitni baar today', 'count today text WORD1 ; answer'),
    ('', 'WORD1 kitni baar today', 'spend today text WORD1 ; answer'),
    ('', 'to reach NUM1 in NUM2 months what do i need to put aside monthly', 'saveplan NUM1 months NUM2 ; answer'),
    ('', 'to reach NUM1 in NUM2 months what do i need to put aside monthly', 'savetime NUM1 per NUM2 ; answer'),
    ('', 'at NUM1 a month how many months for NUM2', 'savetime NUM2 per NUM1 ; answer'),
    ('', 'at NUM1 a month how many months for NUM2', 'savetime NUM1 per NUM2 ; answer'),
    ('', 'if i save NUM1 every month how long to reach NUM2', 'saveplan NUM2 months NUM1 ; answer'),
    ('', 'will i overspend this month', 'forecast ; answer'),
    ('spend this_month ; answer', 'order pizza for me', 'forecast ; answer'),
    ('spend last_year acc ACC1 ; answer', 'vs today', 'spend last_year acc ACC1 ; spend today acc ACC1 ; compare $1 $2 ; answer'),
    ('spend last_year acc ACC1 ; answer', 'vs today', 'spend today acc ACC1 ; spend today acc ACC1 ; compare $1 $2 ; answer'),
]


def augment(row, rng):
    source = []
    for t in row['source']:
        literal = t[0].isalpha() and t[:3] not in ('CAT', 'ACC', 'NUM') and not t.startswith('WORD')
        if literal and t in SYNONYMS and rng.random() < .2:
            t = rng.choice(SYNONYMS[t])
        if literal and rng.random() < .1:
            t = '<unk>'
        source.append(t)
    if rng.random() < .3:
        source.insert(rng.randrange(len(source) + 1), rng.choice(FILLERS))
    return {**row, 'source': source}


def batches(rows, index, size, rng, device):
    while True:
        encoded = [encode(augment(r, rng), index, with_context=True) for r in rng.sample(rows, size)]
        width = max(len(t) for t, _ in encoded)
        x = torch.zeros(size, width, dtype=torch.long); y = torch.full((size, width), -100, dtype=torch.long)
        for i, (tokens, start) in enumerate(encoded):
            x[i, :len(tokens)] = torch.tensor(tokens)
            y[i, start - 1:len(tokens) - 1] = torch.tensor(tokens[start:])
        yield x.to(device, non_blocking=True), y.to(device, non_blocking=True)


def score(model, vocab, index, rows):
    out = []
    for row in rows:
        program, confidence = generate(model, vocab, index, row['source'], context=row.get('context', []))
        out.append({'intent': row['intent'], 'head': program[0] if program else '', 'confidence': confidence,
                    'correct': program == row['program'], 'program': program,
                    'answered': program[:1] != ['unknown'] and valid(row['source'], program, row.get('context', []))})
    return out


def summary(rows, thresholds):
    answered = [r for r in rows if r['answered'] and r['confidence'] >= thresholds.get(r['head'], 1.01)]
    return {'count': len(rows), 'answered': len(answered), 'wrong_answers': sum(not r['correct'] for r in answered),
            'declined': len(rows) - len(answered), 'exact_program': sum(r['correct'] for r in rows)}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--size', choices=SIZES, default='large')
    parser.add_argument('--steps', type=int, default=30000)
    parser.add_argument('--batch', type=int, default=128)
    parser.add_argument('--lr', type=float, default=4e-4)
    parser.add_argument('--train-count', type=int, default=300000)
    parser.add_argument('--seed', type=int, default=9173)
    parser.add_argument('--device', default='auto', help='auto, cpu, cuda')
    parser.add_argument('--export', action='store_true', help='write the gated int8 model and fixtures into the app')
    parser.add_argument('--regate', action='store_true', help='re-run the gate on an existing run directory without training')
    args = parser.parse_args()
    if args.regate:
        return regate(args)
    if args.run_dir.exists():
        parser.error('Use a fresh run directory')
    device = torch.device('cuda' if args.device == 'auto' and torch.cuda.is_available() else 'cpu' if args.device == 'auto' else args.device)
    torch.manual_seed(args.seed); rng = random.Random(args.seed)
    if device.type == 'cpu':
        torch.set_num_threads(max(1, min(8, torch.get_num_threads())))
    started = time.perf_counter()
    train, dev, test, words = make_corpus(train_count=args.train_count, seed=args.seed)
    vocab = build_vocabulary(words); index = {t: i for i, t in enumerate(vocab)}
    d, layers, heads, ff = SIZES[args.size]
    model = TinyLM(len(vocab), d, layers, heads, ff).to(device)
    parameters = sum(p.numel() for p in model.parameters())
    args.run_dir.mkdir(parents=True)
    dataset = json.dumps({'train': train, 'dev': dev, 'test': test})
    (args.run_dir / 'dataset.json').write_text(dataset)
    info = {'parameters': parameters, 'size': args.size, 'device': str(device), 'vocab': len(vocab), 'train': len(train),
            'dataset_sha256': hashlib.sha256(dataset.encode()).hexdigest()}
    if device.type == 'cuda':
        info['gpu'] = torch.cuda.get_device_name(0)
    print(json.dumps(info), flush=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=.05, betas=(.9, .98))
    use_amp = device.type == 'cuda'
    amp_dtype = torch.bfloat16 if use_amp and torch.cuda.is_bf16_supported() else torch.float16
    scaler = torch.amp.GradScaler('cuda', enabled=use_amp and amp_dtype == torch.float16)
    stream = batches(train, index, args.batch, rng, device)
    probe = dev[:800]
    best, best_state, best_step, history = -1.0, None, 0, []
    every = max(500, args.steps // 15)
    for step in range(1, args.steps + 1):
        model.train()
        warm = min(1.0, step / 500)
        for group in optimizer.param_groups:
            group['lr'] = args.lr * warm * (.05 + .95 * .5 * (1 + math.cos(math.pi * step / args.steps)))
        x, y = next(stream)
        with torch.autocast(device.type, dtype=amp_dtype, enabled=use_amp):
            loss = F.cross_entropy(model(x).flatten(0, 1).float(), y.flatten(), ignore_index=-100)
        optimizer.zero_grad(set_to_none=True)
        scaler.scale(loss).backward(); scaler.unscale_(optimizer)
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        scaler.step(optimizer); scaler.update()
        if step % 250 == 0:
            print(json.dumps({'step': step, 'loss': round(float(loss), 5), 'seconds': round(time.perf_counter() - started)}), flush=True)
        if step % every == 0 or step == args.steps:
            model.eval()
            exact = sum(r['correct'] for r in score(model, vocab, index, probe)) / len(probe)
            history.append({'step': step, 'dev_exact': exact}); print(json.dumps(history[-1]), flush=True)
            if exact > best:
                best, best_step = exact, step
                best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}
    model = TinyLM(len(vocab), d, layers, heads, ff).eval()
    model.load_state_dict(best_state)
    torch.save({'state_dict': best_state, 'vocab': vocab, 'size': args.size, 'step': best_step}, args.run_dir / 'model.pt')
    info.update({'selected_step': best_step, 'history': history, 'training_seconds': round(time.perf_counter() - started)})
    report = gate_and_save(model, vocab, index, dev, test, words, args.run_dir, info, device)
    if args.export:
        export_to_app(args.run_dir, report)


def gate_and_save(model, vocab, index, dev, test, words, run_dir, info, device=torch.device('cpu')):
    quantise_(model)  # every gate number below is for the int8 model as shipped
    model.to(device)
    dev_scores = score(model, vocab, index, dev)
    thresholds = {}
    for head in HEADS:
        mine = [r for r in dev_scores if r['head'] == head and r['answered']]
        wrong = [r['confidence'] for r in mine if not r['correct']]
        thresholds[head] = max(.5, max(wrong) + 1e-6) if wrong else (.5 if mine else 1.01)
    test_scores = score(model, vocab, index, test)  # frozen thresholds; scored once
    gate = {'thresholds': thresholds, 'dev': summary(dev_scores, thresholds), 'test': summary(test_scores, thresholds),
            'test_by_intent': {i: summary([r for r in test_scores if r['intent'] == i], thresholds) for i in sorted({r['intent'] for r in test_scores})}}
    model.cpu()
    buffer = io.BytesIO(); export(model, vocab, min(thresholds.values()), buffer, words, thresholds, int8=True); blob = buffer.getvalue()
    (run_dir / 'assistant.bin').write_bytes(blob)
    report = {**info, 'bytes': len(blob), 'sha256': hashlib.sha256(blob).hexdigest(), 'gate': gate}
    (run_dir / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps({k: v for k, v in report.items() if k != 'history'}, indent=1), flush=True)
    return report


def regate(args):
    previous = json.loads((args.run_dir / 'report.json').read_text())
    data = json.loads((args.run_dir / 'dataset.json').read_text())
    checkpoint = torch.load(args.run_dir / 'model.pt')
    vocab = checkpoint['vocab']; index = {t: i for i, t in enumerate(vocab)}
    model = TinyLM(len(vocab), *SIZES[checkpoint['size']]).eval()
    model.load_state_dict(checkpoint['state_dict'])
    words = sorted(language_vocabulary())
    info = {k: v for k, v in previous.items() if k not in ('bytes', 'sha256', 'gate', 'regated_from')}
    info['regated_from'] = {'sha256': previous['sha256'], 'gate': previous['gate']}
    report = gate_and_save(model, vocab, index, data['dev'], data['test'], words, args.run_dir, info)
    if args.export:
        export_to_app(args.run_dir, report)


def export_to_app(run_dir, report):
    if report['gate']['test']['wrong_answers']:
        raise SystemExit('Held-out test has wrong answers; not exporting')
    blob = (run_dir / 'assistant.bin').read_bytes()
    model, vocab, _ = load(io.BytesIO(blob)); index = {t: i for i, t in enumerate(vocab)}
    data = json.loads((run_dir / 'dataset.json').read_text())
    rows = data['dev'][:80] + data['test'][:80] + [r for r in data['test'] if 'context' in r][:40]
    lines = []
    for row in rows:
        program, confidence = generate(model, vocab, index, row['source'], context=row.get('context', []))
        lines.append('\t'.join(base64.b64encode(' '.join(x).encode()).decode() for x in (row.get('context', []), row['source'], program))
                     + '\t' + repr(confidence))
    words = language_vocabulary()
    anon = []
    for text, categories, accounts, carried in [
            ('How much did I spend on Food last month?', ['Food', 'Eating out'], ['GPay'], {}),
            ('and travel?', ['Food', 'Travel'], ['GPay'], {'CAT1': 'Food'}),
            ('compare that with 30 days', [], [], {'NUM1': '7'}),
            ('save ₹1.5 lakh in 12 months, 12 months!', [], [], {}),
            ('foods paid with gpay over 2,000', ['Food'], ['GPay', 'Cash'], {'ACC1': 'GPay'}),
            ('Kitna kharch hua momos pe ₹50k', [], [], {'WORD1': 'momos'})]:
        result = anonymise(text, categories, accounts, words, carried)
        anon.append('\t'.join(base64.b64encode(x.encode()).decode() for x in
                              (text, '|'.join(categories), '|'.join(accounts), json.dumps(carried, sort_keys=True),
                               ' '.join(result[0]) if result else '-', json.dumps(result[1], sort_keys=True) if result else '-')))
    assets = REPO / 'app/src/localAi/assets'
    (assets / 'tally-assistant.bin').write_bytes(blob)
    metadata = {'model': 'scratch decoder-only transformer step planner (int8)', 'parameters': report['parameters'],
                'size': report['size'], 'vocab': len(vocab), 'width': model.d, 'layers': model.layers, 'heads': model.heads,
                'feed_forward': model.ff, 'context': model.max_len, 'thresholds': report['gate']['thresholds'],
                'selected_step': report['selected_step'], 'sha256': report['sha256'], 'bytes': len(blob),
                'gate': {k: report['gate'][k] for k in ('dev', 'test')}, 'trained_on': report.get('gpu', report['device']),
                'pretrained_weights': False, 'model_version': 'assistant-v2',
                'evaluation': 'Held-out assistant-authored question templates and follow-ups; not independent real-user accuracy.',
                'data_provenance': 'Assistant-authored synthetic questions; no private financial records.'}
    (assets / 'tally-assistant.json').write_text(json.dumps(metadata, indent=2) + '\n')
    resources = REPO / 'app/src/test/resources'
    (resources / 'assistant-parity.tsv').write_text('\n'.join(lines) + '\n')
    (resources / 'assistant-anonymise.tsv').write_text('\n'.join(anon) + '\n')
    rng = random.Random(5)
    cases = []
    for row in rng.sample(data['dev'], 200) + rng.sample(data['test'], 200):
        cases.append((row.get('context', []), row['source'], row['program']))
        broken = list(row['program'])
        if len(broken) > 3:
            del broken[rng.randrange(len(broken) - 2)]
        cases.append((row.get('context', []), row['source'], broken))
    for context, source, program in RULE_CASES:  # meaning and saving-role rules, both directions
        cases.append((context.split(), source.split(), program.split()))
    (resources / 'assistant-validate.tsv').write_text('\n'.join(
        '\t'.join(base64.b64encode(' '.join(x).encode()).decode() for x in (c, s, p)) + '\t' + str(valid(s, p, c)).lower()
        for c, s, p in cases) + '\n')
    print('exported', len(lines), 'parity,', len(anon), 'anonymise,', len(cases), 'validator cases')


if __name__ == '__main__':
    main()
