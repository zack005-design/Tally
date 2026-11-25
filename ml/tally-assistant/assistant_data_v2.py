"""v2 training data: more phrasings, every step family evaluable, and follow-up turns.

Every v1 template is kept. Extra templates are appended per intent so every intent has development and
test templates (split by position, as in v1). Follow-up turns carry the previous question's placeholders:
the model sees `<bos> previous-program <ctx> question <sep>` and writes the new program.
"""
import random
from assistant_data import (T, SPEND, INCOME, COUNT, AVERAGE, TOP, LARGEST, COMPARE_PERIODS, COMPARE_CATEGORIES, SHARE,
                            BUDGET, FORECAST, SAVE, WHATIF, HELP_TOPICS, UNKNOWN, CATEGORIES, ACCOUNTS, WORDS, MONTHS,
                            PERIODS, split_of, tokenize, number_value, period_choice)
import assistant_data as v1

p_ = lambda s: s['p']
# "savings" alone is also an ordinary word ("savings if food goes down"), so the v2 account is "savings account".
ACCOUNTS = tuple(a for a in ACCOUNTS if a != 'savings') + ('savings account',)
EXTRA = {
    'spend': [
        T('how much did i blow {p}', lambda s: ['spend', *p_(s), ';', 'answer']),
        T('sum of my expenses {p}', lambda s: ['spend', *p_(s), ';', 'answer']),
        T('overall spend {p}', lambda s: ['spend', *p_(s), ';', 'answer']),
        T('{p} kitna paisa gaya', lambda s: ['spend', *p_(s), ';', 'answer']),
        T('how much have i spent in total on {c} {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('{c} total {p} please', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('what did {c} cost me {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('how much on {d} {p}', lambda s: ['spend', *p_(s), 'text', *s['d'], ';', 'answer']),
        T('what did {d} cost me {p}', lambda s: ['spend', *p_(s), 'text', *s['d'], ';', 'answer']),
        T('{a} spending total {p}', lambda s: ['spend', *p_(s), 'acc', s['a'], ';', 'answer']),
        T('what did i pay through {a} {p}', lambda s: ['spend', *p_(s), 'acc', s['a'], ';', 'answer']),
    ],
    'income': [
        T('how much money did i get {p}', lambda s: ['income', *p_(s), ';', 'answer']),
        T('income total {p}', lambda s: ['income', *p_(s), ';', 'answer']),
        T('what came in {p}', lambda s: ['income', *p_(s), ';', 'answer']),
        T('{p} kitni kamai hui', lambda s: ['income', *p_(s), ';', 'answer']),
        T('show my income {p}', lambda s: ['income', *p_(s), ';', 'answer']),
    ],
    'count': [
        T('how many {d} did i buy {p}', lambda s: ['count', *p_(s), 'text', *s['d'], ';', 'answer']),
        T('number of times i paid for {d} {p}', lambda s: ['count', *p_(s), 'text', *s['d'], ';', 'answer']),
        T('how many transactions {p}', lambda s: ['count', *p_(s), ';', 'answer']),
        T('how many times did i spend on {c} {p}', lambda s: ['count', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('{d} kitni baar {p}', lambda s: ['count', *p_(s), 'text', *s['d'], ';', 'answer']),
    ],
    'average': [
        T('average spending per day {p}', lambda s: ['average', *p_(s), ';', 'answer']),
        T('what is my daily spend {p}', lambda s: ['average', *p_(s), ';', 'answer']),
        T('on average how much do i spend a day {p}', lambda s: ['average', *p_(s), ';', 'answer']),
        T('daily average for {c} {p}', lambda s: ['average', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('per day {c} spending {p}', lambda s: ['average', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('how much per day on {c} {p}', lambda s: ['average', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('roz ka average kharch {p}', lambda s: ['average', *p_(s), ';', 'answer']),
        T('my average daily expenses {p}', lambda s: ['average', *p_(s), ';', 'answer']),
    ],
    'top': [
        T('top spending categories {p}', lambda s: ['top', *p_(s), ';', 'answer']),
        T('what did i spend the most on {p}', lambda s: ['top', *p_(s), ';', 'answer']),
        T('rank my categories {p}', lambda s: ['top', *p_(s), ';', 'answer']),
        T('show my {k} top categories {p}', lambda s: ['top', *p_(s), 'n', s['k'], ';', 'answer']),
        T('my category breakdown {p}', lambda s: ['top', *p_(s), ';', 'answer']),
        T('kis category mein sabse zyada gaya {p}', lambda s: ['top', *p_(s), ';', 'answer']),
    ],
    'largest': [
        T('my most expensive purchase {p}', lambda s: ['largest', *p_(s), ';', 'answer']),
        T('what was the largest payment {p}', lambda s: ['largest', *p_(s), ';', 'answer']),
        T('highest expense {p}', lambda s: ['largest', *p_(s), ';', 'answer']),
        T('biggest single expense {p}', lambda s: ['largest', *p_(s), ';', 'answer']),
        T('what was my largest {c} expense {p}', lambda s: ['largest', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('highest {c} payment {p}', lambda s: ['largest', *p_(s), 'cat', s['c'], ';', 'answer']),
        T('sabse bada kharch {p}', lambda s: ['largest', *p_(s), ';', 'answer']),
    ],
    'compare_periods': [
        T('{p} or {q} which was more expensive', lambda s: ['spend', *p_(s), ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('spending {p} compared with {q}', lambda s: ['spend', *p_(s), ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('compare {c} spending {p} with {q}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *s['q'], 'cat', s['c'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('did {c} cost more {p} than {q}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *s['q'], 'cat', s['c'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('compare my income {p} with {q}', lambda s: ['income', *p_(s), ';', 'income', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('did i earn more {p} than {q}', lambda s: ['income', *p_(s), ';', 'income', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('{p} vs {q} for {d}', lambda s: ['spend', *p_(s), 'text', *s['d'], ';', 'spend', *s['q'], 'text', *s['d'], ';', 'compare', '$1', '$2', ';', 'answer']),
    ],
    'compare_categories': [
        T('which cost more {p} {c} or {e}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *p_(s), 'cat', s['e'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('compare spending on {c} versus {e} {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *p_(s), 'cat', s['e'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('is {c} higher than {e} {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *p_(s), 'cat', s['e'], ';', 'compare', '$1', '$2', ';', 'answer']),
        T('{c} ya {e} kis pe zyada kharch hua {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *p_(s), 'cat', s['e'], ';', 'compare', '$1', '$2', ';', 'answer']),
    ],
    'share': [
        T('{c} share of total spending {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *p_(s), ';', 'share', '$1', '$2', ';', 'answer']),
        T('what percentage did {c} take {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *p_(s), ';', 'share', '$1', '$2', ';', 'answer']),
        T('how much of my spending was {c} in percent {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *p_(s), ';', 'share', '$1', '$2', ';', 'answer']),
        T('percent spent on {c} {p}', lambda s: ['spend', *p_(s), 'cat', s['c'], ';', 'spend', *p_(s), ';', 'share', '$1', '$2', ';', 'answer']),
    ],
    'budget': [
        T('budget status', lambda s: ['budget', ';', 'answer']),
        T('how close am i to my budget', lambda s: ['budget', ';', 'answer']),
        T('did i cross my budget', lambda s: ['budget', ';', 'answer']),
        T('{c} budget left', lambda s: ['budget', 'cat', s['c'], ';', 'answer']),
        T('how much more can i spend on {c}', lambda s: ['budget', 'cat', s['c'], ';', 'answer']),
    ],
    'forecast': [
        T('what will i have spent by month end', lambda s: ['forecast', ';', 'answer']),
        T('forecast my spending', lambda s: ['forecast', ';', 'answer']),
        T('projected spending this month', lambda s: ['forecast', ';', 'answer']),
        T('will i overspend this month', lambda s: ['forecast', ';', 'answer']),
    ],
    'save': [
        T('monthly saving needed for {n} in {m} months', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
        T('how to save {n} within {m} months', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
        T('if i set aside {n} monthly when do i reach {o}', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
        T('months needed to save {o} with {n} per month', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
        T('{n} har mahine bachaun to {o} kab tak', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
        T('how long until i have {o} saving {n} each month', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
        T('per month amount to reach {n} in {m} months', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
    ],
    'whatif': [
        T('what would i save by cutting {c} {r} %', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
        T('savings if {c} goes down {r} %', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
        T('if i drop {c} completely how much do i save', lambda s: ['whatif', 'cat', s['c'], 'cut', 'all', ';', 'answer']),
        T('reduce {c} by {r} % what is the saving', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
        T('{c} {r} % kam karun to kitna bachega', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
        T('how much would i save if i quit {c}', lambda s: ['whatif', 'cat', s['c'], 'cut', 'all', ';', 'answer']),
    ],
}
# v1's "how much cash did i burn" collides with an account named Cash; v2 uses "money" instead.
INTENTS = {name: [(text.replace('how much cash did i burn', 'how much money did i burn'), build) for text, build in templates]
           + EXTRA.get(name, []) for name, templates in v1.INTENTS.items()}
HELP_EXTRA = {'add': ['where do i add spending'], 'split': ['can i split rent with my flatmates'],
              'backup': ['how do i save a backup file'], 'budget': ['can i limit my monthly spending'],
              'export': ['how do i make a csv report'], 'lock': ['can i hide tally behind a fingerprint'],
              'receipt': ['can i scan a paper receipt'], 'privacy': ['does tally send my data anywhere']}
HELP = {k: v + HELP_EXTRA[k] for k, v in HELP_TOPICS.items()}
UNKNOWN_V2 = UNKNOWN + ['recommend a good movie', 'who is the prime minister', 'write my homework', 'what is love',
                        'order pizza for me', 'is it going to rain', 'tell me something interesting', 'sing a song']

# Follow-ups: (text, kind). Kinds: period, category, word, compare_period.
FOLLOWUPS = {
    'period': ['and {q}', 'what about {q}', 'same for {q}', 'and for {q}', 'how about {q}', 'aur {q}', '{q} too', 'now {q}'],
    'category': ['and {e}', 'what about {e}', 'same for {e}', 'how about {e}', 'and for {e} instead', 'aur {e}', 'now {e}', 'try {e}'],
    'compare_period': ['compare that with {q}', 'compare it to {q}', 'how does that compare with {q}', 'vs {q}', 'is that more than {q}',
                       'versus {q}', 'compare with {q}', 'and compared to {q}'],
}


def language_vocabulary():
    words = set(v1.language_vocabulary())
    def add(text):
        for t in tokenize(text.replace('{p}', ' ').replace('{q}', ' ').replace('{c}', ' ').replace('{e}', ' ').replace('{d}', ' ')
                          .replace('{a}', ' ').replace('{n}', ' ').replace('{o}', ' ').replace('{m}', ' ').replace('{k}', ' ').replace('{r}', ' ')):
            if t[0].isalpha():
                words.add(t)
    for templates in EXTRA.values():
        for text, _ in templates:
            add(text)
    for phrases in list(HELP.values()) + [UNKNOWN_V2]:
        for p in phrases:
            add(p)
    add('quick question bata')
    for phrases in FOLLOWUPS.values():
        for p in phrases:
            add(p)
    return words


def anonymise(text, categories, accounts, vocabulary, carried=None):
    """v1 anonymiser, but placeholders from the previous turn are reused and new ones continue the numbering."""
    carried = carried or {}
    tokens = tokenize(text)
    out, slots = [], dict(carried)
    names = sorted([(c, 'CAT') for c in categories] + [(a, 'ACC') for a in accounts], key=lambda x: -len(tokenize(x[0])))
    seen = {(label.rstrip('0123456789'), value): label for label, value in carried.items()}
    counters = {k: max([int(l[len(k):]) for l in carried if l.startswith(k) and l[len(k):].isdigit()] or [0]) for k in ('CAT', 'ACC', 'NUM', 'WORD')}

    def label(kind, value):
        if (kind, value) not in seen:
            counters[kind] += 1
            seen[(kind, value)] = f'{kind}{counters[kind]}'
            slots[seen[(kind, value)]] = value
        return seen[(kind, value)]
    i = 0
    while i < len(tokens):
        hit = None
        for name, kind in names:
            parts = tokenize(name)
            window = tokens[i:i + len(parts)]
            if parts and len(window) == len(parts) and (window == parts or (window[:-1] == parts[:-1] and window[-1] == parts[-1] + 's')):
                hit = (name, kind, len(parts)); break
        if hit:
            out.append(label(hit[1], hit[0])); i += hit[2]; continue
        t = tokens[i]
        if number_value(t) is not None:
            out.append(label('NUM', t))
        elif t in vocabulary or not t[0].isalpha():
            out.append(t)
        else:
            out.append(label('WORD', t))
        i += 1
    if any(v > 4 for v in counters.values()) or len(out) > 40:
        return None
    return out, slots


def _fill(rng, text, build, vocabulary, carried=None, categories=None, accounts=None):
    categories = categories or rng.sample(CATEGORIES, 3)
    accounts = accounts or rng.sample(ACCOUNTS, 2)
    p, p_text = period_choice(rng)
    q, q_text = period_choice(rng)
    while q == p:
        q, q_text = period_choice(rng)
    word = rng.choice([w for w in WORDS if not any(t in vocabulary for t in tokenize(w))])
    n = rng.choice(('5000', '10000', '25k', '1 lakh', '50,000', '2000', '75000', '1.5 lakh', '300', '12000', '8k', '3 lakh'))
    o = rng.choice(('20000', '1 lakh', '60k', '2,00,000', '45000', '9000', '5 lakh'))
    while o == n:
        o = rng.choice(('20000', '1 lakh', '60k', '2,00,000', '45000', '9000', '5 lakh'))
    m = rng.choice(('3', '6', '10', '12', '18', '24', '4', '9'))
    k = rng.choice(('2', '3', '4', '5'))
    r = rng.choice(('10', '20', '25', '30', '50', '15', '40'))
    filled = (text.replace('{p}', p_text).replace('{q}', q_text).replace('{c}', categories[0]).replace('{e}', categories[1])
              .replace('{d}', word).replace('{a}', accounts[0]).replace('{n}', n).replace('{o}', o).replace('{m}', m)
              .replace('{k}', k).replace('{r}', r))
    if rng.random() < .25:
        filled = rng.choice(('please ', 'can you tell me ', 'hey tally ', 'tell me ', 'quick question ', 'bata ')) + filled
    if rng.random() < .3:
        filled += '?'
    result = anonymise(filled, categories, accounts, vocabulary, carried)
    if result is None:
        return None
    tokens, slots = result
    reverse = {}
    for key, value in slots.items():
        reverse.setdefault(value, key)

    def ref(value):
        return reverse.get(value[1]) if isinstance(value, tuple) else value
    slot_values = {'p': [ref(x) for x in p], 'q': [ref(x) for x in q], 'c': reverse.get(categories[0]), 'e': reverse.get(categories[1]),
                   'a': reverse.get(accounts[0]), 'd': [reverse[t] for t in tokenize(word) if t in reverse],
                   'n': reverse.get(n.lower()), 'o': reverse.get(o.lower()), 'm': reverse.get(m), 'k': reverse.get(k), 'r': reverse.get(r)}
    try:
        program = build(slot_values)
    except KeyError:
        return None
    if any(x is None for x in program) or any(isinstance(x, list) for x in program) or \
            ('{d}' in text and len(slot_values['d']) != len(tokenize(word))):
        return None
    return {'text': filled, 'source': tokens, 'program': program, 'slots': slots, 'categories': categories, 'accounts': accounts}


PERIOD_HEADS = {'today', 'yesterday', 'this_week', 'last_week', 'this_month', 'last_month', 'this_year', 'last_year'}


def _replace_period(program, new):
    """Replace every period in the program's filtered steps with `new` (a list of period tokens)."""
    out, i = [], 0
    while i < len(program):
        t = program[i]
        if t in PERIOD_HEADS:
            out += new; i += 1
        elif t == 'month':
            out += new; i += 2
        elif t == 'last_days':
            out += new; i += 2
        else:
            out.append(t); i += 1
    return out


def _followup(rng, base, vocabulary, split):
    """Builds a follow-up turn on top of a rendered single-statement or comparison base row."""
    program = base['program']
    heads = [t for t in program if t in ('spend', 'income', 'count', 'average', 'top', 'largest')]
    # Follow-ups extend single-total questions only; changing "the period" of a comparison is ambiguous.
    if len(heads) != 1 or len(program) - program.count(';') > 8 and 'compare' in program:
        return None
    kind = rng.choice(('period', 'period', 'category', 'compare_period'))
    if kind == 'category' and 'cat' not in program:
        kind = 'period'
    if kind == 'compare_period' and (len(heads) != 1 or program[0] not in ('spend', 'income')):
        kind = 'period'
    phrases = [p for i, p in enumerate(FOLLOWUPS[kind]) if split_of(i, len(FOLLOWUPS[kind])) == split]
    text = rng.choice(phrases)
    q, q_text = period_choice(rng)
    categories = base['categories']; accounts = base['accounts']
    new_category = rng.choice([c for c in CATEGORIES if c not in categories])
    filled = text.replace('{q}', q_text).replace('{e}', new_category)
    if rng.random() < .25:
        filled += '?'
    result = anonymise(filled, categories + [new_category], accounts, vocabulary, base['slots'])
    if result is None:
        return None
    tokens, slots = result
    reverse = {}
    for key, value in slots.items():
        reverse.setdefault(value, key)
    q_tokens = [reverse.get(x[1]) if isinstance(x, tuple) else x for x in q]
    if None in q_tokens:
        return None
    if kind == 'period':
        if not any(t in PERIOD_HEADS or t in ('month', 'last_days') for t in program):
            return None
        new_program = _replace_period(program, q_tokens)
        if new_program == program:
            return None
    elif kind == 'category':
        old = program[program.index('cat') + 1]
        new = reverse[new_category]
        new_program = [new if t == old else t for t in program]
    else:
        first = program[:-2]  # drop '; answer'
        second = _replace_period(first, q_tokens)
        if second == first:
            return None
        new_program = first + [';'] + second + [';', 'compare', '$1', '$2', ';', 'answer']
    return {'text': filled, 'context': program, 'source': tokens, 'program': new_program, 'slots': slots,
            'previous_text': base['text'], 'intent': 'followup_' + kind}


def make_corpus(train_count=300000, dev_count=3000, test_count=3000, seed=9173, context_rate=.25, followup_rate=.18):
    rng = random.Random(seed)
    vocabulary = language_vocabulary()
    pools = {'train': [], 'dev': [], 'test': []}
    for name, templates in INTENTS.items():
        for index, (text, build) in enumerate(templates):
            pools[split_of(index, len(templates))].append((name, text, build))
    help_pools = {'train': [], 'dev': [], 'test': []}
    for topic, phrases in HELP.items():
        for index, phrase in enumerate(phrases):
            help_pools['test' if index == 3 else 'dev' if index == 2 else 'train'].append((topic, phrase))
    unknown_pools = {'train': UNKNOWN_V2[:20], 'dev': UNKNOWN_V2[20:24], 'test': UNKNOWN_V2[24:]}

    def standalone(split, carried=None):
        roll = rng.random()
        if roll < .06:
            topic, phrase = rng.choice(help_pools[split])
            text = rng.choice(('', 'please ', 'hey tally ')) + phrase + rng.choice(('', '?'))
            tokens, slots = anonymise(text, [], [], vocabulary, carried)
            return {'text': text, 'source': tokens, 'program': ['help', topic, ';', 'answer'], 'intent': 'help', 'slots': slots,
                    'categories': [], 'accounts': []}
        if roll < .1:
            text = rng.choice(unknown_pools[split]) + rng.choice(('', '?'))
            tokens, slots = anonymise(text, [], [], vocabulary, carried)
            return {'text': text, 'source': tokens, 'program': ['unknown', ';', 'answer'], 'intent': 'unknown', 'slots': slots,
                    'categories': [], 'accounts': []}
        name, text, build = rng.choice(pools[split])
        row = _fill(rng, text, build, vocabulary, carried)
        if row:
            row['intent'] = name
        return row

    def make(split, count):
        rows, seen = [], set()
        while len(rows) < count:
            if rng.random() < followup_rate:
                base = standalone(split)
                if not base or base['intent'] in ('help', 'unknown'):
                    continue
                row = _followup(rng, base, vocabulary, split)
            elif rng.random() < context_rate:
                # A new question after an unrelated one: placeholders continue the previous turn's numbering,
                # exactly as in the app, and the model must ignore the previous program.
                other = standalone(split)
                if not other or other['intent'] in ('help', 'unknown'):
                    continue
                row = standalone(split, other['slots'])
                if row:
                    row['context'] = other['program']
            else:
                row = standalone(split)
            if not row:
                continue
            key = ' '.join(row.get('context', [])) + '|' + ' '.join(row['source']) + '|' + ' '.join(row['program'])
            if split == 'train' and key in seen and rng.random() < .7:
                continue
            seen.add(key)
            row = {k: v for k, v in row.items() if k not in ('categories', 'accounts')}
            row['split'] = split
            rows.append(row)
        return rows

    return make('train', train_count), make('dev', dev_count), make('test', test_count), sorted(vocabulary)
