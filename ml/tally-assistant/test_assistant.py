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


class AssistantV2Tests(unittest.TestCase):
    def test_v2_gold_programs_pass_the_validator_and_follow_ups_carry_names(self):
        import assistant_data_v2 as v2
        from assistant_validate import valid
        train, dev, test, _ = v2.make_corpus(4000, 400, 400, seed=3)
        for row in train + dev + test:
            self.assertTrue(valid(row['source'], row['program'], row.get('context', ())), row['text'])
        follow = [r for r in train if r['intent'].startswith('followup')]
        self.assertTrue(follow)
        self.assertTrue(any(t in r['context'] for r in follow for t in r['program'] if t.startswith('CAT')))

    def test_carried_placeholders_continue_numbering(self):
        import assistant_data_v2 as v2
        words = v2.language_vocabulary()
        tokens, slots = v2.anonymise('and travel?', ['Food', 'Travel'], [], words, {'CAT1': 'Food'})
        self.assertEqual(tokens, ['and', 'CAT2', '?'])
        self.assertEqual(slots, {'CAT1': 'Food', 'CAT2': 'Travel'})

    def test_int8_export_round_trip_is_exact(self):
        import assistant_data_v2 as v2
        from assistant_model import quantise_
        torch.manual_seed(1)
        words = v2.language_vocabulary(); vocab = build_vocabulary(words); index = {t: i for i, t in enumerate(vocab)}
        model = quantise_(TinyLM(len(vocab), d=32, layers=2, heads=4, ff=64).eval())
        buffer = io.BytesIO(); export(model, vocab, .5, buffer, words, {'spend': .5}, int8=True)
        reloaded, _, _ = load(io.BytesIO(buffer.getvalue()))
        source = ['how', 'much', 'did', 'i', 'spend']
        self.assertEqual(generate(model, vocab, index, source, 6, context=[]), generate(reloaded, vocab, index, source, 6, context=[]))
