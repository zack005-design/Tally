import unittest


class ImprovedCorpusTests(unittest.TestCase):
    def test_export_gate_binds_both_model_artifacts(self):
        from export_extension import validate_artifact_binding
        summary = {'passed': True, 'model_sha256': 'extension', 'baseline_sha256': 'baseline'}
        self.assertTrue(validate_artifact_binding(summary, 'extension', 'baseline'))
        self.assertFalse(validate_artifact_binding(summary, 'extension', 'changed'))
        self.assertFalse(validate_artifact_binding(summary, 'changed', 'baseline'))

    def test_legacy_policy_stays_separate_from_enhanced_guards(self):
        from command_data import decode_spans, tokenize
        text = 'coffee 45 gpy'
        self.assertIsNone(decode_spans(text, tokenize(text), [1, 2, 3], enhanced=False))
        self.assertEqual(decode_spans(text, tokenize(text), [1, 2, 3], enhanced=True)['account'], 'GPAY')
        text = "today's entry: laundry 1 paid with gpay"
        labels = [0, 1, 0, 0, 2, 0, 0, 3]
        self.assertEqual(decode_spans(text, tokenize(text), labels, enhanced=False)['description'], 'entry')
        self.assertIsNone(decode_spans(text, tokenize(text), labels, enhanced=True))

    def test_new_splits_are_disjoint_and_include_capabilities(self):
        from command_v4_data import make_corpus_v4, signature
        corpus = make_corpus_v4(1000, 100, 100, seed=29173)
        self.assertEqual(corpus, make_corpus_v4(1000, 100, 100, seed=29173))
        for a, b in ((0, 1), (0, 2), (1, 2)):
            self.assertFalse({r['text'].lower() for r in corpus[a]} & {r['text'].lower() for r in corpus[b]})
            self.assertFalse({r['template'] for r in corpus[a]} & {r['template'] for r in corpus[b]})
            self.assertFalse({signature(r['template']) for r in corpus[a]} & {signature(r['template']) for r in corpus[b]})
        for rows in corpus:
            self.assertTrue(any(r.get('language') == 'hinglish' and r['intent'] for r in rows))
            self.assertTrue(any(r['draft'] and r['draft']['account'] == 'GPAY' for r in rows))

    def test_checkpoint_selection_requires_precision_then_improves_coverage(self):
        from train_improved import development_score
        self.assertEqual(development_score({'accepted_count': 25, 'accepted_precision': 1.0}), 25)
        self.assertEqual(development_score({'accepted_count': 2000, 'accepted_precision': .999}), -1)
        self.assertEqual(development_score({'accepted_count': 19, 'accepted_precision': 1.0}), -1)

    def test_batched_development_matches_scalar_inference(self):
        import torch
        from command_model import CommandModel, build_vocabulary, infer
        from command_v4_data import make_corpus_v4
        from train_improved import batched_evaluate
        torch.set_num_threads(2); torch.manual_seed(3)
        rows = make_corpus_v4(30, 10, 10)[0]
        vocabulary = build_vocabulary(rows)
        model = CommandModel(len(vocabulary)).eval()
        batch = batched_evaluate(model, vocabulary, rows, .8)
        for row, result in zip(rows, batch['results']):
            scalar = infer(model, vocabulary, row['text'], .8)
            self.assertEqual(result['candidate'], scalar['candidate'])
            self.assertEqual(result['draft'], scalar['draft'])
            self.assertAlmostEqual(result['confidence'], scalar['confidence'], places=6)


if __name__ == '__main__':
    unittest.main()
