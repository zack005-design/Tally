import unittest


class CategoryTests(unittest.TestCase):
    def test_lexicon_has_no_ambiguous_or_unknown_labels(self):
        from category_data import CATEGORIES, terms
        rows = terms()
        self.assertGreater(len(rows), 400)
        self.assertTrue(all(c in CATEGORIES for _, c in rows))
        self.assertEqual(len({t for t, _ in rows}), len(rows))

    def test_evaluation_variants_never_repeat_training_strings(self):
        from category_data import make_splits
        train, dev, test = make_splits(5309)
        seen = {t.lower() for t, _ in train}
        self.assertFalse(seen & {t.lower() for t, c in dev if c})
        self.assertFalse(seen & {t.lower() for t, c in test if c})
        self.assertTrue(any(c is None for _, c in test))

    def test_hashing_and_export_round_trip(self):
        import io
        import torch
        from category_model import CategoryModel, features, fnv1a, export, load, predict
        self.assertEqual(fnv1a(''), 0x811C9DC5)
        self.assertEqual(fnv1a('a'), 0xE40C292C)
        self.assertEqual(features('1234'), [])
        self.assertEqual(len(features('Tea')), len(features('tea')))
        torch.manual_seed(1)
        model = CategoryModel(3).eval()
        buffer = io.BytesIO(); export(model, ['A', 'B', 'C'], .5, buffer)
        reloaded, names, threshold = load(io.BytesIO(buffer.getvalue()))
        self.assertEqual((names, threshold), (['A', 'B', 'C'], .5))
        self.assertAlmostEqual(predict(model, names, 'tea', 0)[1], predict(reloaded, names, 'tea', 0)[1], places=6)
        with self.assertRaises(ValueError):
            load(io.BytesIO(buffer.getvalue() + b'x'))


if __name__ == '__main__':
    unittest.main()
