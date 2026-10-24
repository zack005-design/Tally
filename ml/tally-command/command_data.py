"""Synthetic span-label data authored here; no pretrained data or weights."""
from dataclasses import dataclass
from decimal import Decimal
import random
import re
import string


TOKEN = re.compile(r"[A-Za-z]+(?:['’-][A-Za-z]+)*|[0-9]+(?:,[0-9]+)*(?:\.[0-9]+)?|[^\s]")
NUMBER = re.compile(r'[0-9]+(?:,[0-9]+)*(?:\.[0-9]+)?\Z')
AMOUNT = re.compile(r'(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+|[1-9][0-9]?(?:,[0-9]{2})*,[0-9]{3})(?:\.[0-9]{1,2})?\Z')
UNSUPPORTED = re.compile(r'\b(?:transfer|transferred|refund|refunded|income|salary|earned|received|repay|repaid|repayment|returned|split|shared|owe|owed|owes|between|dollars|usd|eur|gbp|balance|budget|delete|remove|cancel|not|never|didn\W?t|don\W?t|haven\W?t|tomorrow|plan|planning)\b|[$€£]|\?', re.I)
ACCOUNTS = {'cash': 'WALLET', 'wallet': 'WALLET', 'gpay': 'GPAY', 'google pay': 'GPAY', 'g pay': 'GPAY'}
ACCOUNTS.update({'g-pay': 'GPAY', 'googlepay': 'GPAY'})
BASE_ACCOUNTS = tuple(ACCOUNTS)
# A small explicit allowlist, never fuzzy correction of arbitrary account names.
ACCOUNTS.update({'gpy': 'GPAY', 'gpaay': 'GPAY', 'gooogle pay': 'GPAY', 'wallett': 'WALLET', 'walllet': 'WALLET'})
DESCRIPTIONS = ('lunch', 'coffee', 'tea', 'dinner', 'groceries', 'breakfast', 'bus ticket',
                'office lunch', 'ice cream', 'phone recharge', 'movie', 'milk', 'fuel',
                'books', 'snacks', 'rent', 'medicine', 'vegetables', 'train', 'taxi',
                'pizza', 'internet', 'haircut', 'parking', 'gym', 'laundry', 'water bottle',
                'printer paper', 'electricity', 'school fees', 'pet food', 'chocolate',
                'restaurant meal', 'sandwich', 'auto ride', 'repair', 'fruit', 'bakery',
                'breakfast dosa', 'monthly subscription', 'bread and eggs', 'train pass')
TRAIN = (
    '{description} {amount} {account}', 'spent {amount} on {description} through {account}',
    'paid {amount} for {description} using {account}', 'add {description} {amount} from {account}',
    '{description} cost {amount} paid by {account}', 'record {amount} for {description} via {account}',
    'i spent {amount} on {description} with {account}', '{account} payment {amount} for {description}',
    'log {description} for {amount} using {account}', 'bought {description} for {amount} with {account}',
    '{description}, {amount}, {account}', 'expense {description} {amount} {account}',
    'my {description} was {amount} and i used {account}', 'i used {account} for {description} costing {amount}',
    'please add my {description} {amount} rupees {account}', 'paid through {account}: {description} {amount}',
    'today i bought {description} using {account} for {amount}', '{amount} went on {description} from {account}',
    'for {description}, {amount} was paid via {account}', 'i paid for {description}: {amount} with {account}',
    'add an expense of {amount} for {description} paid using {account}', '{description} paid with {account} amount {amount}',
    'yesterday {description} {amount} via {account}', 'record a {account} expense for {description} of {amount}',
    '{description} for {amount} rupees, paid through {account}', 'spent on {description}: {amount}, account {account}',
    'payment for {description} using {account} was {amount}', '{amount} {description} {account}',
    'paid {account} {amount} for {description}', 'log {amount} spent via {account} on {description}',
    'for my {description} the cost came to {amount} from {account}', 'i had {description} for {amount}, used {account}',
    'note {amount} {description} via {account}', 'paid {amount} at {description} through {account}',
)
DEV = ('note {description} at {amount} with {account}', 'today {account} paid for {description} {amount}',
       'i bought {description}; paid {amount} from {account}', '{description} came to {amount} using {account}')
