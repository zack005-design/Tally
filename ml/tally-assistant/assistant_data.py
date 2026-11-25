"""Synthetic training data for Tally's tiny reasoning model (assistant-authored, no private records).

A question is anonymised first (categories -> CATn, accounts -> ACCn, amounts/counts -> NUMn, other
unknown words -> WORDn). The model maps the anonymised question to a short step program; Tally's exact
code runs the steps, so the model never produces a number itself.
"""
import random
import re

CATEGORIES = ('food', 'groceries', 'transport', 'shopping', 'education', 'entertainment', 'bills', 'subscriptions',
              'health', 'travel', 'other', 'pets', 'gym', 'kids', 'office', 'fuel', 'medical', 'rent', 'eating out',
              'home', 'gifts', 'personal care', 'utilities', 'snacks')
ACCOUNTS = ('cash', 'wallet', 'gpay', 'bank', 'hdfc', 'sbi', 'icici', 'paytm', 'phonepe', 'credit card', 'savings')
WORDS = ('chai', 'coffee', 'swiggy', 'zomato', 'uber', 'ola', 'petrol', 'netflix', 'amazon', 'momos', 'pizza',
         'biryani', 'auto', 'metro', 'movies', 'books', 'medicine', 'milk', 'dosa', 'samosa', 'juice',
         'electricity', 'recharge', 'haircut', 'flipkart', 'dmart', 'blinkit', 'rapido', 'spotify', 'cigarettes', 'beer')
MONTHS = ('january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october',
          'november', 'december')

TOKEN = re.compile(r"[0-9][0-9,]*(?:\.[0-9]+)?(?:\s*(?:k|lakh|lakhs|lac|thousand))?(?![a-z0-9])|[a-z]+(?:'[a-z]+)?|%|\?|[^\s]")
DROP = {'₹', 'rs', 'inr', 'rupees', 'rupee', '.', ',', '!', ':', ';', '"', "'", '(', ')', '-'}

# ---------------------------------------------------------------- periods
PERIODS = [  # (program tokens, phrasings)
    (['today'], ['today', 'so far today']),
    (['yesterday'], ['yesterday']),
    (['this_week'], ['this week', 'so far this week']),
    (['last_week'], ['last week', 'the previous week']),
    (['this_month'], ['this month', 'so far this month', 'in this month']),
    (['last_month'], ['last month', 'the previous month', 'in the last month']),
    (['this_year'], ['this year', 'so far this year']),
    (['last_year'], ['last year', 'the previous year']),
]


def period_choice(rng):
    roll = rng.random()
    if roll < .12:
        month = rng.choice(MONTHS)
        return ['month', month], rng.choice([f'in {month}', f'during {month}', f'for {month}', month])
    if roll < .2:
        days = str(rng.choice((3, 5, 7, 10, 14, 15, 30, 45, 60, 90)))
        return ['last_days', ('NUM', days)], rng.choice([f'in the last {days} days', f'over the past {days} days', f'last {days} days'])
    program, phrases = rng.choice(PERIODS)
    return list(program), rng.choice(phrases)


# ---------------------------------------------------------------- templates per intent
# Each template: (text with {slots}, program builder). Lists are split train/dev/test by position.
def T(text, build):
    return text, build


