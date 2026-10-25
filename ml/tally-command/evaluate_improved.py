"""Evaluate a frozen 1M extension plus the exact bundled compatibility model."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import torch
from command_model import infer, load_portable

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    if args.output_dir.exists(): parser.error('Use a fresh output directory')
    torch.set_num_threads(4)
    with (REPO / 'app/src/localAi/assets/tally-command.bin').open('rb') as stream:
        baseline, bv, bt = load_portable(stream)
    with (args.run_dir / 'command.bin').open('rb') as stream:
        extension, ev, et = load_portable(stream)
    dataset = json.loads((args.run_dir / 'dataset.json').read_text(encoding='utf-8'))
    legacy = json.loads((ROOT / 'runs/command-v3/dataset.json').read_text(encoding='utf-8'))['test']
    groups = {'fresh_heldout': dataset['test'], 'legacy_heldout': legacy,
              'legacy_challenge': json.loads((ROOT / 'challenge_v3.json').read_text(encoding='utf-8')),
              'new_capabilities': json.loads((ROOT / 'challenge_ladder.json').read_text(encoding='utf-8')),
              'safety': json.loads((ROOT / 'command_safety.json').read_text(encoding='utf-8'))}
    summary = {}; full = {}; fixtures = []
    for name, rows in groups.items():
        results = []; baseline_correct = 0
        for row in rows:
            old = infer(baseline, bv, row['text'], bt, enhanced=False)
            new = old if old['draft'] is not None else infer(extension, ev, row['text'], et, enhanced=True)
            source = 'baseline' if old['draft'] is not None else ('extension' if new['draft'] is not None else 'none')
            correct = new['draft'] == row['draft']
            baseline_correct += old['draft'] == row['draft'] and row['draft'] is not None
            results.append({'text': row['text'], 'expected': row['draft'], 'draft': new['draft'], 'confidence': new['confidence'], 'source': source, 'correct': correct})
        positives = [r for r in results if r['expected'] is not None]
        accepted = [r for r in results if r['draft'] is not None]
        summary[name] = {'count': len(rows), 'positives': len(positives), 'baseline_correct_positives': baseline_correct,
                         'correct_positives': sum(r['correct'] for r in positives),
                         'accepted_errors': sum(not r['correct'] for r in accepted),
                         'negative_false_accepts': sum(r['draft'] is not None for r in results if r['expected'] is None),
                         'extension_accepts': sum(r['source'] == 'extension' for r in results)}
        full[name] = results
        fixtures.extend(results[:150] if name.endswith('heldout') else results)
        print(json.dumps({name: summary[name]}), flush=True)
    # No test-driven threshold tuning: a failing gate blocks promotion of this frozen candidate.
    passed = all(v['accepted_errors'] == 0 and v['correct_positives'] >= v['baseline_correct_positives'] for v in summary.values())
    passed = passed and summary['new_capabilities']['correct_positives'] >= 10
    args.output_dir.mkdir(parents=True)
    report = {'passed': passed, 'extension_parameters': sum(p.numel() for p in extension.parameters()),
              'baseline_sha256': hashlib.sha256((REPO / 'app/src/localAi/assets/tally-command.bin').read_bytes()).hexdigest(),
              'baseline_parameters': sum(p.numel() for p in baseline.parameters()), 'groups': summary,
              'model_sha256': hashlib.sha256((args.run_dir / 'command.bin').read_bytes()).hexdigest(),
              'fixture_cases': len(fixtures)}
    (args.output_dir / 'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (args.output_dir / 'predictions.json').write_text(json.dumps(full, indent=2), encoding='utf-8')
    encode = lambda text: base64.b64encode(text.encode()).decode()
    lines = []
    for row in fixtures:
        d = row['draft']
        lines.append('\t'.join((encode(row['text']), str(d['amount_paise']) if d else '-', d['account'] if d else '-', encode(d['description']) if d else '-', str(row['confidence']), row['source'])))
    (args.output_dir / 'parity.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(json.dumps(report), flush=True)
    if not passed: raise RuntimeError('Frozen candidate failed the non-regression gate; do not promote')


if __name__ == '__main__': main()
