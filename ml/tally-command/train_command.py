"""Train an app-oriented span model from scratch and report untouched tests."""
import argparse
import math
import hashlib
import json
from pathlib import Path
import random
import statistics
import time

import torch
from torch.nn import functional as F
from command_data import make_corpus, tokenize
from command_model import CommandModel, build_vocabulary, vectorize, infer, export_portable, size_profile

ROOT = Path(__file__).resolve().parent


def pack(rows, vocabulary):
    inputs, labels = [], []
    for row in rows:
        ids = vectorize(tokenize(row['text']), vocabulary)
        inputs.append(ids + [0] * (48 - len(ids)))
        labels.append(row['tags'] + [-100] * (48 - len(ids)))
    return torch.tensor(inputs), torch.tensor(labels), torch.tensor([r['intent'] for r in rows])


def evaluate(model, vocabulary, rows, threshold):
    results = []
    for row in rows:
        result = infer(model, vocabulary, row['text'], threshold)
        result.update(text=row['text'], expected=row['draft'])
        result['correct'] = result['draft'] == row['draft']
        results.append(result)
    positives = [r for r in results if r['expected'] is not None]
    negatives = [r for r in results if r['expected'] is None]
    accepted = [r for r in results if r['draft'] is not None]
    return {'count': len(rows), 'positive_count': len(positives), 'negative_count': len(negatives),
            'correct_positive_rate': sum(r['correct'] for r in positives) / max(1, len(positives)),
            'accepted_count': len(accepted),
            'accepted_precision': sum(r['correct'] for r in accepted) / max(1, len(accepted)),
            'negative_false_accepts': sum(r['draft'] is not None for r in negatives),
            'results': results}