SPEND = [
    T('how much did i spend {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('how much have i spent {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('what did i spend {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('total spending {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('my expenses {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('how much money went out {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('tell me my total spend {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('what was my spending {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('{p} how much did i spend', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('kitna kharch hua {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('how much did i spend on {c} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('what did i spend on {c} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('{c} spending {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('how much went on {c} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('total for {c} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('{p} how much was spent on {c}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('show my {c} expenses {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('{c} pe kitna kharch hua {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('how much did i spend on {d} {p}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('how much have i spent on {d} {p}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('money spent on {d} {p}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('what is my {d} total {p}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('{d} pe kitna gaya {p}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('how much did i pay with {a} {p}', lambda s: ['spend', *s['p'], 'acc', s['a'], ';', 'answer']),
    T('spending from {a} {p}', lambda s: ['spend', *s['p'], 'acc', s['a'], ';', 'answer']),
    T('how much went out of {a} {p}', lambda s: ['spend', *s['p'], 'acc', s['a'], ';', 'answer']),
    T('how much cash did i burn {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('give me my spending total {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('what amount did i spend {p}', lambda s: ['spend', *s['p'], ';', 'answer']),
    T('{p} total for {c}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('how much was my {c} bill {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('{d} expenses {p}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('total {d} spend {p}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('how much did {a} pay {p}', lambda s: ['spend', *s['p'], 'acc', s['a'], ';', 'answer']),
]
INCOME = [
    T('how much did i earn {p}', lambda s: ['income', *s['p'], ';', 'answer']),
    T('what was my income {p}', lambda s: ['income', *s['p'], ';', 'answer']),
    T('how much money came in {p}', lambda s: ['income', *s['p'], ';', 'answer']),
    T('total income {p}', lambda s: ['income', *s['p'], ';', 'answer']),
    T('how much did i receive {p}', lambda s: ['income', *s['p'], ';', 'answer']),
    T('my earnings {p}', lambda s: ['income', *s['p'], ';', 'answer']),
    T('income received {p}', lambda s: ['income', *s['p'], ';', 'answer']),
    T('how much salary came in {p}', lambda s: ['income', *s['p'], ';', 'answer']),
    T('what did i earn {p}', lambda s: ['income', *s['p'], ';', 'answer']),
]
COUNT = [
    T('how many times did i buy {d} {p}', lambda s: ['count', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('how often did i spend on {d} {p}', lambda s: ['count', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('number of {d} purchases {p}', lambda s: ['count', *s['p'], 'text', *s['d'], ';', 'answer']),
    T('how many {c} expenses {p}', lambda s: ['count', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('how many entries {p}', lambda s: ['count', *s['p'], ';', 'answer']),
    T('count my {c} entries {p}', lambda s: ['count', *s['p'], 'cat', s['c'], ';', 'answer']),
]
AVERAGE = [
    T('what is my average daily spending {p}', lambda s: ['average', *s['p'], ';', 'answer']),
    T('how much do i spend per day {p}', lambda s: ['average', *s['p'], ';', 'answer']),
    T('daily average {p}', lambda s: ['average', *s['p'], ';', 'answer']),
    T('average per day on {c} {p}', lambda s: ['average', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('how much do i spend on {c} each day {p}', lambda s: ['average', *s['p'], 'cat', s['c'], ';', 'answer']),
]
TOP = [
    T('where did my money go {p}', lambda s: ['top', *s['p'], ';', 'answer']),
    T('what are my top categories {p}', lambda s: ['top', *s['p'], ';', 'answer']),
    T('which category did i spend the most on {p}', lambda s: ['top', *s['p'], ';', 'answer']),
    T('biggest spending areas {p}', lambda s: ['top', *s['p'], ';', 'answer']),
    T('top {k} categories {p}', lambda s: ['top', *s['p'], 'n', s['k'], ';', 'answer']),
    T('show my {k} biggest categories {p}', lambda s: ['top', *s['p'], 'n', s['k'], ';', 'answer']),
    T('sabse zyada kharch kahan hua {p}', lambda s: ['top', *s['p'], ';', 'answer']),
    T('which categories cost me the most {p}', lambda s: ['top', *s['p'], ';', 'answer']),
    T('break down my spending by category {p}', lambda s: ['top', *s['p'], ';', 'answer']),
    T('list the top {k} areas i spent on {p}', lambda s: ['top', *s['p'], 'n', s['k'], ';', 'answer']),
]
LARGEST = [
    T('what was my biggest expense {p}', lambda s: ['largest', *s['p'], ';', 'answer']),
    T('largest purchase {p}', lambda s: ['largest', *s['p'], ';', 'answer']),
    T('what was the most expensive thing i bought {p}', lambda s: ['largest', *s['p'], ';', 'answer']),
    T('biggest {c} expense {p}', lambda s: ['largest', *s['p'], 'cat', s['c'], ';', 'answer']),
    T('my largest {c} payment {p}', lambda s: ['largest', *s['p'], 'cat', s['c'], ';', 'answer']),
]
COMPARE_PERIODS = [
    T('did i spend more {p} than {q}', lambda s: ['spend', *s['p'], ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('compare my spending {p} with {q}', lambda s: ['spend', *s['p'], ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('am i spending more {p} compared to {q}', lambda s: ['spend', *s['p'], ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('how does {p} compare to {q}', lambda s: ['spend', *s['p'], ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('is my spending {p} higher than {q}', lambda s: ['spend', *s['p'], ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('did i spend more on {c} {p} than {q}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['q'], 'cat', s['c'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('compare {c} {p} and {q}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['q'], 'cat', s['c'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('has my {c} spending gone up {p} compared with {q}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['q'], 'cat', s['c'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('was {d} more {p} than {q}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'spend', *s['q'], 'text', *s['d'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('compare {d} spending {p} vs {q}', lambda s: ['spend', *s['p'], 'text', *s['d'], ';', 'spend', *s['q'], 'text', *s['d'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('{p} versus {q} spending', lambda s: ['spend', *s['p'], ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('was i more careful {p} than {q}', lambda s: ['spend', *s['p'], ';', 'spend', *s['q'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('{c} {p} vs {q}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['q'], 'cat', s['c'], ';', 'compare', '$1', '$2', ';', 'answer']),
]
COMPARE_CATEGORIES = [
    T('did i spend more on {c} or {e} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['p'], 'cat', s['e'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('compare {c} and {e} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['p'], 'cat', s['e'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('which was higher {p} {c} or {e}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['p'], 'cat', s['e'], ';', 'compare', '$1', '$2', ';', 'answer']),
    T('{c} vs {e} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['p'], 'cat', s['e'], ';', 'compare', '$1', '$2', ';', 'answer']),
]
SHARE = [
    T('what percent of my spending was {c} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['p'], ';', 'share', '$1', '$2', ';', 'answer']),
    T('what share of my money went to {c} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['p'], ';', 'share', '$1', '$2', ';', 'answer']),
    T('how big a part of my spending is {c} {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['p'], ';', 'share', '$1', '$2', ';', 'answer']),
    T('{c} as a percentage of total {p}', lambda s: ['spend', *s['p'], 'cat', s['c'], ';', 'spend', *s['p'], ';', 'share', '$1', '$2', ';', 'answer']),
]
BUDGET = [
    T('how much budget do i have left', lambda s: ['budget', ';', 'answer']),
    T('am i within my budget', lambda s: ['budget', ';', 'answer']),
    T('how is my budget doing', lambda s: ['budget', ';', 'answer']),
    T('how much can i still spend this month', lambda s: ['budget', ';', 'answer']),
    T('how much is left in my {c} budget', lambda s: ['budget', 'cat', s['c'], ';', 'answer']),
    T('am i over budget on {c}', lambda s: ['budget', 'cat', s['c'], ';', 'answer']),
    T('can i still spend on {c} this month', lambda s: ['budget', 'cat', s['c'], ';', 'answer']),
    T('budget kitna bacha hai', lambda s: ['budget', ';', 'answer']),
    T('what is left of my budget', lambda s: ['budget', ';', 'answer']),
    T('remaining {c} budget', lambda s: ['budget', 'cat', s['c'], ';', 'answer']),
]
FORECAST = [
    T('how much will i spend this month', lambda s: ['forecast', ';', 'answer']),
    T('what will my spending be by the end of the month', lambda s: ['forecast', ';', 'answer']),
    T('predict my spending for this month', lambda s: ['forecast', ';', 'answer']),
    T('at this rate how much will i spend', lambda s: ['forecast', ';', 'answer']),
    T('am i on track this month', lambda s: ['forecast', ';', 'answer']),
    T('month end tak kitna kharch hoga', lambda s: ['forecast', ';', 'answer']),
    T('estimate my total for this month', lambda s: ['forecast', ';', 'answer']),
    T('where will i end up this month', lambda s: ['forecast', ';', 'answer']),
]
SAVE = [
    T('how much should i save each month to have {n} in {m} months', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
    T('i want to save {n} in {m} months how much per month', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
    T('plan to save {n} over {m} months', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
    T('to reach {n} in {m} months what do i need to put aside monthly', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
    T('{m} months mein {n} bachane ke liye har mahine kitna', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
    T('if i save {n} every month how long to reach {o}', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
    T('how many months to save {o} if i put aside {n} a month', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
    T('saving {n} per month when will i have {o}', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
    T('how long will it take to save {o} at {n} a month', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
    T('i need {n} in {m} months how much to save monthly', lambda s: ['saveplan', s['n'], 'months', s['m'], ';', 'answer']),
    T('at {n} a month how many months for {o}', lambda s: ['savetime', s['o'], 'per', s['n'], ';', 'answer']),
]
WHATIF = [
    T('what if i cut {c} by {r} %', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
    T('how much would i save if i spend {r} % less on {c}', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
    T('if i reduce {c} by {r} % how much do i save', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
    T('cutting {c} {r} % would save me how much', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
    T('what happens if i stop spending on {c}', lambda s: ['whatif', 'cat', s['c'], 'cut', 'all', ';', 'answer']),
    T('how much would i save without {c}', lambda s: ['whatif', 'cat', s['c'], 'cut', 'all', ';', 'answer']),
    T('if i spend {r} % less on {c} what do i save', lambda s: ['whatif', 'cat', s['c'], 'cut', s['r'], ';', 'answer']),
]
HELP_TOPICS = {
    'add': ['how do i add an expense', 'how to record a purchase', 'how can i log spending', 'how do i enter an expense'],
    'split': ['how do i split a bill', 'how to share an expense with friends', 'how can i split dinner with friends', 'how do shared bills work'],
    'backup': ['how do i back up my data', 'how to restore a backup', 'how can i move tally to a new phone', 'is my data backed up'],
    'budget': ['how do i set a budget', 'how to create a monthly budget', 'how can i add a budget for a category', 'where do i set budgets'],
    'export': ['how do i export my transactions', 'how to get a pdf report', 'can i export to csv', 'how do i download my expenses'],
    'lock': ['how do i lock the app', 'how to add a fingerprint lock', 'can i protect tally with a pin', 'how do i turn on app lock'],
    'receipt': ['how do i scan a bill', 'how to add a receipt', 'can tally read my receipt', 'how do i import a pdf bill'],
    'privacy': ['is my data private', 'does tally upload my data', 'does tally use the internet', 'who can see my expenses'],
}
UNKNOWN = [
    'what is the weather today', 'tell me a joke', 'who won the match yesterday', 'write a poem about money',
    'what is the capital of france', 'should i buy bitcoin', 'which stock should i invest in', 'what is my bank password',
    'send money to my friend', 'delete all my data', 'what is the meaning of life', 'book a cab for me',
    'how do i hack an account', 'translate this to hindi', 'what time is it', 'call my mom',
    'play some music', 'open whatsapp', 'what should i eat for dinner', 'how old are you',
]

INTENTS = {'spend': SPEND, 'income': INCOME, 'count': COUNT, 'average': AVERAGE, 'top': TOP, 'largest': LARGEST,
           'compare_periods': COMPARE_PERIODS, 'compare_categories': COMPARE_CATEGORIES, 'share': SHARE,
           'budget': BUDGET, 'forecast': FORECAST, 'save': SAVE, 'whatif': WHATIF}


def split_of(index, size):
    """Deterministic template split: every 7th template is held out for test, the next one for dev."""
    if size < 4:
        return 'train'
    return 'test' if index % 7 == 3 else 'dev' if index % 7 == 5 else 'train'


# ---------------------------------------------------------------- anonymiser (mirrored in Kotlin)
def tokenize(text):
    return [t for t in TOKEN.findall(text.lower()) if t not in DROP]


def number_value(token):
    m = re.fullmatch(r"([0-9][0-9,]*(?:\.[0-9]+)?)\s*(k|lakh|lakhs|lac|thousand)?", token)
    if not m:
        return None
    value = float(m.group(1).replace(',', ''))
    scale = {'k': 1000, 'thousand': 1000, 'lakh': 100000, 'lakhs': 100000, 'lac': 100000}.get(m.group(2), 1)
    return value * scale


def anonymise(text, categories, accounts, vocabulary):
    """Returns (tokens, slots) or None when too many unknowns. Longest category/account names match first."""
    tokens = tokenize(text)
    slots = {}
    out = []
    names = sorted([(c, 'CAT') for c in categories] + [(a, 'ACC') for a in accounts], key=lambda x: -len(tokenize(x[0])))
    seen = {}
    counters = {'CAT': 0, 'ACC': 0, 'NUM': 0, 'WORD': 0}
    i = 0
    while i < len(tokens):
        matched = False
        for name, kind in names:
            parts = tokenize(name)
            window = tokens[i:i + len(parts)]
            if len(window) == len(parts) and (window == parts or window[:-1] == parts[:-1] and window[-1] == parts[-1] + 's'):
                key = (kind, name)
                if key not in seen:
                    counters[kind] += 1
                    seen[key] = f'{kind}{counters[kind]}'
                    slots[seen[key]] = name
                out.append(seen[key]); i += len(parts); matched = True
                break
        if matched:
            continue
        token = tokens[i]
        if number_value(token) is not None:
            key = ('NUM', token)
            if key not in seen:
                counters['NUM'] += 1
                seen[key] = f"NUM{counters['NUM']}"
                slots[seen[key]] = token
            out.append(seen[key])
        elif token in vocabulary or not token[0].isalpha():
            out.append(token)
        else:
            key = ('WORD', token)
            if key not in seen:
                counters['WORD'] += 1
                seen[key] = f"WORD{counters['WORD']}"
                slots[seen[key]] = token
            out.append(seen[key])
        i += 1
    if any(v > 4 for v in counters.values()) or len(out) > 40:
        return None
    return out, slots


# ---------------------------------------------------------------- generation
def language_vocabulary():
    words = set()
    def add(text):
        for t in tokenize(re.sub(r'\{[a-z]\}', ' ', text)):
            if t[0].isalpha():
                words.add(t)
    for templates in INTENTS.values():
        for text, _ in templates:
            add(text)
    for _, phrases in PERIODS:
        for p in phrases:
            add(p)
    for p in ('in the last days', 'over the past days', 'last days', 'in during for') + MONTHS:
        add(p)
    for phrases in HELP_TOPICS.values():
        for p in phrases:
            add(p)
    for p in UNKNOWN:
        add(p)
    add('please can you tell me hey tally')
    return words


def render(rng, text, build, vocabulary):
    categories = rng.sample(CATEGORIES, 3)
    accounts = rng.sample(ACCOUNTS, 2)
    p, p_text = period_choice(rng)
    q, q_text = period_choice(rng)
    while q == p:
        q, q_text = period_choice(rng)
    word = rng.choice([w for w in WORDS if not any(t in vocabulary for t in tokenize(w))])
    n = rng.choice(('5000', '10000', '25k', '1 lakh', '50,000', '2000', '75000', '1.5 lakh', '300', '12000'))
    o = rng.choice(('20000', '1 lakh', '60k', '2,00,000', '45000', '9000'))
    while o == n:
        o = rng.choice(('20000', '1 lakh', '60k', '2,00,000', '45000', '9000'))
    m = rng.choice(('3', '6', '10', '12', '18', '24', '4'))
    k = rng.choice(('2', '3', '4', '5'))
    r = rng.choice(('10', '20', '25', '30', '50', '15'))
    filled = (text.replace('{p}', p_text).replace('{q}', q_text).replace('{c}', categories[0]).replace('{e}', categories[1])
              .replace('{d}', word).replace('{a}', accounts[0]).replace('{n}', n).replace('{o}', o).replace('{m}', m)
              .replace('{k}', k).replace('{r}', r))
    if rng.random() < .25:
        filled = rng.choice(('please ', 'can you tell me ', 'hey tally ', 'tell me ')) + filled
    if rng.random() < .3:
        filled += '?'
    if rng.random() < .15:
        filled = filled.capitalize()
    result = anonymise(filled, categories, accounts, vocabulary)
    if result is None:
        return None
    tokens, slots = result
    reverse = {v: k for k, v in slots.items()}

    def ref(value):
        if isinstance(value, tuple):  # ('NUM', literal) from a period phrase
            return reverse.get(value[1])
        return value
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
    return {'text': filled, 'source': tokens, 'program': program}


def make_corpus(train_count=60000, dev_count=1500, test_count=1500, seed=7331):
    rng = random.Random(seed)
    vocabulary = language_vocabulary()
    pools = {'train': [], 'dev': [], 'test': []}
    for name, templates in INTENTS.items():
        for index, (text, build) in enumerate(templates):
            pools[split_of(index, len(templates))].append((name, text, build))
    help_pools = {'train': [], 'dev': [], 'test': []}
    for topic, phrases in HELP_TOPICS.items():
        for index, phrase in enumerate(phrases):
            help_pools['test' if index == 3 else 'dev' if index == 2 else 'train'].append((topic, phrase))
    unknown_pools = {'train': UNKNOWN[:14], 'dev': UNKNOWN[14:17], 'test': UNKNOWN[17:]}

    def make(split, count):
        rows, seen = [], set()
        while len(rows) < count:
            roll = rng.random()
            if roll < .06:
                topic, phrase = rng.choice(help_pools[split])
                text = rng.choice(('', 'please ', 'hey tally ')) + phrase + rng.choice(('', '?'))
                tokens, _ = anonymise(text, [], [], vocabulary)
                row = {'text': text, 'source': tokens, 'program': ['help', topic, ';', 'answer'], 'intent': 'help'}
            elif roll < .1:
                text = rng.choice(unknown_pools[split]) + rng.choice(('', '?'))
                tokens, _ = anonymise(text, [], [], vocabulary)
                row = {'text': text, 'source': tokens, 'program': ['unknown', ';', 'answer'], 'intent': 'unknown'}
            else:
                name, text, build = rng.choice(pools[split])
                row = render(rng, text, build, vocabulary)
                if row is None:
                    continue
                row['intent'] = name
            key = ' '.join(row['source']) + '|' + ' '.join(row['program'])
            if split == 'train' and key in seen and rng.random() < .7:
                continue
            seen.add(key)
            row['split'] = split
            rows.append(row)
        return rows

    return make('train', train_count), make('dev', dev_count), make('test', test_count), sorted(vocabulary)
