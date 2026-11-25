"""Scratch hashed character n-gram category suggester (no pretrained weights or data).

Features: each lowercase word, its character 3-5-grams with boundary marks, and adjacent
word pairs, hashed with 32-bit FNV-1a into a fixed table. Logits are the sum of the hashed
rows divided by sqrt(feature count), plus a bias. The Kotlin runtime (CategoryModel.kt) reproduces this exactly.
"""
import struct
import torch
from torch import nn
from category_data import WORD

MAGIC = 0x54434154  # 'TCAT'
BUCKETS = 4096


def fnv1a(text):
    value = 0x811C9DC5
    for byte in text.encode('utf-8'):
        value = ((value ^ byte) * 0x01000193) & 0xFFFFFFFF
    return value


def features(description):
    words = WORD.findall(description.lower())[:12]
    names = []
    for word in words:
        names.append('w=' + word)
        marked = '<' + word + '>'
        for n in (3, 4, 5):
            names.extend('g=' + marked[i:i + n] for i in range(max(0, len(marked) - n + 1)))
    names.extend('b=' + left + '_' + right for left, right in zip(words, words[1:]))
    return [fnv1a(name) % BUCKETS for name in names]


class CategoryModel(nn.Module):
    def __init__(self, classes, buckets=BUCKETS):
        super().__init__()
        self.table = nn.EmbeddingBag(buckets, classes, mode='sum')
        self.bias = nn.Parameter(torch.zeros(classes))
        nn.init.normal_(self.table.weight, std=.01)

    def forward(self, indices, offsets, weights):
        return self.table(indices, offsets, per_sample_weights=weights) + self.bias


def pack(descriptions):
    indices, offsets, weights = [], [], []
    for text in descriptions:
        offsets.append(len(indices))
        found = features(text) or [0]
        indices.extend(found)
        weights.extend([len(found) ** -.5] * len(found))
    return torch.tensor(indices), torch.tensor(offsets), torch.tensor(weights)


@torch.inference_mode()
def predict(model, categories, description, threshold):
    if not features(description):
        return None, 0.0
    probabilities = model(*pack([description])).softmax(-1)[0]
    score, index = probabilities.max(-1)
    score = float(score)
    return (categories[int(index)] if score >= threshold else None), score


def export(model, categories, threshold, stream):
    stream.write(struct.pack('>iiiif', MAGIC, 1, BUCKETS, len(categories), threshold))
    for name in categories:
        data = name.encode('utf-8')
        stream.write(struct.pack('>i', len(data)) + data)
    weights = model.table.weight.detach().cpu().flatten().tolist()
    stream.write(struct.pack('>%df' % len(weights), *weights))
    bias = model.bias.detach().cpu().tolist()
    stream.write(struct.pack('>%df' % len(bias), *bias))


def load(stream):
    magic, version, buckets, classes, threshold = struct.unpack('>iiiif', stream.read(20))
    if magic != MAGIC or version != 1 or buckets != BUCKETS:
        raise ValueError('Unsupported category model')
    categories = []
    for _ in range(classes):
        (length,) = struct.unpack('>i', stream.read(4))
        categories.append(stream.read(length).decode('utf-8'))
    model = CategoryModel(classes, buckets)
    weights = struct.unpack('>%df' % (buckets * classes), stream.read(4 * buckets * classes))
    bias = struct.unpack('>%df' % classes, stream.read(4 * classes))
    if stream.read(1):
        raise ValueError('Trailing category model data')
    with torch.no_grad():
        model.table.weight.copy_(torch.tensor(weights).view(buckets, classes))
        model.bias.copy_(torch.tensor(bias))
    return model.eval(), categories, threshold
