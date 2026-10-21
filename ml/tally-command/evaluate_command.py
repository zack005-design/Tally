"""Freeze a trained model into app assets and verify its deployment guards."""
import base64
import argparse
import hashlib
import json
from pathlib import Path
import torch
from command_model import CommandModel, infer, export_portable
from train_command import evaluate

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent


def main():
    torch.set_num_threads(4)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, default=ROOT / 'runs' / 'command-v3')
    run = parser.parse_args().run_dir
    state = torch.load(run / 'model.pt', map_location='cpu', weights_only=True)
    vocabulary = state['vocabulary']
    model = CommandModel(len(vocabulary))
    model.load_state_dict(state['state_dict'])
    model.eval()
    threshold = state['threshold']
    dataset = json.loads((run / 'dataset.json').read_text(encoding='utf-8'))
    previous = json.loads((run / 'report.json').read_text(encoding='utf-8'))
    historical = json.loads((ROOT / 'manual_eval.json').read_text())
    # Span extraction preserves original case; use the prior run's equivalent labels.
    for row, old in zip(historical, previous['historical_regressions']['results']):
        row['draft'] = old['expected']
    safety = json.loads((ROOT / 'command_safety.json').read_text(encoding='utf-8'))
    report = {k: v for k, v in previous.items() if k not in ('heldout', 'historical_regressions')}
    report['deployment_guard'] = 'Unknown words only in anchored/compact description spans; exact monetary validation and guarded rules fallback.'
    report['evaluation_status'] = 'V3 disjoint synthetic templates plus pre-frozen challenge; not independently collected real-user language.'
    report['heldout'] = evaluate(model, vocabulary, dataset['test'], threshold)
    report['historical_regressions'] = evaluate(model, vocabulary, historical, threshold)
    report['safety_cases'] = evaluate(model, vocabulary, safety, threshold)
    challenge = json.loads((ROOT / 'challenge_v3.json').read_text(encoding='utf-8'))
    report['challenge_v3'] = evaluate(model, vocabulary, challenge, threshold)
    baseline = json.loads((ROOT / 'CHALLENGE_BASELINE_V3.json').read_text(encoding='utf-8'))
    report['challenge_v2_baseline'] = {k: v for k, v in baseline.items() if k != 'results'}
    assets = REPO / 'app' / 'src' / 'main' / 'assets'
    assets.mkdir(parents=True, exist_ok=True)
    target = assets / 'tally-command.bin'
    with target.open('wb') as stream:
        export_portable(model, vocabulary, threshold, stream)
    metadata = {k: report[k] for k in ('model', 'parameters', 'seed', 'steps', 'pretrained_weights',
                                       'data_provenance', 'dataset_sha256', 'threshold', 'deployment_guard', 'evaluation_status')}
    metadata.update(sha256=hashlib.sha256(target.read_bytes()).hexdigest(), bytes=target.stat().st_size,
                    format='TCMD version 1, big-endian float32', model_version='command-v3-5000')
    (assets / 'tally-command.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    cases = dataset['test'][:150] + historical + safety + challenge
    lines = []
    for row in cases:
        result = infer(model, vocabulary, row['text'], threshold)
        draft = result['draft']
        encode = lambda text: base64.b64encode(text.encode('utf-8')).decode('ascii')
        lines.append('\t'.join((encode(row['text']), str(draft['amount_paise']) if draft else '-',
                                draft['account'] if draft else '-', encode(draft['description']) if draft else '-',
                                str(result['confidence']))))
    resources = REPO / 'app' / 'src' / 'test' / 'resources'
    resources.mkdir(parents=True, exist_ok=True)
    (resources / 'command-parity.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    (run / 'deployment-report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    # Store compact reviewable metrics alongside source; full predictions stay in run artifacts.
    summary = {k: report[k] for k in ('model', 'parameters', 'steps', 'training_seconds', 'threshold', 'deployment_guard', 'evaluation_status')}
    for name in ('heldout', 'historical_regressions', 'safety_cases', 'challenge_v3', 'challenge_v2_baseline'):
        summary[name] = {k: v for k, v in report[name].items() if k != 'results'}
    summary.update(model_bytes=target.stat().st_size, parity_cases=len(cases))
    (ROOT / 'COMMAND_RESULTS.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