TEST = ('please record {description} costing {amount} paid through {account}',
        'for {description} i used {account} to pay {amount}', 'my {description} expense was {amount} via {account}',
        'put down {amount} for {description} with {account}', 'the {description} cost me {amount} out of {account}',
        '{account} was used to pay {amount} towards {description}', 'i got {description} and paid {amount} via {account}',
        '{description} today: {amount} rupees through {account}')
NEGATIVES = ('transfer {amount} from cash to gpay', 'refund {amount} for {description} to {account}',
             'salary {amount} via {account}', 'split {description} {amount} with rahul using {account}',
             'i did not spend {amount} on {description} with {account}', 'what is my balance in {account}',
             'delete {description} {amount} {account}', 'budget {amount} for {description}',
             'received {amount} from rahul in {account}', 'i will buy {description} tomorrow for {amount} with {account}',
             'rahul repaid {amount} into {account}', 'planning {description} for {amount} from {account}')
NEGDEV = ('my salary was {amount} into {account}', 'refund for {description}: {amount} {account}',
          'do not add {description} {amount} {account}', 'how much is in my {account}?')
NEGTEST = ('move {amount} from wallet to gpay', 'i got a refund of {amount} for {description} in {account}',
           'i never paid {amount} for {description} with {account}', 'please split {amount} for {description} using {account}',
           'rahul owes me {amount} in {account}', 'tomorrow i plan to spend {amount} on {description} via {account}')

# All previously inspected template sets are training material in v3, explicitly.
# New dev/test structures below are disjoint; challenge_v3.json is never trained on.
HISTORICAL_TEMPLATES = TRAIN + DEV + TEST
LEADS = ('paid', 'i paid', 'spent', 'i spent', 'record', 'log', 'please add',
         'note', 'please log', 'i have paid', 'this morning i spent', 'yesterday i paid')
FRAMES = ('{currency}{amount} for {description} using {account}',
          '{currency}{amount} on {description} through {account}',
          '{currency}{amount} towards {description} with {account}',
          '{account} {currency}{amount} for {description}',
          'for {description}, {currency}{amount} via {account}',
          '{description}: {currency}{amount}, paid from {account}')
TRAIN = tuple(dict.fromkeys(HISTORICAL_TEMPLATES + tuple(lead + ' ' + frame for lead in LEADS for frame in FRAMES) + (
    'for {description} i paid {currency}{amount} using {account}',
    'used {account} to pay {currency}{amount} for {description}',
    "today's {description} cost {currency}{amount} via {account}",
    'i bought a {description} for {currency}{amount} with {account}',
    'the {description} cost was {currency}{amount}, paid using {account}',
    'bought {description}; paid {currency}{amount} through {account}',
    'entry {description}: paid {currency}{amount} using {account}',
)))
DEV = ('record for {description}: {currency}{amount} paid via {account}',
       'i paid through {account} for {description} costing {currency}{amount}',
       'please log {description} with {account} for {currency}{amount}',
       '{description} was paid from {account}, {currency}{amount}',
       "today's entry: {description} {currency}{amount} paid with {account}",
       'paid {currency}{amount} from {account} towards {description}')
TEST = ('i have paid {currency}{amount} through {account} for {description}',
        'entry for {description}: {currency}{amount} out of {account}',
        'this morning i paid for {description} using {account}, {currency}{amount}',
        'the {description} was {currency}{amount} and paid from {account}',
        'please add an entry for {description}, {currency}{amount} using {account}',
        '{account} paid {currency}{amount} towards my {description}',
        'note for today: {description} costing {currency}{amount} with {account}',
        'for {description}, i have paid {currency}{amount} via {account}')
DESCRIPTIONS += ('stationery', 'dentist', 'biscuits', 'printer ink', 'badminton',
                 'masala dosa', 'filter coffee', 'laptop', 'noodles', 'shampoo',
                 'subscription', 'train fare', 'mobile data', 'toll', 'charging cable',
                 'school supplies', 'paneer', 'chai', 'samosa', 'idli', 'vegetable curry',
                 'toothpaste', 'soap', 'sunscreen', 'consultation', 'delivery fee',
                 'almonds', 'apples', 'rice', 'flour', 'yogurt', 'battery', 'socks')
UNSUPPORTED = re.compile(UNSUPPORTED.pattern + r'|\bi\s+(?:will|would|might|may|could|should)\b', re.I)
UNSUPPORTED = re.compile(UNSUPPORTED.pattern + r'|\b(?:nahi|nahin|mat|kal|karunga|karungi|karenge|dunga|dungi)\b', re.I)

