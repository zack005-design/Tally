"""Structural checks a step program must pass before Tally runs it (mirrored in Kotlin AssistantPlanner).

1. Grammar: each statement is a known step with well-formed arguments, at most four statements, ending in 'answer'.
2. Coverage: every placeholder (CATn, ACCn, NUMn, WORDn) in the question is used by the program, and the program
   uses no placeholder absent from the question.
3. Periods: the set of time periods named in the question equals the set used by the program (budget, forecast,
   saving plans, what-if and help steps take no period).
4. Meaning cues: percentage wording needs a share or what-if step; comparison wording needs a compare step; a help
   topic must be the single best match for the question's topic words.
"""
PERIODS = {('today',): 'today', ('yesterday',): 'yesterday', ('this', 'week'): 'this_week', ('last', 'week'): 'last_week',
           ('previous', 'week'): 'last_week', ('this', 'month'): 'this_month', ('last', 'month'): 'last_month',
           ('previous', 'month'): 'last_month', ('this', 'year'): 'this_year', ('last', 'year'): 'last_year',
           ('previous', 'year'): 'last_year'}
MONTHS = {'january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'}
SIMPLE = {'today', 'yesterday', 'this_week', 'last_week', 'this_month', 'last_month', 'this_year', 'last_year'}
FILTERED = {'spend', 'income', 'count', 'average', 'top', 'largest'}
TOPICS = {'add', 'split', 'backup', 'budget', 'export', 'lock', 'receipt', 'privacy'}
TOPIC_CUES = {'add': {'add', 'record', 'log', 'enter', 'expense', 'purchase'}, 'split': {'split', 'share', 'shared', 'friends'},
              'backup': {'backup', 'back', 'restore', 'move', 'phone', 'backed'}, 'budget': {'budget', 'budgets', 'limit'},
              'export': {'export', 'csv', 'download', 'report', 'transactions'}, 'lock': {'lock', 'pin', 'fingerprint', 'protect'},
              'receipt': {'receipt', 'bill', 'scan', 'import', 'read'},
              'privacy': {'private', 'privacy', 'upload', 'internet', 'see', 'who', 'send'}}
# A topic's own name counts double, so "split a bill" is about splitting and "add a receipt" about receipts.
STRONG_CUES = {'split': {'split'}, 'receipt': {'receipt', 'scan'}, 'budget': {'budget', 'budgets'}, 'lock': {'lock'},
               'backup': {'backup', 'restore'}, 'export': {'export', 'csv'}, 'privacy': {'privacy', 'private'}}
PERCENT_CUES = {'percent', 'percentage', '%', 'share', 'part'}
COMPARE_CUES = {'compare', 'compared', 'vs', 'versus', 'than', 'or', 'higher', 'ya'}


def placeholder(token, kinds=('CAT', 'ACC', 'NUM', 'WORD')):
    return any(token.startswith(k) and token[len(k):].isdigit() for k in kinds)


def question_periods(source):
    found = set()
    for i, token in enumerate(source):
        if token in MONTHS and not (token == 'may' and i + 1 < len(source) and source[i + 1] in ('i', 'we')):
            found.add(('month', token))
        if token == 'days' and i >= 1 and source[i - 1].startswith('NUM') and i >= 2 and source[i - 2] == 'last' or \
                token == 'days' and i >= 2 and source[i - 2] == 'past':
            found.add(('last_days', source[i - 1]))
        for words, name in PERIODS.items():
            if tuple(source[i:i + len(words)]) == words and not (name == 'last_month' and i + 2 < len(source) and source[i + 2] == 'days'):
                found.add((name,))
    # "last N days" also contains "last"; "this month" inside "can i still spend this month" belongs to budget questions.
    return found


def parse(program):
    if len(program) < 2 or program[-1] != 'answer' or program[-2] != ';':
        return None
    statements, current = [], []
    for token in program[:-1]:
        if token == ';':
            if not current:
                return None
            statements.append(current); current = []
        else:
            current.append(token)
    if current or not 1 <= len(statements) <= 4:
        return None
    return statements


