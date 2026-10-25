"""Randomly initialized compact span model plus a runtime-independent export."""
import struct
import math
import torch
from torch import nn
from torch.nn.utils.rnn import pack_padded_sequence, pad_packed_sequence
from command_data import tokenize, decode_spans, unknown_description_allowed


def size_profile(vocabulary_size, target):
    """Closest supported GRU width; no unused padding parameters to hit a headline size."""
    if target not in (100000, 500000, 1000000) or not 2 <= vocabulary_size <= 4096:
        raise ValueError('Unsupported target or vocabulary size')
    embedding = 64 if target == 100000 else 128
    def count(hidden):
        return vocabulary_size * embedding + 6 * hidden * (embedding + hidden) + 24 * hidden + 6
    hidden = min(range(1, 513), key=lambda width: abs(count(width) - target))
    return embedding, hidden


class CommandModel(nn.Module):
    def __init__(self, vocabulary_size, embedding=48, hidden=64):
        super().__init__()
        self.embedding_size, self.hidden_size = embedding, hidden
        self.embedding = nn.Embedding(vocabulary_size, embedding, padding_idx=0)
        self.encoder = nn.GRU(embedding, hidden, batch_first=True, bidirectional=True)
        self.tags = nn.Linear(hidden * 2, 4)
        self.intent = nn.Linear(hidden * 2, 2)

    def forward(self, tokens):
        lengths = (tokens != 0).sum(1)
        if (lengths == 0).any():
            raise ValueError('Empty token sequence')
        packed = pack_padded_sequence(self.embedding(tokens), lengths.cpu(), batch_first=True, enforce_sorted=False)
        encoded, _ = self.encoder(packed)
        encoded, _ = pad_packed_sequence(encoded, batch_first=True, total_length=tokens.shape[1])
        pooled = encoded.sum(1) / lengths[:, None]
        return self.tags(encoded), self.intent(pooled)


def build_vocabulary(rows):
    words = sorted({token.normalized for row in rows for token in tokenize(row['text'])})
    return {word: i for i, word in enumerate(['<pad>', '<unknown>'] + words)}


def vectorize(tokens, vocabulary):
    return [vocabulary.get(token.normalized, 1) for token in tokens]


@torch.inference_mode()
def infer(model, vocabulary, text, threshold=0.95, enhanced=True):
    tokens = tokenize(text)
    if not tokens or len(tokens) > 48 or len(text) > 240:
        return {'draft': None, 'candidate': None, 'confidence': 0.0, 'reason': 'length'}
    if any(char.isalpha() and not char.isascii() for char in text):
        return {'draft': None, 'candidate': None, 'confidence': 0.0, 'reason': 'unsupported_alphabet'}
    ids = torch.tensor([vectorize(tokens, vocabulary)], device=next(model.parameters()).device)
    model.eval()
    tag_logits, intent_logits = model(ids)
    probabilities = tag_logits[0].softmax(-1)
    certainty, labels = probabilities.max(-1)
    tags = labels.tolist()
    if not unknown_description_allowed(tokens, tags, vocabulary):
        return {'draft': None, 'candidate': None, 'confidence': 0.0, 'reason': 'unknown_word_outside_anchored_description'}
    candidate = decode_spans(text, tokens, tags, enhanced=enhanced)
    relevant = certainty[labels != 0]
    confidence = min(intent_logits.softmax(-1)[0, 1].item(), relevant.min().item() if len(relevant) else 0.0)
    return {'draft': candidate if confidence >= threshold else None, 'candidate': candidate,
            'confidence': confidence, 'reason': None if candidate and confidence >= threshold else 'review_required'}


def write_int(stream, value):
    stream.write(struct.pack('>i', value))


def write_string(stream, value):
    encoded = value.encode('utf-8')
    write_int(stream, len(encoded))
    stream.write(encoded)


def export_portable(model, vocabulary, threshold, stream):
    stream.write(b'TCMD')
    for value in (1, len(vocabulary), model.embedding_size, model.hidden_size):
        write_int(stream, value)
    stream.write(struct.pack('>f', threshold))
    for word, _ in sorted(vocabulary.items(), key=lambda pair: pair[1]):
        write_string(stream, word)
    weights = model.state_dict()
    write_int(stream, len(weights))
    for name, tensor in weights.items():
        write_string(stream, name)
        write_int(stream, tensor.ndim)
        for size in tensor.shape:
            write_int(stream, size)
        values = tensor.detach().cpu().flatten().tolist()
        stream.write(struct.pack('>' + 'f' * len(values), *values))


def load_portable(stream):
    def integer():
        return struct.unpack('>i', stream.read(4))[0]

    def text():
        size = integer()
        if not 0 <= size <= 1000:
            raise ValueError('Invalid string size')
        return stream.read(size).decode('utf-8')

    if stream.read(4) != b'TCMD' or integer() != 1:
        raise ValueError('Unsupported model format')
    size, embedding, hidden = integer(), integer(), integer()
    if not (2 <= size <= 4096 and 1 <= embedding <= 128 and 1 <= hidden <= 512):
        raise ValueError('Invalid model dimensions')
    threshold = struct.unpack('>f', stream.read(4))[0]
    if not math.isfinite(threshold) or not 0 <= threshold <= 1:
        raise ValueError('Invalid threshold')
    vocabulary = {text(): i for i in range(size)}
    if len(vocabulary) != size or vocabulary.get('<pad>') != 0 or vocabulary.get('<unknown>') != 1:
        raise ValueError('Invalid vocabulary')
    model = CommandModel(size, embedding, hidden)
    expected = model.state_dict()
    if sum(t.numel() for t in expected.values()) > 1_500_000:
        raise ValueError('Model exceeds the research size limit')
    state = {}
    if integer() != len(expected):
        raise ValueError('Invalid tensor count')
    for _ in range(len(expected)):
        name = text()
        if name not in expected or name in state:
            raise ValueError('Unexpected or duplicate tensor')
        dimensions = integer()
        if dimensions not in (1, 2):
            raise ValueError('Invalid tensor rank')
        shape = tuple(integer() for _ in range(dimensions))
        if shape != tuple(expected[name].shape):
            raise ValueError('Invalid tensor shape')
        count = 1
        for dim in shape:
            count *= dim
        if not 1 <= count <= 1_000_000:
            raise ValueError('Invalid tensor size')
        values = struct.unpack('>' + 'f' * count, stream.read(count * 4))
        if not all(math.isfinite(value) for value in values):
            raise ValueError('Non-finite weights')
        state[name] = torch.tensor(values).reshape(shape)
    if stream.read(1):
        raise ValueError('Trailing model data')
    model.load_state_dict(state)
    return model.eval(), vocabulary, threshold