HINGLISH_TRAIN = (
    'maine {amount} {description} ke liye {account} se diye',
    '{description} pe {amount} {account} se kharch kiye',
    'aaj {description} ka {amount} {account} se payment kiya',
    'maine {account} se {description} par {amount} kharch kiye',
    '{description} ke {amount} rupaye {account} se diye',
    'aaj maine {description} pe {amount} {account} se kharch kiye',
)
HINGLISH_DEV = (
    'aaj {description} ke liye {account} se {amount} diye',
    '{description} ka {account} se payment kiya {amount}',
)
HINGLISH_TEST = (
    '{account} se {description} ke liye {amount} diye',
    '{description} ka payment {amount} {account} se kiya',
    'maine {amount} rupaye {account} se {description} pe kharch kiye',
    '{description} par aaj {account} se {amount} kharch kiye',
)
HINGLISH_NEGATIVES = (
    'maine {description} pe {amount} {account} se nahi diye',
    'kal {description} pe {amount} {account} se kharch karunga',
    'mat add {description} {amount} {account}',
)
DESCRIPTION_FILLERS = frozenset(('entry', "today's", 'today', 'please', 'record', 'log', 'note',
                               'add', 'spent', 'paid', 'payment', 'i', 'my', 'for', 'on', 'at',
                               'via', 'using', 'aaj', 'maine', 'the'))
DESCRIPTION_END = frozenset(('using', 'through', 'with', 'paid', 'amount', 'via', 'was', 'cost',
                            'costing', 'came', 'for', 'from', 'rupees', 'inr', 'rs', 'ke', 'pe',
                            'par', 'ka', 'rupaye', 'se', 'out', 'and'))


def description_boundary_allowed(tokens, descriptions):
    first, last = descriptions[0], descriptions[-1]
    if tokens[first].text.lower() in DESCRIPTION_FILLERS:
        return False
    if last + 1 == len(tokens):
        return True
    following = tokens[last + 1].text.lower()
    if following == 'and':
        return last + 2 < len(tokens) and tokens[last + 2].text.lower() == 'paid'
    return NUMBER.fullmatch(following) is not None or following in DESCRIPTION_END or following in (',', ':', ';', '.', '₹')


@dataclass(frozen=True)
class Token:
    text: str
    start: int
    end: int

    @property
    def normalized(self):
        if NUMBER.fullmatch(self.text):
            return '<number>'
        return self.text.lower() if self.text[0].isascii() and self.text[0].isalpha() else '<punct>'


def tokenize(text):
    return [Token(m.group(), m.start(), m.end()) for m in TOKEN.finditer(text)]


def amount_paise(text):
    if not AMOUNT.fullmatch(text):
        raise ValueError('Invalid decimal or grouping')
    value = Decimal(text.replace(',', '')) * 100
    if not 0 < value <= 100_000_000 or value != value.to_integral_value():
        raise ValueError('Amount outside supported range')
    return int(value)


def unknown_description_allowed(tokens, tags, vocabulary):
    unknown = [i for i, token in enumerate(tokens) if token.normalized not in vocabulary]
    if not unknown:
        return True
    descriptions = [i for i, tag in enumerate(tags) if tag == 1]
    if not descriptions or any(tags[i] != 1 for i in unknown):
        return False
    if descriptions != list(range(descriptions[0], descriptions[-1] + 1)):
        return False
    first, last = descriptions[0], descriptions[-1]
    if first > 0 and tokens[first - 1].text.lower() in ('for', 'on', 'at', 'towards'):
        return True
    # Compact merchant/description + exact amount + explicit account, with no filler.
    return first == 0 and last + 2 < len(tags) and tags[last + 1] == 2 and all(tag == 3 for tag in tags[last + 2:])