def statement_ok(s, spends_before):
    head, args = s[0], s[1:]
    if head in FILTERED:
        if not args:
            return False, None
        i = 0
        if args[0] in SIMPLE:
            period = (args[0],); i = 1
        elif args[0] == 'month' and len(args) > 1 and args[1] in MONTHS:
            period = ('month', args[1]); i = 2
        elif args[0] == 'last_days' and len(args) > 1 and placeholder(args[1], ('NUM',)):
            period = ('last_days', args[1]); i = 2
        else:
            return False, None
        while i < len(args):
            key = args[i]; i += 1
            if key in ('cat', 'acc', 'n') and i < len(args) and placeholder(args[i], ({'cat': 'CAT', 'acc': 'ACC', 'n': 'NUM'}[key],)):
                i += 1
            elif key == 'text' and i < len(args) and placeholder(args[i], ('WORD',)):
                while i < len(args) and placeholder(args[i], ('WORD',)):
                    i += 1
            else:
                return False, None
        if 'n' in args and head != 'top':
            return False, None
        return True, period
    if head in ('compare', 'share'):
        return args == ['$1', '$2'] and spends_before == 2, None
    if head == 'budget':
        return args == [] or (len(args) == 2 and args[0] == 'cat' and placeholder(args[1], ('CAT',))), None
    if head == 'forecast':
        return args == [], None
    if head == 'saveplan':
        return len(args) == 3 and placeholder(args[0], ('NUM',)) and args[1] == 'months' and placeholder(args[2], ('NUM',)), None
    if head == 'savetime':
        return len(args) == 3 and placeholder(args[0], ('NUM',)) and args[1] == 'per' and placeholder(args[2], ('NUM',)), None
    if head == 'whatif':
        return len(args) == 4 and args[0] == 'cat' and placeholder(args[1], ('CAT',)) and args[2] == 'cut' and \
            (args[3] == 'all' or placeholder(args[3], ('NUM',))), None
    if head == 'help':
        return len(args) == 1 and args[0] in TOPICS, None
    if head == 'unknown':
        return args == [], None
    return False, None


def program_periods(program):
    """Periods used by a program's filtered steps (used for a follow-up's previous turn)."""
    found, i = set(), 0
    while i < len(program):
        t = program[i]
        if t in SIMPLE:
            found.add((t,))
        elif t in ('month', 'last_days') and i + 1 < len(program):
            found.add((t, program[i + 1])); i += 1
        i += 1
    return found


def valid(source, program, context=()):
    """`context` is the previous turn's program for follow-ups (empty for a first question).

    With context, the plan may also use the previous turn's placeholders and periods; everything named in the
    new question must still be used, and nothing outside the question and the previous turn may appear.
    """
    context = list(context)
    statements = parse(program)
    if statements is None:
        return False
    periods, spends = set(), 0
    for i, s in enumerate(statements):
        if s[0] in ('compare', 'share') and i != len(statements) - 1:
            return False
        ok, period = statement_ok(s, spends)
        if not ok:
            return False
        if s[0] == 'spend' or s[0] == 'income':
            spends += 1
        if period:
            periods.add(period)
    heads = [s[0] for s in statements]
    if len(statements) > 1 and not (heads[-1] in ('compare', 'share') and all(h in ('spend', 'income') for h in heads[:-1]) and len(heads) == 3):
        return False
    words = set(source)
    if heads[0] == 'help':
        scores = {topic: len(words & cues) + len(words & STRONG_CUES.get(topic, set())) for topic, cues in TOPIC_CUES.items()}
        best = max(scores.values())
        if best == 0 or scores[statements[0][1]] != best or list(scores.values()).count(best) > 1:
            return False
    elif heads[0] != 'unknown':
        if words & PERCENT_CUES and not ({'share', 'whatif'} & set(heads)):
            return False
        if words & COMPARE_CUES and 'compare' not in heads and 'whatif' not in heads and 'savetime' not in heads:
            return False
        if 'compare' in heads and not words & (COMPARE_CUES | {'more', 'more'}):
            return False
    used = {t for t in program if placeholder(t)}
    mentioned = {t for t in source if placeholder(t)}
    carried = {t for t in context if placeholder(t)}
    if heads[0] in ('help', 'unknown'):
        return not used
    if not mentioned <= used or not used <= mentioned | carried:
        return False
    asked = question_periods(source)
    if periods and not (asked <= periods <= asked | program_periods(context)):
        return False
    if periods and not asked and not context:
        return False
    return True
