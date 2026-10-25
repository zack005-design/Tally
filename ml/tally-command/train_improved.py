"""Scratch 1M training with development-only checkpoint selection and frozen new tests."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import time
import torch
from torch.nn import functional as F
from command_data import tokenize, decode_spans, unknown_description_allowed
from command_model import CommandModel, build_vocabulary, export_portable, size_profile, infer
from command_v4_data import make_corpus_v4
from train_command import pack, select_threshold, evaluate


def development_score(summary):
    return summary['accepted_count'] if summary['accepted_count'] >= 20 and summary['accepted_precision'] == 1.0 else -1


@torch.inference_mode()
def batched_evaluate(model, vocabulary, rows, threshold):
    model.eval()
    results = []
    device = next(model.parameters()).device
    for offset in range(0, len(rows), 128):
        batch = rows[offset:offset + 128]
        x, _, _ = pack(batch, vocabulary)
        tags, intents = model(x.to(device))
        probabilities = tags.softmax(-1).cpu()
        kinds = intents.softmax(-1).cpu()
        for i, row in enumerate(batch):
            tokens = tokenize(row['text'])
            certainties, labels = probabilities[i, :len(tokens)].max(-1)
            labels = labels.tolist()
            candidate = None
            confidence = 0.0
            if len(row['text']) <= 240 and not any(c.isalpha() and not c.isascii() for c in row['text']) and unknown_description_allowed(tokens, labels, vocabulary):
                candidate = decode_spans(row['text'], tokens, labels)
                relevant = [float(c) for c, label in zip(certainties, labels) if label != 0]
                confidence = min(float(kinds[i, 1]), min(relevant) if relevant else 0.0)
            draft = candidate if confidence >= threshold else None
            results.append({'candidate': candidate, 'draft': draft, 'confidence': confidence,
                            'expected': row['draft'], 'text': row['text'], 'correct': draft == row['draft']})
    accepted = [r for r in results if r['draft'] is not None]
    positives = [r for r in results if r['expected'] is not None]
    negatives = [r for r in results if r['expected'] is None]
    return {'accepted_count': len(accepted), 'accepted_precision': sum(r['correct'] for r in accepted) / max(1, len(accepted)),
            'positive_count': len(positives), 'negative_count': len(negatives), 'count': len(rows),
            'correct_positive_rate': sum(r['correct'] for r in positives) / max(1, len(positives)),
            'negative_false_accepts': sum(r['draft'] is not None for r in negatives), 'results': results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--steps', type=int, default=8000)
    parser.add_argument('--seed', type=int, default=29173)
    args = parser.parse_args()
    if args.steps < 1000 or args.run_dir.exists():
        parser.error('Use >=1000 steps and a fresh run directory')
    torch.set_num_threads(4); torch.manual_seed(args.seed); random.seed(args.seed)
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    train, dev, test = make_corpus_v4(seed=args.seed)
    dataset = json.dumps({'train': train, 'dev': dev, 'test': test}, ensure_ascii=False)
    args.run_dir.mkdir(parents=True)
    (args.run_dir / 'dataset.json').write_text(dataset, encoding='utf-8')
    vocabulary = build_vocabulary(train)
    embedding, hidden = size_profile(len(vocabulary), 1000000)
    model = CommandModel(len(vocabulary), embedding, hidden).to(device)
    x, y, intent = [t.to(device) for t in pack(train, vocabulary)]
    optimizer = torch.optim.AdamW(model.parameters(), lr=.0006, weight_decay=.02)
    best_score, best_state = -1, None
    started = time.perf_counter(); history = []
    print(json.dumps({'parameters': sum(p.numel() for p in model.parameters()), 'embedding': embedding, 'hidden': hidden, 'device': str(device), 'dataset_sha256': hashlib.sha256(dataset.encode()).hexdigest()}), flush=True)
    for step in range(1, args.steps + 1):
        model.train()
        for group in optimizer.param_groups:
            group['lr'] = .00003 + .00057 * .5 * (1 + math.cos(math.pi * step / args.steps))
        indices = torch.randint(len(x), (128,), device=device)
        a, b, c = x[indices].clone(), y[indices], intent[indices]
        length = int((a != 0).sum(1).max().item()); a, b = a[:, :length], b[:, :length]
        a[(torch.rand_like(a.float()) < .15) & (b == 1)] = 1
        a[(torch.rand_like(a.float()) < .02) & (b == 0) & (a != 0)] = 1
        optimizer.zero_grad(set_to_none=True)
        tags, kinds = model(a)
        loss = F.cross_entropy(tags.flatten(0, 1), b.flatten(), ignore_index=-100) + F.cross_entropy(kinds, c)
        if not torch.isfinite(loss): raise RuntimeError('Non-finite loss')
        loss.backward(); torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0); optimizer.step()
        if step % 500 == 0 or step == 1:
            print(json.dumps({'step': step, 'loss': float(loss.detach()), 'seconds': time.perf_counter() - started}), flush=True)
        if step % 1000 == 0 or step == args.steps:
            raw = batched_evaluate(model, vocabulary, dev, 0.0)
            threshold = select_threshold(raw['results'])
            summary = batched_evaluate(model, vocabulary, dev, threshold) if threshold is not None else raw
            score = development_score(summary) if threshold is not None else -1
            record = {'step': step, 'threshold': threshold, 'score': score, 'dev': {k: v for k, v in summary.items() if k != 'results'}}
            history.append(record); print(json.dumps(record), flush=True)
            if score > best_score:
                best_score = score
                best_state = {'state_dict': {k: v.detach().cpu().clone() for k, v in model.state_dict().items()},
                              'vocabulary': vocabulary, 'threshold': threshold, 'seed': args.seed, 'steps': step,
                              'embedding': embedding, 'hidden': hidden, 'expanded': True, 'pretrained_weights': False,
                              'guard_revision': 'description-boundary-v1', 'recipe': 'improved-v4'}
                torch.save(best_state, args.run_dir / 'model.pt')
    (args.run_dir / 'selection-history.json').write_text(json.dumps(history, indent=2), encoding='utf-8')
    if best_state is None: raise RuntimeError('No checkpoint met the development gate; refusing export')
    chosen = CommandModel(len(vocabulary), embedding, hidden).eval()
    chosen.load_state_dict(best_state['state_dict'])
    with (args.run_dir / 'command.bin').open('wb') as stream: export_portable(chosen, vocabulary, best_state['threshold'], stream)
    # Test evaluation occurs only after checkpoint selection is frozen.
    report = {'selected_step': best_state['steps'], 'parameters': sum(p.numel() for p in chosen.parameters()),
              'threshold': best_state['threshold'], 'training_seconds': time.perf_counter() - started,
              'dataset_sha256': hashlib.sha256(dataset.encode()).hexdigest(),
              'heldout': evaluate(chosen, vocabulary, test, best_state['threshold'])}
    (args.run_dir / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: ({a: b for a, b in v.items() if a != 'results'} if isinstance(v, dict) else v) for k, v in report.items()}), flush=True)


if __name__ == '__main__': main()