def select_threshold(results):
    """Development-only gate: at least 20 accepted drafts and no accepted error."""
    for value in (.8, .9, .95, .97, .98, .99, .995, .999):
        accepted = [r for r in results if r['candidate'] is not None and r['confidence'] >= value]
        if len(accepted) >= 20 and all(r['candidate'] == r['expected'] for r in accepted):
            return value
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--steps', type=int, default=5000)
    parser.add_argument('--seed', type=int, default=2027)
    parser.add_argument('--target-parameters', type=int, choices=(100000, 500000, 1000000))
    parser.add_argument('--expanded', action='store_true', help='Include disjoint Hinglish and account-typo capability templates')
    parser.add_argument('--dataset', type=Path, help='Reuse a locally generated frozen corpus for controlled comparisons')
    parser.add_argument('--run-dir', type=Path, default=ROOT / 'runs' / 'command-v3')
    args = parser.parse_args()
    if args.steps <= 0:
        parser.error('Steps must be positive')
    if args.dataset and not args.expanded:
        parser.error('Frozen-corpus reuse requires --expanded research provenance')
    args.run_dir.mkdir(parents=True, exist_ok=True)
    if (args.run_dir / 'model.pt').exists():
        parser.error('Choose a fresh run directory')
    torch.set_num_threads(4)
    torch.manual_seed(args.seed)
    random.seed(args.seed)
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    if args.dataset:
        frozen = json.loads(args.dataset.read_text(encoding='utf-8'))
        train, dev, test = (frozen[key] for key in ('train', 'dev', 'test'))
        for rows in (train, dev, test):
            if not rows or any(not 1 <= len(row['tags']) <= 48 or len(tokenize(row['text'])) != len(row['tags']) for row in rows):
                parser.error('Invalid frozen corpus')
    else:
        train, dev, test = make_corpus(seed=args.seed, expanded=args.expanded)
    vocabulary = build_vocabulary(train)
    dimensions = size_profile(len(vocabulary), args.target_parameters) if args.target_parameters else (48, 64)
    model = CommandModel(len(vocabulary), *dimensions).to(device)
    print(json.dumps({'device': str(device), 'parameters': sum(p.numel() for p in model.parameters()),
                      'embedding': dimensions[0], 'hidden': dimensions[1], 'expanded': args.expanded}), flush=True)
    inputs, labels, intents = [t.to(device) for t in pack(train, vocabulary)]
    optimizer = torch.optim.AdamW(model.parameters(), lr=.002, weight_decay=.01)
    started = time.perf_counter()
    history = []
    for step in range(1, args.steps + 1):
        learning_rate = .0001 + .0019 * .5 * (1 + math.cos(math.pi * step / args.steps))
        for group in optimizer.param_groups:
            group['lr'] = learning_rate
        model.train()
        indices = torch.randint(len(inputs), (96,), device=device)
        x, y, intent = inputs[indices].clone(), labels[indices], intents[indices]
        length = int((x != 0).sum(1).max().item())
        x, y = x[:, :length], y[:, :length]
        # Unknown-word embedding learns contextual description extraction.
        x[(torch.rand_like(x.float()) < .20) & (y == 1)] = 1
        x[(torch.rand_like(x.float()) < .08) & (y == 0) & (x != 0)] = 1
        optimizer.zero_grad(set_to_none=True)
        tags, kind = model(x)
        loss = F.cross_entropy(tags.flatten(0, 1), y.flatten(), ignore_index=-100) + F.cross_entropy(kind, intent)
        if not torch.isfinite(loss):
            raise RuntimeError('Non-finite training loss')
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        if step == 1 or step % 300 == 0 or step == args.steps:
            record = {'step': step, 'loss': loss.item(), 'seconds': time.perf_counter() - started}
            history.append(record)
            print(json.dumps(record), flush=True)
    if device.type == 'cuda':
        torch.cuda.synchronize()
    training_seconds = time.perf_counter() - started
    model.cpu().eval()
    # Preserve research evidence even when development calibration refuses deployment.
    dataset = json.dumps({'train': train, 'dev': dev, 'test': test}, ensure_ascii=False)
    (args.run_dir / 'dataset.json').write_text(dataset, encoding='utf-8')
    state = {'state_dict': model.state_dict(), 'vocabulary': vocabulary, 'threshold': None,
             'pretrained_weights': False, 'seed': args.seed, 'steps': args.steps,
             'embedding': dimensions[0], 'hidden': dimensions[1], 'expanded': args.expanded}
    torch.save(state, args.run_dir / 'model.pt')
    # Choose abstention threshold using development data only, before test evaluation.
    dev_raw = evaluate(model, vocabulary, dev, 0.0)
    threshold = select_threshold(dev_raw['results'])
    if threshold is None:
        failed = {'status': 'development_rejected', 'parameters': sum(p.numel() for p in model.parameters()),
                  'seed': args.seed, 'steps': args.steps, 'training_seconds': training_seconds,
                  'embedding': dimensions[0], 'hidden': dimensions[1], 'history': history,
                  'expanded': args.expanded, 'pretrained_weights': False,
                  'dev_raw': dev_raw, 'dataset_sha256': hashlib.sha256(dataset.encode()).hexdigest()}
        (args.run_dir / 'report.json').write_text(json.dumps(failed, indent=2), encoding='utf-8')
        raise RuntimeError('Development evaluation found no acceptable threshold; refusing to export this run')
    state = {'state_dict': model.state_dict(), 'vocabulary': vocabulary, 'threshold': threshold,
             'pretrained_weights': False, 'seed': args.seed, 'steps': args.steps,
             'embedding': dimensions[0], 'hidden': dimensions[1], 'expanded': args.expanded}
    torch.save(state, args.run_dir / 'model.pt')
    with (args.run_dir / 'command.bin').open('wb') as stream:
        export_portable(model, vocabulary, threshold, stream)
    # Prior v1 cases are historical regression data, not a new independent test set.
    old = json.loads((ROOT / 'manual_eval.json').read_text(encoding='utf-8'))
    for row in old:
        row['draft']['description'] = row['draft']['description'].lower()
    for _ in range(10):
        infer(model, vocabulary, 'Lunch 180 GPay', threshold)
    timings = []
    for _ in range(100):
        start = time.perf_counter()
        infer(model, vocabulary, 'Lunch 180 GPay', threshold)
        timings.append((time.perf_counter() - start) * 1000)
    dataset = json.dumps({'train': train, 'dev': dev, 'test': test}, ensure_ascii=False)
    (args.run_dir / 'dataset.json').write_text(dataset, encoding='utf-8')
    # Preserve original description case for span extraction; comparisons use the input case.
    for row in old:
        if row['text'].startswith('Lunch'):
            row['draft']['description'] = 'Lunch'
        elif row['text'].startswith('Coffee'):
            row['draft']['description'] = 'Coffee'
        elif row['text'].startswith('Dinner'):
            row['draft']['description'] = 'Dinner'
    report = {'model': 'scratch bidirectional GRU span tagger; not a generative LLM',
              'parameters': sum(p.numel() for p in model.parameters()), 'seed': args.seed,
              'target_parameters': args.target_parameters, 'embedding': dimensions[0], 'hidden': dimensions[1],
              'device': str(device), 'expanded': args.expanded,
              'pretrained_weights': False, 'external_teacher_model_calls': False,
              'data_provenance': 'Coding-assistant-authored programmatic templates; no private records.',
              'training_examples': len(train), 'dev_examples': len(dev), 'threshold': threshold,
              'steps': args.steps, 'training_seconds': training_seconds, 'history': history,
              'dataset_sha256': hashlib.sha256(dataset.encode()).hexdigest(),
              'portable_bytes': (args.run_dir / 'command.bin').stat().st_size,
              'python_cpu_warm_p50_ms': statistics.median(timings),
              'python_cpu_warm_p95_ms': sorted(timings)[94],
              'heldout': evaluate(model, vocabulary, test, threshold),
              'historical_regressions': evaluate(model, vocabulary, old, threshold),
              'limits': ['Synthetic English and bounded Latin-script Hinglish INR commands only.' if args.expanded else 'Synthetic English INR commands only.', 'Threshold is an uncalibrated model score, not probability of correctness.',
                         'Held-out templates still share generator vocabulary.', 'No physical Android speed measured here.']}
    (args.run_dir / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    fixtures = []
    for row in test[:40] + old:
        result = infer(model, vocabulary, row['text'], threshold)
        fixtures.append({'text': row['text'], 'draft': result['draft'], 'confidence': result['confidence']})
    (args.run_dir / 'parity.json').write_text(json.dumps(fixtures, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k not in ('heldout', 'historical_regressions', 'history')}), flush=True)
    print(json.dumps({name: {k: v for k, v in report[name].items() if k != 'results'} for name in ('heldout', 'historical_regressions')}), flush=True)


if __name__ == '__main__':
    main()
