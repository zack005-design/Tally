import io
import struct
import subprocess
import sys
import tempfile
from pathlib import Path
import unittest
import torch

from command_model import CommandModel, build_vocabulary, vectorize, export_portable, load_portable
from command_data import tokenize


class CommandModelTests(unittest.TestCase):
    def test_frozen_corpus_requires_research_provenance_before_creating_output(self):
        cache = Path(__file__).resolve().parent / '.cache'
        cache.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=cache) as folder:
            output = Path(folder) / 'out'
            result = subprocess.run([sys.executable, str(Path(__file__).with_name('train_command.py')),
                                     '--dataset', str(Path(folder) / 'missing.json'), '--steps', '1',
                                     '--run-dir', str(output)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn('requires --expanded', result.stderr)
            self.assertFalse(output.exists())

    def test_development_gate_does_not_accept_wrong_or_tiny_subsets(self):
        from train_command import select_threshold
        good = {'candidate': {'amount_paise': 100}, 'expected': {'amount_paise': 100}, 'confidence': .99}
        bad = {'candidate': {'amount_paise': 200}, 'expected': {'amount_paise': 100}, 'confidence': 1.0}
        self.assertEqual(select_threshold([good] * 20), .8)
        self.assertIsNone(select_threshold([good] * 19))
        self.assertIsNone(select_threshold([good] * 20 + [bad]))

    def test_portable_loader_rejects_invalid_threshold_and_trailing_data(self):
        stream = io.BytesIO()
        export_portable(CommandModel(2), {'<pad>': 0, '<unknown>': 1}, .9, stream)
        blob = stream.getvalue()
        invalid = bytearray(blob)
        struct.pack_into('>f', invalid, 20, float('nan'))
        for value in (bytes(invalid), blob + b'extra'):
            with self.assertRaises(ValueError):
                load_portable(io.BytesIO(value))

    def test_size_profiles_are_close_to_target_and_export_dimensions(self):
        from command_model import size_profile
        for target in (100000, 500000, 1000000):
            embedding, hidden = size_profile(220, target)
            model = CommandModel(220, embedding, hidden).eval()
            count = sum(p.numel() for p in model.parameters())
            self.assertLess(abs(count - target) / target, .02)
            stream = io.BytesIO()
            vocabulary = {'<pad>': 0, '<unknown>': 1, **{f'word{i}': i for i in range(2, 220)}}
            export_portable(model, vocabulary, .9, stream)
            stream.seek(0)
            restored, _, _ = load_portable(stream)
            self.assertEqual((restored.embedding_size, restored.hidden_size), (embedding, hidden))

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
