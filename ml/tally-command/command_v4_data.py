"""Broader synthetic structure training; all previous ladders are regression evidence."""
import random
import command_data as data

DEV_EN = (
    'expense entry: for {description} paid {amount} using {account}',
    'for {description} today i used {account} and paid {amount}',
    'note {account}: paid {amount} for {description}',
    'today i paid {amount} using {account} for my {description}',
    'i paid {amount} towards {description} from {account}',
)
DEV_HI = (
    'aaj {account} se {amount} diye {description} ke liye',
    'maine {description} ka payment kiya {amount} {account} se',
    'note {description} ke liye {amount} {account} se diye',
)
TEST_EN = (
    'entry today: paid {amount} for {description} using {account}',
    'for my {description}, i used {account} and paid {amount}',
    'note for {description}: paid {amount} from {account}',
    'today {description} cost {amount} and i paid via {account}',
)
TEST_HI = (
    'note aaj {description} pe {amount} {account} se kharch kiye',
    'maine aaj {amount} {account} se {description} ke liye diye',
    '{description} ke liye aaj maine {account} se {amount} diye',
)


def signature(template):
    row = data.render(template, {'description': 'descriptionmarker', 'amount': '45', 'account': 'accountmarker', 'currency': ''}, False)
    return tuple(token.normalized for token in data.tokenize(row['text']))


TRAIN_EN = tuple(dict.fromkeys(prefix + frame for prefix in ('', 'please ', 'today ', 'note ', 'expense entry: ')
                              for frame in data.TRAIN + data.DEV + data.TEST))
TRAIN_HI = tuple(dict.fromkeys(prefix + frame for prefix in ('', 'aaj ', 'note ')
                              for frame in data.HINGLISH_TRAIN + data.HINGLISH_DEV + data.HINGLISH_TEST))
reserved = {signature(t) for t in DEV_EN + DEV_HI + TEST_EN + TEST_HI}
TRAIN_EN = tuple(t for t in TRAIN_EN if signature(t) not in reserved)
TRAIN_HI = tuple(t for t in TRAIN_HI if signature(t) not in reserved)


def make_corpus_v4(train_count=120000, dev_count=2000, test_count=3000, seed=29173):
    rng = random.Random(seed)
    seen = set()

    def split(count, english, hinglish, name):
        rows = []
        while len(rows) < count:
            paise = rng.randint(1, rng.choice((999, 99999, 9999999)))
            if rng.random() < .5:
                paise = max(100, paise // 100 * 100)
            amount = str(paise // 100) if paise % 100 == 0 else f'{paise // 100}.{paise % 100:02d}'
            if rng.random() < .15 and paise >= 100000:
                whole, _, decimal = amount.partition('.')
                amount = f'{int(whole):,}' + ('.' + decimal if decimal else '')
            description = rng.choice(data.DESCRIPTIONS)
            if rng.random() < .15:
                description = rng.choice(('cedar', 'lotus', 'maple', 'silver', 'orange')) + ' ' + description
            values = {'description': description, 'amount': amount, 'account': rng.choice(tuple(data.ACCOUNTS)),
                      'currency': rng.choice(('', '', '₹', 'INR ', 'Rs. '))}
            positive = rng.random() >= .2
            hindi = rng.random() < .35
            if positive:
                template = rng.choice(hinglish if hindi else english)
            else:
                negatives = data.NEGATIVES + data.NEGDEV + data.NEGTEST + data.HINGLISH_NEGATIVES
                template = {'train': '', 'dev': 'note today: ', 'test': 'expense entry today: '}[name] + rng.choice(negatives)
            row = data.render(template, values, positive)
            if positive and row['draft'] is None:
                continue
            if rng.random() < .2:
                row['text'] = row['text'].upper()
                if row['draft']:
                    row['draft']['description'] = row['draft']['description'].upper()
            if row['text'].lower() in seen:
                continue
            seen.add(row['text'].lower())
            row.update(split=name, language='hinglish' if hindi else 'english')
            rows.append(row)
        return rows

    return split(train_count, TRAIN_EN, TRAIN_HI, 'train'), split(dev_count, DEV_EN, DEV_HI, 'dev'), split(test_count, TEST_EN, TEST_HI, 'test')
