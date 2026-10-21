import unittest

from command_data import tokenize, amount_paise, make_corpus, decode_spans, unknown_description_allowed


class CommandDataTests(unittest.TestCase):
    def test_unknown_words_require_a_description_span_and_a_clear_anchor(self):
        vocabulary = {'paid': 2, '<number>': 3, 'for': 4, 'using': 5, 'cash': 6, 'gpay': 7, 'coffee': 8}
        self.assertTrue(unknown_description_allowed(tokenize('paid 45 for ZestBistro using cash'), [0, 2, 0, 1, 0, 3], vocabulary))
        self.assertTrue(unknown_description_allowed(tokenize('ZestBistro 45 gpay'), [1, 2, 3], vocabulary))
        self.assertFalse(unknown_description_allowed(tokenize('mystery paid 45 for coffee using cash'), [0, 0, 2, 0, 1, 0, 3], vocabulary))
        self.assertFalse(unknown_description_allowed(tokenize('mystery coffee using cash 45'), [1, 1, 0, 3, 2], vocabulary))

    def test_amounts_are_exact_and_invalid_inputs_rejected(self):
        for text, expected in [('45', 4500), ('0.01', 1), ('99.50', 9950),
                               ('1,234.56', 123456), ('1,23,456.78', 12345678)]:
            self.assertEqual(amount_paise(text), expected)
        for text in ('0', '-10', '1.234', '12,34', '1000000.01', 'NaN'):
            with self.assertRaises(ValueError):
                amount_paise(text)

    def test_spans_copy_original_words_and_money_without_generation(self):
        text = 'Paid 45 for Coffee using cash'
        tokens = tokenize(text)
        result = decode_spans(text, tokens, [0, 2, 0, 1, 0, 3])
        self.assertEqual(result, {'amount_paise': 4500, 'description': 'Coffee', 'account': 'WALLET'})

    def test_decoder_refuses_missing_or_ambiguous_fields(self):
        for text, tags in [('coffee 45', [1, 2]),
                           ('coffee 45 80 cash', [1, 2, 2, 3]),
                           ('coffee 45 cash gpay', [1, 2, 3, 3]),
                           ('coffee 45 cash google pay', [1, 2, 3, 0, 0]),
                           ('coffee 45 g pay wallet', [1, 2, 0, 0, 3]),
                           ('coffee -45 cash', [1, 0, 2, 3]),
                           ('coffee 45.001 cash', [1, 2, 3])]:
            self.assertIsNone(decode_spans(text, tokenize(text), tags))

    def test_corpus_is_repeatable_with_disjoint_templates_and_inputs(self):
        corpus = make_corpus(1000, 100, 100, seed=81)
        self.assertEqual(corpus, make_corpus(1000, 100, 100, seed=81))
        for left, right in ((0, 1), (0, 2), (1, 2)):
            self.assertFalse({x['text'] for x in corpus[left]} & {x['text'] for x in corpus[right]})
        self.assertFalse({x['template'] for x in corpus[0]} & {x['template'] for x in corpus[2]})
        for split in corpus:
            for row in split:
                if row['draft'] is not None:
                    self.assertEqual(decode_spans(row['text'], tokenize(row['text']), row['tags']), row['draft'])


if __name__ == '__main__':
    unittest.main()
