"""Tiny decoder-only transformer for Tally's reasoning steps, trained from random weights."""
import math
import struct
import torch
from torch import nn
from torch.nn import functional as F

SPECIALS = ['<pad>', '<bos>', '<sep>', '<eos>', '<unk>']
PLACEHOLDERS = [f'{kind}{i}' for kind in ('CAT', 'ACC', 'NUM', 'WORD') for i in range(1, 5)] + ['$1', '$2']
PROGRAM = ['spend', 'income', 'count', 'average', 'top', 'largest', 'compare', 'share', 'budget', 'forecast', 'saveplan',
           'savetime', 'whatif', 'help', 'unknown', 'answer', ';', 'cat', 'acc', 'text', 'n', 'months', 'per', 'cut', 'all',
           'today', 'yesterday', 'this_week', 'last_week', 'this_month', 'last_month', 'this_year', 'last_year', 'month',
           'last_days', 'add', 'split', 'backup', 'export', 'lock', 'receipt', 'privacy', '?', '%']
MAX_LEN = 96
MAGIC = 0x544C4D41  # 'TLMA'


def build_vocabulary(language_words):
    seen, vocab = set(), []
    for token in SPECIALS + PLACEHOLDERS + PROGRAM + sorted(language_words):
        if token not in seen:
            seen.add(token); vocab.append(token)
    return vocab


