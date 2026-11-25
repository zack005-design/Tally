"""Gate and export a trained assistant run into the app (format v2) with parity fixtures.

An answer is given only when the program passes assistant_validate.valid and its lowest token probability reaches
the threshold. The threshold is the lowest value with zero wrong answers on development data; the held-out test is
scored once afterwards and any wrong answer blocks export. A program of `unknown` is a refusal, not an answer.
Development rows whose own expected program fails validation (label noise) are excluded and counted.
"""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
from assistant_data import language_vocabulary, anonymise, CATEGORIES, ACCOUNTS
from assistant_model import load, export, generate
from assistant_validate import valid

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
ANONYMISE_CASES = [
    ('How much did I spend on Food last month?', ['Food', 'Eating out'], ['GPay']),
    ('did i spend more on eating out than food this month', ['Food', 'Eating out'], ['GPay']),
    ('how much did i spend on chai and chai this week', ['Food'], []),
    ('save ₹1.5 lakh in 12 months, 12 months!', [], []),
    ('foods paid with gpay over 2,000', ['Food'], ['GPay', 'Cash']),
    ('cat food budget left', ['Pets'], []),
    ('Kitna kharch hua momos pe ₹50k', [], []),
    ('a b c d e WORD', [], []),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    args = parser.parse_args()
    report = json.loads((args.run_dir / 'report.json').read_text())
    with (args.run_dir / 'assistant.bin').open('rb') as stream:
        model, vocab, _ = load(stream)
    words = language_vocabulary()
    index = {t: i for i, t in enumerate(vocab)}
    dataset = json.loads((args.run_dir / 'dataset.json').read_text())

    def score(rows):
        out = []
        for row in rows:
            program, confidence = generate(model, vocab, index, row['source'])
            out.append({'intent': row['intent'], 'head': program[0] if program else '', 'answered': valid(row['source'], program) and program[:1] != ['unknown'],
                        'confidence': confidence, 'correct': program == row['program'], 'refused': program[:1] == ['unknown']})
        return out
    noise = sum(not valid(r['source'], r['program']) for r in dataset['dev'])
    dev = score([r for r in dataset['dev'] if valid(r['source'], r['program'])])
    # One threshold per first step (spend, compare, budget, ...): the lowest with zero wrong dev answers for that
    # predicted step. A step never predicted on development data gets 1.01, so it is never answered.
    heads = ['spend', 'income', 'count', 'average', 'top', 'largest', 'budget', 'forecast', 'saveplan', 'savetime', 'whatif', 'help']
    thresholds = {}
    for head in heads:
        mine = [r for r in dev if r['head'] == head and r['answered']]
        wrong = [r['confidence'] for r in mine if not r['correct']]
        thresholds[head] = (max(wrong) + 1e-6 if wrong else 0.5) if mine else 1.01
    threshold = min(thresholds.values())
    test = score(dataset['test'])
    def summary(rows):
        answered = [r for r in rows if r['answered'] and r['confidence'] >= thresholds.get(r['head'], 1.01)]
        return {'count': len(rows), 'answered': len(answered), 'wrong_answers': sum(not r['correct'] for r in answered),
                'refused_or_declined': len(rows) - len(answered), 'exact_program': sum(r['correct'] for r in rows)}
    gate = {'thresholds': thresholds, 'dev_label_noise_excluded': noise, 'dev': summary(dev), 'test': summary(test),
            'test_by_intent': {i: summary([r for r in test if r['intent'] == i]) for i in sorted({r['intent'] for r in test})}}
    (args.run_dir / 'gate.json').write_text(json.dumps(gate, indent=2))
    print(json.dumps(gate, indent=1))
    if gate['test']['wrong_answers']:
        parser.error('Held-out test has wrong answers; not exporting')
    buffer = io.BytesIO(); export(model, vocab, threshold, buffer, words, thresholds); blob = buffer.getvalue()
    lines = []
    for row in dataset['dev'][:100] + dataset['test'][:100]:
        program, confidence = generate(model, vocab, index, row['source'])
        lines.append('\t'.join((base64.b64encode(' '.join(row['source']).encode()).decode(),
                                base64.b64encode(' '.join(program).encode()).decode(), repr(confidence))))
    anon = []
    for text, categories, accounts in ANONYMISE_CASES:
        result = anonymise(text, categories, accounts, words)
        anon.append('\t'.join(base64.b64encode(x.encode()).decode() for x in
                              (text, '|'.join(categories), '|'.join(accounts), ' '.join(result[0]) if result else '-',
                               json.dumps(result[1], sort_keys=True) if result else '-')))
    assets = REPO / 'app/src/localAi/assets'
    (assets / 'tally-assistant.bin').write_bytes(blob)
    metadata = {'model': 'scratch decoder-only transformer step planner', 'parameters': report['parameters'], 'vocab': len(vocab),
                'layers': model.layers, 'width': model.d, 'heads': model.heads, 'feed_forward': model.ff, 'context': 96,
                'thresholds': thresholds, 'selected_step': report['selected_step'], 'gate': {k: gate[k] for k in ('dev', 'test')}, 'sha256': hashlib.sha256(blob).hexdigest(),
                'bytes': len(blob), 'pretrained_weights': False, 'model_version': 'assistant-v1',
                'evaluation': 'Held-out assistant-authored question templates; not independent real-user accuracy.',
                'data_provenance': 'Assistant-authored synthetic questions; no private financial records.'}
    (assets / 'tally-assistant.json').write_text(json.dumps(metadata, indent=2) + '\n')
    resources = REPO / 'app/src/test/resources'
    (resources / 'assistant-parity.tsv').write_text('\n'.join(lines) + '\n')
    (resources / 'assistant-anonymise.tsv').write_text('\n'.join(anon) + '\n')
    print(json.dumps(metadata, indent=1)); print('parity', len(lines), 'anonymise', len(anon))


if __name__ == '__main__':
    main()
