"""Train the tiny reasoning model from scratch, select on development data, then score the held-out test once.

Usage: python train_assistant.py --run-dir runs/assistant-v1 [--steps 6000]; then export_assistant.py.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import random
import time
import torch
from torch.nn import functional as F
from assistant_data import make_corpus
from assistant_model import TinyLM, build_vocabulary, encode, generate, export, load

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent


FILLERS = ['please', 'just', 'tell', 'me', 'so', 'far', 'my', 'the', 'total', 'how', 'much', 'can', 'you', 'in', 'on']
SYNONYMS = {'spend': ['spent', 'spending'], 'spent': ['spend'], 'biggest': ['largest'], 'largest': ['biggest'],
            'earn': ['receive'], 'receive': ['earn'], 'money': ['cash'], 'compare': ['vs'], 'vs': ['compare'],
            'expenses': ['spending'], 'spending': ['expenses'], 'more': ['higher'], 'higher': ['more']}


def augment(row, index, rng):
    """Training-only noise: synonym swaps, word dropout to <unk>, and a random filler word."""
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


def batches(rows, index, size, rng):
    while True:
        chosen = rng.sample(rows, size)
        encoded = [encode(augment(r, index, rng), index) for r in chosen]
        width = max(len(t) for t, _ in encoded)
        x = torch.zeros(size, width, dtype=torch.long); y = torch.full((size, width), -100, dtype=torch.long)
        for i, (tokens, start) in enumerate(encoded):
            x[i, :len(tokens)] = torch.tensor(tokens)
            # Learn only the program (after <sep>), including <eos>.
            for j in range(start - 1, len(tokens) - 1):
                y[i, j] = tokens[j + 1]
        yield x, y


def evaluate(model, vocab, index, rows, threshold=0.0):
    results = []
    for row in rows:
        program, confidence = generate(model, vocab, index, row['source'])
        accepted = confidence >= threshold
        results.append({'text': row['text'], 'intent': row['intent'], 'expected': row['program'], 'program': program,
                        'confidence': confidence, 'accepted': accepted, 'correct': program == row['program']})
    accepted = [r for r in results if r['accepted']]
    return {'count': len(rows), 'exact': sum(r['correct'] for r in results) / len(rows),
            'accepted': len(accepted), 'accepted_errors': sum(not r['correct'] for r in accepted),
            'correct_accepted': sum(r['correct'] for r in accepted), 'results': results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--steps', type=int, default=6000)
    parser.add_argument('--seed', type=int, default=7331)
    args = parser.parse_args()
    if args.run_dir.exists():
        parser.error('Use a fresh run directory')
    torch.manual_seed(args.seed); torch.set_num_threads(4)
    rng = random.Random(args.seed)
    train, dev, test, words = make_corpus(seed=args.seed)
    vocab = build_vocabulary(words); index = {t: i for i, t in enumerate(vocab)}
    model = TinyLM(len(vocab))
    parameters = sum(p.numel() for p in model.parameters())
    dataset = json.dumps({'train': train, 'dev': dev, 'test': test})
    args.run_dir.mkdir(parents=True)
    (args.run_dir / 'dataset.json').write_text(dataset)
    print(json.dumps({'parameters': parameters, 'vocab': len(vocab), 'train': len(train), 'dataset_sha256': hashlib.sha256(dataset.encode()).hexdigest()}), flush=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=6e-4, weight_decay=.05, betas=(.9, .98))
    stream = batches(train, index, 64, rng)
    best, best_state, history, started = -1.0, None, [], time.perf_counter()
    dev_probe = dev[:600]
    for step in range(1, args.steps + 1):
        model.train()
        warm = min(1.0, step / 300)
        for group in optimizer.param_groups:
            group['lr'] = 6e-4 * warm * (.05 + .95 * .5 * (1 + math.cos(math.pi * step / args.steps)))
        x, y = next(stream)
        loss = F.cross_entropy(model(x).flatten(0, 1), y.flatten(), ignore_index=-100)
        optimizer.zero_grad(set_to_none=True); loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0); optimizer.step()
        if step % 250 == 0:
            print(json.dumps({'step': step, 'loss': round(float(loss), 5), 'seconds': round(time.perf_counter() - started)}), flush=True)
        if step % 1000 == 0 or step == args.steps:
            model.eval()
            score = evaluate(model, vocab, index, dev_probe)['exact']
            history.append({'step': step, 'dev_exact': score}); print(json.dumps(history[-1]), flush=True)
            if score > best:
                best = score
                best_state = {k: v.detach().clone() for k, v in model.state_dict().items()}; best_step = step
    model.load_state_dict(best_state); model.eval()
    # Threshold: the lowest minimum-token probability with zero accepted errors on the full development set.
    raw = evaluate(model, vocab, index, dev)
    wrong = [r['confidence'] for r in raw['results'] if not r['correct']]
    threshold = float(max(wrong) + 1e-6) if wrong else 0.0
    dev_summary = evaluate(model, vocab, index, dev, threshold)
    test_summary = evaluate(model, vocab, index, test, threshold)  # frozen; scored once
    buffer = io.BytesIO(); export(model, vocab, threshold, buffer); blob = buffer.getvalue()
    reloaded, _, _ = load(io.BytesIO(blob))
    (args.run_dir / 'assistant.bin').write_bytes(blob)
    strip = lambda s: {k: v for k, v in s.items() if k != 'results'}
    by_intent = {}
    for r in test_summary['results']:
        bucket = by_intent.setdefault(r['intent'], {'count': 0, 'correct_accepted': 0, 'accepted_errors': 0})
        bucket['count'] += 1; bucket['correct_accepted'] += r['accepted'] and r['correct']; bucket['accepted_errors'] += r['accepted'] and not r['correct']
    report = {'parameters': parameters, 'vocab': len(vocab), 'selected_step': best_step, 'threshold': threshold,
              'training_seconds': round(time.perf_counter() - started), 'bytes': len(blob), 'sha256': hashlib.sha256(blob).hexdigest(),
              'history': history, 'dev': strip(dev_summary), 'test': strip(test_summary), 'test_by_intent': by_intent,
              'test_errors': [r for r in test_summary['results'] if not r['correct']][:40]}
    (args.run_dir / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps({k: v for k, v in report.items() if k != 'test_errors'}, indent=1), flush=True)


if __name__ == '__main__':
    main()
