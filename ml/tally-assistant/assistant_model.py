"""Tiny decoder-only transformer for Tally's reasoning steps, trained from random weights."""
import math
import struct
import torch
from torch import nn
from torch.nn import functional as F

SPECIALS = ['<pad>', '<bos>', '<sep>', '<eos>', '<unk>', '<ctx>']
PLACEHOLDERS = [f'{kind}{i}' for kind in ('CAT', 'ACC', 'NUM', 'WORD') for i in range(1, 5)] + ['$1', '$2']
PROGRAM = ['spend', 'income', 'count', 'average', 'top', 'largest', 'compare', 'share', 'budget', 'forecast', 'saveplan',
           'savetime', 'whatif', 'help', 'unknown', 'answer', ';', 'cat', 'acc', 'text', 'n', 'months', 'per', 'cut', 'all',
           'today', 'yesterday', 'this_week', 'last_week', 'this_month', 'last_month', 'this_year', 'last_year', 'month',
           'last_days', 'add', 'split', 'backup', 'export', 'lock', 'receipt', 'privacy', '?', '%']
MAX_LEN = 128
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
    def __init__(self, vocab_size, d=256, layers=6, heads=8, ff=1024, max_len=MAX_LEN, dropout=0.1):
        super().__init__()
        self.embed = nn.Embedding(vocab_size, d); self.position = nn.Embedding(max_len, d)
        self.blocks = nn.ModuleList(Block(d, heads, ff, dropout) for _ in range(layers))
        self.ln = nn.LayerNorm(d)
        self.max_len = max_len
        self.register_buffer('mask', torch.tril(torch.ones(max_len, max_len)), persistent=False)
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


def prompt(index, source, context=None):
    """v1 prompts are `<bos> question <sep>`; v2 adds the previous program: `<bos> previous <ctx> question <sep>`."""
    tokens = [index['<bos>']]
    if context is not None:
        tokens += [index.get(t, index['<unk>']) for t in context] + [index['<ctx>']]
    return tokens + [index.get(t, index['<unk>']) for t in source] + [index['<sep>']]


def encode(row, index, with_context=False):
    head = prompt(index, row['source'], row.get('context', []) if with_context else None)
    tokens = head + [index[t] for t in row['program']] + [index['<eos>']]
    return tokens[:MAX_LEN], len(head)


@torch.inference_mode()
def generate(model, vocab, index, source_tokens, limit=40, context=None):
    """Greedy decoding. Returns (program tokens, minimum chosen-token probability)."""
    tokens = prompt(index, source_tokens, context)
    device = next(model.parameters()).device
    out, confidence = [], 1.0
    while len(out) < limit and len(tokens) < model.max_len:
        probabilities = model(torch.tensor([tokens], device=device))[0, -1].float().softmax(-1)
        p, choice = probabilities.max(-1)
        confidence = min(confidence, float(p))
        if int(choice) == index['<eos>']:
            break
        out.append(vocab[int(choice)]); tokens.append(int(choice))
    return out, confidence


def matrices(model):
    """Tensors in file order; True marks weight matrices that version 3 stores as int8 rows."""
    out = [(model.embed.weight, True), (model.position.weight, False)]
    for block in model.blocks:
        out += [(block.ln1.weight, False), (block.ln1.bias, False), (block.qkv.weight, True), (block.qkv.bias, False),
                (block.proj.weight, True), (block.proj.bias, False), (block.ln2.weight, False), (block.ln2.bias, False),
                (block.up.weight, True), (block.up.bias, False), (block.down.weight, True), (block.down.bias, False)]
    return out + [(model.ln.weight, False), (model.ln.bias, False)]


@torch.no_grad()
def quantise_(model):
    """Rounds every weight matrix to symmetric per-row int8 in place, exactly as version 3 stores it."""
    for tensor, int8 in matrices(model):
        if int8:
            scale = tensor.abs().amax(dim=1, keepdim=True).clamp(min=1e-12) / 127
            tensor.copy_((tensor / scale).round().clamp(-127, 127) * scale)
    return model


def export(model, vocab, threshold, stream, language_words=None, head_thresholds=None, int8=False):
    """v2 adds question words and per-step thresholds; v3 also stores weight matrices as int8 rows with scales."""
    version = 1 if language_words is None else 3 if int8 else 2
    stream.write(struct.pack('>iiiiiiif', MAGIC, version, len(vocab), model.d, model.layers, model.heads, model.ff, threshold))
    stream.write(struct.pack('>i', model.max_len))
    for token in vocab:
        data = token.encode('utf-8'); stream.write(struct.pack('>i', len(data)) + data)
    if language_words is not None:
        indices = [i for i, token in enumerate(vocab) if token in language_words]
        stream.write(struct.pack('>i', len(indices)) + struct.pack('>%di' % len(indices), *indices))
        heads = sorted((head_thresholds or {}).items())
        stream.write(struct.pack('>i', len(heads)))
        for head, value in heads:
            stream.write(struct.pack('>if', vocab.index(head), value))
    for tensor, quantised in matrices(model):
        t = tensor.detach().cpu().float()
        if int8 and quantised:
            scale = t.abs().amax(dim=1).clamp(min=1e-12) / 127
            q = (t / scale[:, None]).round().clamp(-127, 127).to(torch.int8)
            stream.write(struct.pack('>iii', -1, t.shape[0], t.shape[1]))
            stream.write(struct.pack('>%df' % t.shape[0], *scale.tolist()))
            values = q.flatten().tolist()
            stream.write(struct.pack('>%db' % len(values), *values))
        else:
            values = t.flatten().tolist()
            stream.write(struct.pack('>i', len(values)))
            stream.write(struct.pack('>%df' % len(values), *values))


def load(stream):
    magic, version, size, d, layers, heads, ff, threshold = struct.unpack('>iiiiiiif', stream.read(32))
    if magic != MAGIC or version not in (1, 2, 3):
        raise ValueError('Unsupported assistant model')
    (max_len,) = struct.unpack('>i', stream.read(4))
    vocab = []
    for _ in range(size):
        (length,) = struct.unpack('>i', stream.read(4)); vocab.append(stream.read(length).decode('utf-8'))
    if version >= 2:
        (count,) = struct.unpack('>i', stream.read(4)); stream.read(4 * count)
        (count,) = struct.unpack('>i', stream.read(4)); stream.read(8 * count)
    model = TinyLM(size, d, layers, heads, ff, max_len)
    with torch.no_grad():
        for tensor, _ in matrices(model):
            (count,) = struct.unpack('>i', stream.read(4))
            if count == -1:
                rows, cols = struct.unpack('>ii', stream.read(8))
                if (rows, cols) != tuple(tensor.shape):
                    raise ValueError('Tensor size mismatch')
                scale = torch.tensor(struct.unpack('>%df' % rows, stream.read(4 * rows)))
                q = torch.frombuffer(bytearray(stream.read(rows * cols)), dtype=torch.int8).float().view(rows, cols)
                tensor.copy_(q * scale[:, None])
                continue
            if count != tensor.numel():
                raise ValueError('Tensor size mismatch')
            tensor.copy_(torch.tensor(struct.unpack('>%df' % count, stream.read(4 * count))).view_as(tensor))
    if stream.read(1):
        raise ValueError('Trailing assistant model data')
    return model.eval(), vocab, threshold
