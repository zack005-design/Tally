import io
import unittest
import torch

from command_model import CommandModel, build_vocabulary, vectorize, export_portable, load_portable
from command_data import tokenize


class CommandModelTests(unittest.TestCase):
    def test_padding_does_not_change_predictions(self):
        torch.manual_seed(81)
        model = CommandModel(8).eval()
        short = torch.tensor([[2, 3, 4]])
        padded = torch.tensor([[2, 3, 4, 0, 0]])
        with torch.no_grad():
            a, ai = model(short)
            b, bi = model(padded)
        torch.testing.assert_close(a, b[:, :3])
        torch.testing.assert_close(ai, bi)

    def test_export_roundtrip_and_unknown_words(self):
        vocabulary = build_vocabulary([{'text': 'lunch 180 gpay'}])
        self.assertEqual(vectorize(tokenize('unknown 45 cash'), vocabulary)[0], 1)
        self.assertEqual(vectorize(tokenize('unknown 45 cash'), vocabulary)[1], vocabulary['<number>'])
        model = CommandModel(len(vocabulary)).eval()
        stream = io.BytesIO()
        export_portable(model, vocabulary, .95, stream)
        stream.seek(0)
        restored, restored_vocab, threshold = load_portable(stream)
        self.assertEqual(restored_vocab, vocabulary)
        self.assertAlmostEqual(threshold, .95, places=5)
        x = torch.tensor([vectorize(tokenize('lunch 180 gpay'), vocabulary)])
        with torch.no_grad():
            torch.testing.assert_close(model(x)[0], restored(x)[0])


if __name__ == '__main__':
    unittest.main()
