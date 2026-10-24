"""Apply current research guards and a development-only gate to a saved run."""
import argparse
import json
from pathlib import Path
import shutil
import torch
from command_model import export_portable
from compare_ladder import load_checkpoint
from train_command import evaluate, select_threshold

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Use a fresh calibrated run directory')
    torch.set_num_threads(4)
    model, state = load_checkpoint(args.run)
    dataset = json.loads((args.run / 'dataset.json').read_text(encoding='utf-8'))
    development = evaluate(model, state['vocabulary'], dataset['dev'], 0.0)
    threshold = select_threshold(development['results'])
    args.output.mkdir(parents=True)
    shutil.copyfile(args.run / 'dataset.json', args.output / 'dataset.json')
    state['threshold'] = threshold
    state['guard_revision'] = 'description-boundary-v1'
    torch.save(state, args.output / 'model.pt')
    report = {'status': 'development_passed' if threshold is not None else 'development_rejected',
              'source_run': args.run.name, 'threshold': threshold,
              'parameters': sum(p.numel() for p in model.parameters()), 'dev_raw': development,
              'guard_revision': state['guard_revision']}
    (args.output / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    if threshold is not None:
        with (args.output / 'command.bin').open('wb') as stream:
            export_portable(model, state['vocabulary'], threshold, stream)
    print(json.dumps({key: value for key, value in report.items() if key != 'dev_raw'}))