class Block(nn.Module):
    def __init__(self, d, heads, ff, dropout=0.1):
        super().__init__()
        self.drop = nn.Dropout(dropout)
        self.ln1 = nn.LayerNorm(d); self.qkv = nn.Linear(d, 3 * d); self.proj = nn.Linear(d, d)
        self.ln2 = nn.LayerNorm(d); self.up = nn.Linear(d, ff); self.down = nn.Linear(ff, d)
        self.heads = heads

    def forward(self, x, mask):
        b, t, d = x.shape
        q, k, v = self.qkv(self.ln1(x)).split(d, dim=-1)
        shape = lambda z: z.view(b, t, self.heads, d // self.heads).transpose(1, 2)
        q, k, v = shape(q), shape(k), shape(v)
        att = (q @ k.transpose(-2, -1)) / math.sqrt(d // self.heads)
        att = self.drop(att.masked_fill(mask[:t, :t] == 0, float('-inf')).softmax(-1))
        x = x + self.drop(self.proj((att @ v).transpose(1, 2).reshape(b, t, d)))
        return x + self.drop(self.down(F.gelu(self.up(self.ln2(x)), approximate='tanh')))


class TinyLM(nn.Module):
    def __init__(self, vocab_size, d=256, layers=6, heads=8, ff=1024):
        super().__init__()
        self.embed = nn.Embedding(vocab_size, d); self.position = nn.Embedding(MAX_LEN, d)
        self.blocks = nn.ModuleList(Block(d, heads, ff) for _ in range(layers))
        self.ln = nn.LayerNorm(d)
        self.register_buffer('mask', torch.tril(torch.ones(MAX_LEN, MAX_LEN)), persistent=False)
        self.d, self.layers, self.heads, self.ff = d, layers, heads, ff
        self.apply(self._init)

    @staticmethod
    def _init(module):
        if isinstance(module, (nn.Linear, nn.Embedding)):
            nn.init.normal_(module.weight, std=.02)
        if isinstance(module, nn.Linear) and module.bias is not None:
            nn.init.zeros_(module.bias)

    def forward(self, tokens):
        t = tokens.shape[1]
        x = self.embed(tokens) + self.position(torch.arange(t, device=tokens.device))
        for block in self.blocks:
            x = block(x, self.mask)
        return self.ln(x) @ self.embed.weight.T  # tied output projection


def encode(row, index):
    source = [index.get(t, index['<unk>']) for t in row['source']]
    program = [index[t] for t in row['program']]
    tokens = [index['<bos>']] + source + [index['<sep>']] + program + [index['<eos>']]
    return tokens[:MAX_LEN], len(source) + 2


@torch.inference_mode()
def generate(model, vocab, index, source_tokens, limit=40):
    """Greedy decoding. Returns (program tokens, minimum chosen-token probability)."""
    tokens = [index['<bos>']] + [index.get(t, index['<unk>']) for t in source_tokens] + [index['<sep>']]
    out, confidence = [], 1.0
    while len(out) < limit and len(tokens) < MAX_LEN:
        probabilities = model(torch.tensor([tokens]))[0, -1].softmax(-1)
        p, choice = probabilities.max(-1)
        confidence = min(confidence, float(p))
        if int(choice) == index['<eos>']:
            break
        out.append(vocab[int(choice)]); tokens.append(int(choice))
    return out, confidence


def export(model, vocab, threshold, stream, language_words=None, head_thresholds=None):
    """Version 2 also stores which vocabulary entries are ordinary question words (kept literal when anonymising)."""
    version = 1 if language_words is None else 2
    stream.write(struct.pack('>iiiiiiif', MAGIC, version, len(vocab), model.d, model.layers, model.heads, model.ff, threshold))
    stream.write(struct.pack('>i', MAX_LEN))
    for token in vocab:
        data = token.encode('utf-8'); stream.write(struct.pack('>i', len(data)) + data)
    if language_words is not None:
        indices = [i for i, token in enumerate(vocab) if token in language_words]
        stream.write(struct.pack('>i', len(indices)) + struct.pack('>%di' % len(indices), *indices))
        heads = sorted((head_thresholds or {}).items())
        stream.write(struct.pack('>i', len(heads)))
        for head, value in heads:
            stream.write(struct.pack('>if', vocab.index(head), value))
    tensors = [model.embed.weight, model.position.weight]
    for block in model.blocks:
        tensors += [block.ln1.weight, block.ln1.bias, block.qkv.weight, block.qkv.bias, block.proj.weight, block.proj.bias,
                    block.ln2.weight, block.ln2.bias, block.up.weight, block.up.bias, block.down.weight, block.down.bias]
    tensors += [model.ln.weight, model.ln.bias]
    for tensor in tensors:
        values = tensor.detach().cpu().flatten().tolist()
        stream.write(struct.pack('>i', len(values)))
        stream.write(struct.pack('>%df' % len(values), *values))


def load(stream):
    magic, version, size, d, layers, heads, ff, threshold = struct.unpack('>iiiiiiif', stream.read(32))
    if magic != MAGIC or version not in (1, 2):
        raise ValueError('Unsupported assistant model')
    (max_len,) = struct.unpack('>i', stream.read(4))
    vocab = []
    for _ in range(size):
        (length,) = struct.unpack('>i', stream.read(4)); vocab.append(stream.read(length).decode('utf-8'))
    if version == 2:
        (count,) = struct.unpack('>i', stream.read(4)); stream.read(4 * count)
        (count,) = struct.unpack('>i', stream.read(4)); stream.read(8 * count)
    model = TinyLM(size, d, layers, heads, ff)
    tensors = [model.embed.weight, model.position.weight]
    for block in model.blocks:
        tensors += [block.ln1.weight, block.ln1.bias, block.qkv.weight, block.qkv.bias, block.proj.weight, block.proj.bias,
                    block.ln2.weight, block.ln2.bias, block.up.weight, block.up.bias, block.down.weight, block.down.bias]
    tensors += [model.ln.weight, model.ln.bias]
    with torch.no_grad():
        for tensor in tensors:
            (count,) = struct.unpack('>i', stream.read(4))
            if count != tensor.numel():
                raise ValueError('Tensor size mismatch')
            tensor.copy_(torch.tensor(struct.unpack('>%df' % count, stream.read(4 * count))).view_as(tensor))
    if stream.read(1):
        raise ValueError('Trailing assistant model data')
    return model.eval(), vocab, threshold
