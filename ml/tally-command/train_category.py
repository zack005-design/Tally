"""Train, gate and export the on-device category suggester from random weights.

The threshold is chosen on development terms only (zero accepted errors, highest coverage).
Held-out test terms are scored once, after the threshold is frozen. Use --export to copy
the gated bundle and parity fixture into the app.
"""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import random
import torch
from torch.nn import functional as F
from category_data import CATEGORIES, make_splits, terms, term_holdout
from category_model import CategoryModel, pack, predict, export, load

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent


def score(model, rows, threshold):
    results = []
    for text, expected in rows:
        predicted, confidence = predict(model, CATEGORIES, text, threshold)
        results.append({'text': text, 'expected': expected, 'predicted': predicted, 'confidence': confidence})
    accepted = [r for r in results if r['predicted'] is not None]
    return {'count': len(rows), 'accepted': len(accepted),
            'accepted_errors': sum(r['predicted'] != r['expected'] for r in accepted),
            'correct': sum(r['predicted'] == r['expected'] for r in results), 'results': results}


def fit(train, epochs=300):
    model = CategoryModel(len(CATEGORIES))
    optimizer = torch.optim.Adam(model.parameters(), lr=.05)
    uniform = torch.full((len(CATEGORIES),), 1 / len(CATEGORIES))
    targets = torch.stack([F.one_hot(torch.tensor(CATEGORIES.index(c)), len(CATEGORIES)).float() if c else uniform
                           for _, c in train])
    packed = pack([t for t, _ in train])
    for _ in range(epochs):
        optimizer.zero_grad()
        loss = F.cross_entropy(model(*packed), targets) + 1e-5 * model.table.weight.pow(2).sum()
        loss.backward(); optimizer.step()
    return model.eval()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--seed', type=int, default=5309)
    parser.add_argument('--export', action='store_true')
    args = parser.parse_args()
    torch.manual_seed(args.seed); random.seed(args.seed); torch.set_num_threads(4)
    train, dev, test = make_splits(args.seed)
    model = fit(train)
    model.eval()
    raw = score(model, dev, 0.0)
    threshold = None
    for candidate in sorted({round(r['confidence'], 4) for r in raw['results']}):
        summary = score(model, dev, candidate)
        if summary['accepted_errors'] == 0:
            threshold = candidate
            break
    if threshold is None or threshold < .5:
        threshold = max(threshold or 0, .5)
    dev_summary = score(model, dev, threshold)
    positives = sum(c is not None for _, c in dev)
    if dev_summary['accepted_errors'] or dev_summary['accepted'] < positives // 2:
        raise RuntimeError('Development gate failed: ' + json.dumps({k: v for k, v in dev_summary.items() if k != 'results'}))
    test_summary = score(model, test, threshold)  # frozen threshold, scored once
    train_summary = score(model, terms(), threshold)
    torch.manual_seed(args.seed)
    held_train, held = term_holdout(args.seed)
    holdout_summary = score(fit(held_train), held, threshold)
    buffer = io.BytesIO(); export(model, CATEGORIES, threshold, buffer)
    blob = buffer.getvalue()
    reloaded, categories, reloaded_threshold = load(io.BytesIO(blob))
    assert categories == list(CATEGORIES) and abs(reloaded_threshold - threshold) < 1e-6
    args.run_dir.mkdir(parents=True, exist_ok=False)
    (args.run_dir / 'category.bin').write_bytes(blob)
    report = {'parameters': sum(p.numel() for p in model.parameters()), 'threshold': threshold, 'bytes': len(blob),
              'sha256': hashlib.sha256(blob).hexdigest(), 'terms': len(terms()), 'seed': args.seed,
              'dev': {k: v for k, v in dev_summary.items() if k != 'results'},
              'test': {k: v for k, v in test_summary.items() if k != 'results'},
              'lexicon_terms': {k: v for k, v in train_summary.items() if k != 'results'},
              'unseen_term_diagnostic': {k: v for k, v in holdout_summary.items() if k != 'results'},
              'test_unknown_false_accepts': sum(r['expected'] is None and r['predicted'] is not None for r in test_summary['results']),
              'test_errors': [r for r in test_summary['results'] if r['predicted'] not in (None, r['expected'])]}
    (args.run_dir / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))
    if args.export:
        fixtures = [r for group in (dev_summary, test_summary) for r in group['results']] + [
            {'text': t, **dict(zip(('predicted', 'confidence'), predict(reloaded, categories, t, threshold)))}
            for t in ('chicken biryani', 'Uber ride to office', 'NETFLIX', 'monthly electricity bill', 'qwerty',
                      '', '1234', 'zomato order', 'blood test', 'paneer tikka masala')]
        lines = ['\t'.join((base64.b64encode(r['text'].encode()).decode(), r['predicted'] or '-', repr(r['confidence'])))
                 for r in fixtures]
        (REPO / 'app/src/localAi/assets/tally-category.bin').write_bytes(blob)
        metadata = {'model': 'scratch hashed character n-gram category suggester', 'parameters': report['parameters'],
                    'buckets': 4096, 'categories': list(CATEGORIES), 'threshold': threshold, 'seed': args.seed,
                    'sha256': report['sha256'], 'bytes': len(blob), 'pretrained_weights': False, 'model_version': 'category-v1',
                    'evaluation': 'Held-out variants of assistant-authored terms and unknown words only; unseen words are unreliable; not independent real-user accuracy.',
                    'data_provenance': 'Assistant-authored description lexicon; no private financial records.'}
        (REPO / 'app/src/localAi/assets/tally-category.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
        (REPO / 'app/src/test/resources/category-parity.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        print('exported', len(lines), 'parity cases')


if __name__ == '__main__': main()
