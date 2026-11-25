"""v5 synthetic corpus: broader phrasing, descriptions and explicit UPI-app accounts.

All v4 development/test structures are now inspected material and join training.
The v5 development/test structures below are new and disjoint after normalization.
Importing this module applies the v5 runtime policy (account aliases and description
boundaries); Kotlin mirrors the same policy in ScratchCommandModel.
"""
import random
import command_data as data
import command_v4_data as v4

# Explicit allowlist only. UPI maps to the app's UPI slot (GPay); PhonePe and Paytm
# are returned as their own names and resolve only to an account with that exact name.
V5_ACCOUNTS = {'upi': 'GPAY', 'phonepe': 'PHONEPE', 'phone pe': 'PHONEPE', 'paytm': 'PAYTM'}
data.ACCOUNTS.update(V5_ACCOUNTS)
ACCOUNT_LEADS = frozenset(name.split(' ')[0] for name in data.ACCOUNTS)
data.DESCRIPTION_END = frozenset(data.DESCRIPTION_END | {'worth', 'mein', 'gaye', 'liya', 'khareeda', 'bhara'} | ACCOUNT_LEADS)
# Hinglish particles never start a description (found after held-out errors such as "73156 mein dentist").
data.DESCRIPTION_FILLERS = frozenset(data.DESCRIPTION_FILLERS | {'mein', 'ka', 'ke', 'ki', 'se', 'pe', 'par'})
GUARD_REVISION = 'description-boundary-v2'

NEW_DESCRIPTIONS = ('swiggy order', 'zomato order', 'uber ride', 'ola ride', 'metro card', 'petrol',
                    'diesel', 'gas cylinder', 'water bill', 'dth recharge', 'tuition', 'gift', 'flowers',
                    'tiffin', 'sabzi', 'doodh', 'kirana', 'rickshaw', 'movie tickets', 'donation',
                    'biryani', 'thali', 'pani puri', 'vada pav', 'momos', 'juice', 'cold drink',
                    'newspaper', 'courier', 'bike service', 'car wash', 'doctor visit', 'eye drops',
                    'notebook', 'umbrella', 'slippers', 'cricket bat', 'phone cover', 'headphones', 'bus pass')
DESCRIPTIONS = tuple(dict.fromkeys(data.DESCRIPTIONS + NEW_DESCRIPTIONS))

NEW_TRAIN_EN = (
    'spent {currency}{amount} {account} on {description}',
    '{currency}{amount} via {account} for {description}',
    'paid {currency}{amount} to {description} via {account}',
    '{description} {account} {currency}{amount}',
    '{account}: {description} {currency}{amount}',
    'got {description} for {currency}{amount}, paid with {account}',
    'ordered {description} for {currency}{amount} on {account}',
    'bought {description} worth {currency}{amount} using {account}',
    'paid for {description} with {account}, {currency}{amount}',
    'just paid {currency}{amount} for {description} through {account}',
    '{currency}{amount} spent on {description} using {account}',
    'expense: {description}, {currency}{amount}, {account}',
    'used {account} for {description}: {currency}{amount}',
    '{description} was {currency}{amount} on {account}',
)
NEW_TRAIN_HI = (
    '{description} ke {amount} {account} se',
    '{account} se {amount} diye {description} ke liye',
    '{description} mein {amount} gaye {account} se',
    '{amount} ka {description} liya {account} se',
    '{description} khareeda {amount} mein {account} se',
    '{account} se {description} ka bill {amount} bhara',
    '{description} ke liye {account} se {amount} bhara',
)
DEV_EN = (
    'ordered {description} worth {currency}{amount} via {account}',
    '{currency}{amount} via {account} towards {description}',
    'just spent {currency}{amount} on {description} with {account}',
    'expense: {account}, {description}, {currency}{amount}',
)
DEV_HI = (
    '{account} se {description} liya {amount} ka',
    '{description} ke liye {amount} gaye {account} se',
)
TEST_EN = (
    'just bought {description} for {currency}{amount} on {account}',
    '{account} used for {description}, {currency}{amount}',
    'spent {currency}{amount} through {account} for {description}',
    'got {description} worth {currency}{amount} from {account}',
    'paid {currency}{amount} to {description} using {account}',
)
TEST_HI = (
    '{amount} mein {description} khareeda {account} se',
    '{description} pe {account} se {amount} gaye',
    '{account} se {description} ke {amount} bhara',
)

reserved = {v4.signature(t) for t in DEV_EN + DEV_HI + TEST_EN + TEST_HI}
TRAIN_EN = tuple(t for t in dict.fromkeys(v4.TRAIN_EN + v4.DEV_EN + v4.TEST_EN + NEW_TRAIN_EN
                                          + tuple('today ' + t for t in NEW_TRAIN_EN)) if v4.signature(t) not in reserved)
TRAIN_HI = tuple(t for t in dict.fromkeys(v4.TRAIN_HI + v4.DEV_HI + v4.TEST_HI + NEW_TRAIN_HI
                                          + tuple('aaj ' + t for t in NEW_TRAIN_HI)) if v4.signature(t) not in reserved)
NEGATIVES = data.NEGATIVES + data.NEGDEV + data.NEGTEST + data.HINGLISH_NEGATIVES + (
    'refund of {amount} for {description} came to {account}',
    '{description} {amount} {account} nahi diye',
    'i did not pay {amount} for {description} with {account}',
    'received {amount} on {account} for {description}',
)


def make_corpus_v5(train_count=140000, dev_count=2000, test_count=3000, seed=41017):
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
            description = rng.choice(DESCRIPTIONS)
            if rng.random() < .15:
                description = rng.choice(('cedar', 'lotus', 'maple', 'silver', 'orange')) + ' ' + description
            values = {'description': description, 'amount': amount, 'account': rng.choice(tuple(data.ACCOUNTS)),
                      'currency': rng.choice(('', '', '₹', 'INR ', 'Rs. '))}
            positive = rng.random() >= .2
            hindi = rng.random() < .35
            if positive:
                template = rng.choice(hinglish if hindi else english)
            else:
                template = {'train': '', 'dev': 'note today: ', 'test': 'expense entry today: '}[name] + rng.choice(NEGATIVES)
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
