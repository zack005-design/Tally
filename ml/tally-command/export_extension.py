"""Export a frozen, gate-verified extension without replacing compatibility weights."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import torch

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent


def validate_artifact_binding(summary, extension_sha256, baseline_sha256):
    return (summary.get('passed') is True and summary.get('model_sha256') == extension_sha256
            and summary.get('baseline_sha256') == baseline_sha256)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--evaluation-dir', type=Path, required=True)
    args = parser.parse_args()
    summary = json.loads((args.evaluation_dir / 'summary.json').read_text())
    source = args.run_dir / 'command.bin'
    if not summary.get('passed') or any(g['accepted_errors'] for g in summary['groups'].values()):
        parser.error('Candidate has not passed the frozen composite gate')
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    baseline_digest = hashlib.sha256((REPO / 'app/src/localAi/assets/tally-command.bin').read_bytes()).hexdigest()
    if not validate_artifact_binding(summary, digest, baseline_digest):
        parser.error('Evaluation does not match both model artifacts')
    state = torch.load(args.run_dir / 'model.pt', map_location='cpu', weights_only=True)
    if state.get('recipe') != 'improved-v4' or state.get('guard_revision') != 'description-boundary-v1':
        parser.error('Unsupported runtime policy')
    metadata = {'model': 'scratch bidirectional GRU expense-span extension', 'parameters': summary['extension_parameters'],
                'compatibility_parameters': summary['baseline_parameters'], 'combined_parameters': summary['extension_parameters'] + summary['baseline_parameters'],
                'embedding': state['embedding'], 'hidden': state['hidden'], 'threshold': state['threshold'],
                'seed': state['seed'], 'selected_step': state['steps'], 'pretrained_weights': False,
                'sha256': digest, 'compatibility_sha256': baseline_digest, 'bytes': source.stat().st_size, 'model_version': 'command-v4-1m',
                'guard_revision': state['guard_revision'], 'routing': 'compatibility first, extension on abstention',
                'evaluation': 'Synthetic templates and regression suites only; not independent real-user accuracy.',
                'data_provenance': 'Assistant-authored synthetic templates; no private financial records.'}
    assets = REPO / 'app/src/localAi/assets'
    shutil.copyfile(source, assets / 'tally-command-extended.bin')
    (assets / 'tally-command-extended.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    shutil.copyfile(args.evaluation_dir / 'parity.tsv', REPO / 'app/src/test/resources/command-extended-parity.tsv')
    print(json.dumps(metadata))