def decode_spans(text, tokens, tags):
    if len(tokens) != len(tags) or not tokens or len(tokens) > 48 or len(text) > 240 or UNSUPPORTED.search(text):
        return None
    numbers = [i for i, token in enumerate(tokens) if NUMBER.fullmatch(token.text)]
    amounts = [i for i, tag in enumerate(tags) if tag == 2]
    descriptions = [i for i, tag in enumerate(tags) if tag == 1]
    accounts = [i for i, tag in enumerate(tags) if tag == 3]
    if len(numbers) != 1 or amounts != numbers or not descriptions or not accounts:
        return None
    index = numbers[0]
    if index and tokens[index - 1].text in ('-', '−', '+', '.', '/'):
        return None
    if index + 1 < len(tokens) and tokens[index + 1].text in ('/', '%'):
        return None
    if descriptions != list(range(descriptions[0], descriptions[-1] + 1)) or accounts != list(range(accounts[0], accounts[-1] + 1)):
        return None
    if not description_boundary_allowed(tokens, descriptions):
        return None
    account_text = ' '.join(tokens[i].text.lower() for i in accounts)
    account = ACCOUNTS.get(account_text)
    # Every explicit account mention must agree; two accounts need clarification.
    mentions = {ACCOUNTS[token.text.lower()] for token in tokens if token.text.lower() in ACCOUNTS}
    for left, right in zip(tokens, tokens[1:]):
        pair = left.text.lower() + ' ' + right.text.lower()
        if pair in ACCOUNTS:
            mentions.add(ACCOUNTS[pair])
    if account is None or (mentions and mentions != {account}):
        return None
    description = text[tokens[descriptions[0]].start:tokens[descriptions[-1]].end].strip()
    if not description or len(description) > 100 or not any(c.isalpha() for c in description):
        return None
    try:
        amount = amount_paise(tokens[index].text)
    except ValueError:
        return None
    return {'amount_paise': amount, 'description': description, 'account': account}


def render(template, values, positive=True):
    text, spans = '', []
    for literal, field, _, _ in string.Formatter().parse(template):
        text += literal
        if field:
            start = len(text)
            text += values[field]
            spans.append((start, len(text), {'description': 1, 'amount': 2, 'account': 3, 'currency': 0}[field]))
    tokens = tokenize(text)
    tags = [next((tag for start, end, tag in spans if start <= token.start and token.end <= end), 0)
            if positive else 0 for token in tokens]
    draft = decode_spans(text, tokens, tags) if positive else None
    return {'text': text, 'tags': tags, 'intent': int(positive), 'draft': draft, 'template': template}


def make_corpus(train_count=60000, dev_count=2000, test_count=2000, seed=2027, expanded=False):
    rng = random.Random(seed)
    seen = set()

    def make(count, templates, split):
        rows = []
        while len(rows) < count:
            # Balance magnitude/precision instead of mostly four-digit amounts.
            upper = rng.choice((99, 999, 9999, 99999, 999999, 9999999))
            paise = rng.randint(1, upper)
            if rng.random() < .45:
                paise = max(100, paise // 100 * 100)
            amount = str(paise // 100) if paise % 100 == 0 else f'{paise // 100}.{paise % 100:02d}'
            if rng.random() < .15 and paise >= 100000:
                whole, _, fraction = amount.partition('.')
                amount = f'{int(whole):,}' + ('.' + fraction if fraction else '')
            description = rng.choice(DESCRIPTIONS)
            if rng.random() < .1:
                description = rng.choice(('cedar', 'lotus', 'maple', 'silver', 'orange')) + ' ' + description
            values = {'description': description, 'amount': amount, 'account': rng.choice(tuple(ACCOUNTS) if expanded else BASE_ACCOUNTS),
                      'currency': rng.choice(('', '', '₹', 'INR ', 'Rs. ', 'rs '))}
            positive = rng.random() >= .20
            negatives = {'train': NEGATIVES, 'dev': NEGDEV, 'test': NEGTEST}[split]
            if expanded and positive and rng.random() < .25:
                template = rng.choice({'train': HINGLISH_TRAIN, 'dev': HINGLISH_DEV, 'test': HINGLISH_TEST}[split])
            elif expanded and not positive and rng.random() < .25:
                # Distinct surface forms for negative dev/test rather than training on challenge rows.
                prefix = {'train': '', 'dev': 'aaj ', 'test': 'please '}[split]
                template = prefix + rng.choice(HINGLISH_NEGATIVES)
            else:
                template = rng.choice(templates if positive else negatives)
            row = render(template, values, positive)
            if positive and row['draft'] is None:
                continue
            # Vary whitespace/capitalization without changing training/test templates.
            if rng.random() < .2:
                row['text'] = row['text'].upper()
                if row['draft']:
                    row['draft']['description'] = row['draft']['description'].upper()
            if row['text'].lower() in seen:
                continue
            seen.add(row['text'].lower())
            row['split'] = split
            rows.append(row)
        return rows

    return make(train_count, TRAIN, 'train'), make(dev_count, DEV, 'dev'), make(test_count, TEST, 'test')
