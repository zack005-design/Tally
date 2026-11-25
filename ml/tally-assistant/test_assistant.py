import io
import unittest
import torch
import assistant_data as d
from assistant_model import TinyLM, build_vocabulary, export, load, generate


class AssistantDataTests(unittest.TestCase):
    def test_corpus_is_deterministic_and_template_disjoint(self):
        a = d.make_corpus(800, 120, 120, seed=11)
        self.assertEqual(a, d.make_corpus(800, 120, 120, seed=11))
        train, dev, test, _ = a
        self.assertFalse({r['text'] for r in train} & {r['text'] for r in test})
        for name, templates in d.INTENTS.items():
            held = [t for i, (t, _) in enumerate(templates) if d.split_of(i, len(templates)) == 'test']
            trained = [t for i, (t, _) in enumerate(templates) if d.split_of(i, len(templates)) == 'train']
            self.assertFalse(set(held) & set(trained), name)

    def test_anonymiser_masks_names_numbers_and_unknown_words(self):
        words = d.language_vocabulary()
        tokens, slots = d.anonymise('How much did I spend on Eating out and momos over ₹2,000 last month?', ['Eating out'], ['GPay'], words)
        self.assertEqual(' '.join(tokens), 'how much did i spend on CAT1 and WORD1 over NUM1 last month ?')
        self.assertEqual(slots, {'CAT1': 'Eating out', 'WORD1': 'momos', 'NUM1': '2,000'})
        self.assertEqual(d.anonymise('save 5k in 5k', [], [], words)[0].count('NUM1'), 2)
        self.assertIsNone(d.anonymise('a b c d e', [], [], set()))

    def test_programs_only_reference_present_slots(self):
        train, _, _, _ = d.make_corpus(1500, 10, 10, seed=3)
        for row in train:
            for token in row['program']:
                if token[:3] in ('CAT', 'ACC', 'NUM') or token.startswith('WORD'):
                    self.assertIn(token, row['source'], row['text'])


class AssistantModelTests(unittest.TestCase):
    def test_export_round_trip_matches_generation(self):
        torch.manual_seed(0)
        vocab = build_vocabulary(d.language_vocabulary())
        model = TinyLM(len(vocab), d=32, layers=2, heads=4, ff=64).eval()
        buffer = io.BytesIO(); export(model, vocab, .5, buffer, d.language_vocabulary())
        reloaded, vocab2, threshold = load(io.BytesIO(buffer.getvalue()))
        index = {t: i for i, t in enumerate(vocab)}
        self.assertEqual((vocab2, threshold), (vocab, .5))
        self.assertEqual(generate(model, vocab, index, ['how', 'much'], 5), generate(reloaded, vocab, index, ['how', 'much'], 5))
        with self.assertRaises(ValueError):
            load(io.BytesIO(buffer.getvalue() + b'x'))


if __name__ == '__main__':
    unittest.main()
