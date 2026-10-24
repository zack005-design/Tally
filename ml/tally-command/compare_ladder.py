"""Read-only model-ladder evaluation; never publishes weights into the app."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import time

import torch
from command_model import CommandModel, load_portable, infer
from train_command import evaluate

ROOT = Path(__file__).resolve().parent


def load_checkpoint(run):
    state = torch.load(run / 'model.pt', map_location='cpu', weights_only=True)
    model = CommandModel(len(state['vocabulary']), state.get('embedding', 48), state.get('hidden', 64))
    model.load_state_dict(state['state_dict'])
    return model.eval(), state


def compact(result):
    return {key: value for key, value in result.items() if key != 'results'}


def compare(runs):
    torch.set_num_threads(4)
    legacy = json.loads((ROOT / 'runs/command-v3/dataset.json').read_text(encoding='utf-8'))['test']
    safety = json.loads((ROOT / 'command_safety.json').read_text(encoding='utf-8'))
    old_challenge = json.loads((ROOT / 'challenge_v3.json').read_text(encoding='utf-8'))
    new_challenge_bytes = (ROOT / 'challenge_ladder.json').read_bytes()
    new_challenge = json.loads(new_challenge_bytes)
    rows = []
    expanded_sha = None
    for run in runs:
        model, state = load_checkpoint(run)
        dataset = json.loads((run / 'dataset.json').read_text(encoding='utf-8'))
        if state.get('expanded'):
            digest = hashlib.sha256((run / 'dataset.json').read_bytes()).hexdigest()
            if expanded_sha is not None and expanded_sha != digest:
                raise ValueError('Expanded runs must use identical data for comparison')
            expanded_sha = digest
        if state['threshold'] is None:
            report = json.loads((run / 'report.json').read_text(encoding='utf-8'))
            result = {'run': run.name, 'parameters': sum(p.numel() for p in model.parameters()),
                      'status': 'development_rejected', 'expanded': state.get('expanded', False),
                      'dev_raw': compact(report['dev_raw']), 'exported': False}
            rows.append(result)
            print(json.dumps(result), flush=True)
            continue
        with (run / 'command.bin').open('rb') as stream:
            restored, vocab, threshold = load_portable(stream)
        cases = dataset['test'][:30] + new_challenge
        for case in cases:
            original = infer(model, state['vocabulary'], case['text'], state['threshold'])
            portable = infer(restored, vocab, case['text'], threshold)
            if original['draft'] != portable['draft'] or abs(original['confidence'] - portable['confidence']) > 2e-6:
                raise AssertionError('Portable export parity mismatch: ' + case['text'])
        timings = []
        for _ in range(20):
            infer(model, state['vocabulary'], 'Lunch 180 GPay', state['threshold'])
        for _ in range(200):
            start = time.perf_counter()
            infer(model, state['vocabulary'], 'Lunch 180 GPay', state['threshold'])
            timings.append((time.perf_counter() - start) * 1000)
        result = {
            'run': run.name, 'parameters': sum(p.numel() for p in model.parameters()),
            'embedding': model.embedding_size, 'hidden': model.hidden_size,
            'threshold': state['threshold'], 'expanded': state.get('expanded', False),
            'model_bytes': (run / 'command.bin').stat().st_size,
            'python_cpu_p50_ms': statistics.median(timings), 'python_cpu_p95_ms': sorted(timings)[189],
            'portable_parity_cases': len(cases),
            'heldout': compact(evaluate(model, state['vocabulary'], dataset['test'], state['threshold'])),
            'legacy_heldout': compact(evaluate(model, state['vocabulary'], legacy, state['threshold'])),
            'legacy_challenge': compact(evaluate(model, state['vocabulary'], old_challenge, state['threshold'])),
            'new_capabilities': compact(evaluate(model, state['vocabulary'], new_challenge, state['threshold'])),
            'safety': compact(evaluate(model, state['vocabulary'], safety, state['threshold'])),
        }
        rows.append(result)
        print(json.dumps(result), flush=True)
    return {'challenge_sha256': hashlib.sha256(new_challenge_bytes).hexdigest(),
            'expanded_dataset_sha256': expanded_sha, 'runs': rows,
            'limits': ['Synthetic English and bounded Latin-script Hinglish only; not independently collected user language.',
                       'Incumbent is a Python rerun under current research guards, not a measurement of shipped Android behavior.',
                       'Incumbent uses older data; comparisons among expanded runs isolate size better.',
                       'Guardrails contribute to safety refusal results.', 'CPU timings are Python, not Android.']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('runs', nargs='+', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Choose a fresh output path')
    report = compare(args.runs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
