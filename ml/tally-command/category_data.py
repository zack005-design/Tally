"""Assistant-authored description -> category lexicon for the on-device category suggester.

Labels use Tally's default spending categories (except Other, which is never suggested).
Terms are split by term, so development/test terms never occur verbatim in training.
"""
import random
import re

CATEGORIES = ('Food', 'Groceries', 'Transport', 'Shopping', 'Education', 'Entertainment',
              'Bills', 'Subscriptions', 'Health', 'Travel')

LEXICON = {
    'Food': '''lunch dinner breakfast coffee tea chai snacks pizza burger sandwich biryani thali dosa idli vada
        samosa momos noodles pasta ice cream juice cold drink milkshake bakery cake pastry restaurant meal
        cafe canteen tiffin office lunch street food pani puri vada pav paratha rolls kebab shawarma
        swiggy order zomato order food delivery dinner out brunch dessert sweets mithai lassi filter coffee
        masala dosa paneer tikka fried rice chicken curry fish fry tandoori butter chicken coffee beans
        sushi tacos waffles doughnuts cookies takeaway mess food hostel mess dhaba meal chaat bhel puri
        jalebi gulab jamun soft drink energy drink smoothie frankie pav bhaji chole bhature poha upma
        evening snacks team lunch birthday dinner family dinner'''.split('\n'),
    'Groceries': '''groceries grocery vegetables sabzi fruit fruits milk doodh bread eggs rice flour atta dal
        sugar salt oil cooking oil ghee butter yogurt curd paneer cheese kirana supermarket dmart
        big basket blinkit order zepto order onions potatoes tomatoes apples bananas mangoes spices
        masala tea leaves coffee powder biscuits cereal oats almonds cashews dry fruits pulses lentils
        detergent dish soap toilet paper tissues cleaning supplies household items monthly ration
        ration water can drinking water chicken meat fish mutton eggs tray baby food pet food'''.split('\n'),
    'Transport': '''bus ticket bus pass metro metro card train ticket local train taxi cab uber ride ola ride
        rapido auto auto ride rickshaw fuel petrol diesel cng parking toll fastag car wash bike service
        car service tyre puncture repair bike repair car repair oil change train pass bus fare cab fare
        train fare driver salary ev charging vehicle insurance car insurance bike insurance pollution check
        road tax scooter rental bicycle repair'''.split('\n'),
    'Shopping': '''clothes shirt tshirt jeans dress saree kurta shoes slippers sandals bag backpack wallet
        watch sunglasses umbrella jacket sweater socks headphones earphones phone cover charger
        charging cable power bank laptop mobile phone keyboard mouse furniture chair table mattress
        bedsheet pillow curtains kitchenware utensils pressure cooker mixer grinder home decor gift
        gifts flowers cosmetics perfume shampoo soap toothpaste sunscreen skincare makeup jewellery
        amazon order flipkart order myntra order meesho order toys stationery pens notebook printer ink
        light bulb extension board trimmer hair dryer cricket bat football yoga shoes gym wear'''.split('\n'),
    'Education': '''school fees college fees tuition tuition fees coaching course fee online course books textbooks
        exam fee exam form admission fee library fee school supplies school uniform school bag
        lab fee workshop fee certification exam test series study material notes printout
        stationery for school hostel fees semester fees university fees language class music class
        dance class coding course'''.split('\n'),
    'Entertainment': '''movie movie tickets cinema concert play theatre show tickets amusement park water park
        bowling arcade gaming video game game top up board game party club entry pub night
        museum ticket zoo ticket sports match stadium tickets comedy show standup show event pass
        karaoke escape room picnic outing birthday party trekking event'''.split('\n'),
    'Bills': '''electricity electricity bill power bill water bill gas bill gas cylinder lpg cylinder
        piped gas internet internet bill broadband wifi bill phone bill mobile bill postpaid bill
        phone recharge mobile recharge dth recharge data recharge rent house rent maintenance
        society maintenance property tax municipal tax credit card bill emi loan emi insurance premium
        life insurance health insurance premium landline bill cable bill laundry dry cleaning
        maid salary cook salary newspaper bill milk bill courier security deposit'''.split('\n'),
    'Subscriptions': '''netflix netflix subscription spotify spotify premium prime video amazon prime hotstar
        disney hotstar youtube premium apple music icloud storage google one cloud storage
        monthly subscription annual subscription subscription magazine subscription newspaper subscription
        gym membership gym club membership app subscription software subscription vpn
        music subscription ott subscription streaming subscription chatgpt plus membership fee
        domain renewal hosting plan'''.split('\n'),
    'Health': '''medicine medicines pharmacy chemist tablets syrup vitamins doctor doctor visit consultation
        clinic hospital hospital bill dentist dental checkup eye checkup eye drops spectacles glasses
        contact lenses lab test blood test x ray scan mri health checkup physiotherapy therapy
        counselling vaccination vaccine bandages first aid inhaler insulin ayurvedic medicine
        homeopathy masks sanitizer protein powder supplements'''.split('\n'),
    'Travel': '''flight flight ticket air ticket airfare hotel hotel booking hostel stay airbnb resort
        holiday trip vacation tour package visa fee passport fee travel insurance train reservation
        sleeper ticket bus booking volvo ticket luggage suitcase sightseeing tour guide
        homestay weekend trip goa trip airport transfer airport taxi forex card'''.split('\n'),
}

# Fixed multi-word phrases kept intact (everything else in the lexicon is one term per word run).
PHRASES = ('ice cream', 'cold drink', 'office lunch', 'street food', 'pani puri', 'vada pav', 'swiggy order',
           'zomato order', 'food delivery', 'dinner out', 'filter coffee', 'masala dosa', 'paneer tikka',
           'fried rice', 'chicken curry', 'fish fry', 'butter chicken', 'coffee beans', 'mess food',
           'hostel mess', 'dhaba meal', 'bhel puri', 'gulab jamun', 'soft drink', 'energy drink', 'pav bhaji',
           'chole bhature', 'evening snacks', 'team lunch', 'birthday dinner', 'family dinner', 'cooking oil',
           'big basket', 'blinkit order', 'zepto order', 'tea leaves', 'coffee powder', 'dry fruits',
           'dish soap', 'toilet paper', 'cleaning supplies', 'household items', 'monthly ration', 'water can',
           'drinking water', 'eggs tray', 'baby food', 'pet food', 'bus ticket', 'bus pass', 'metro card',
           'train ticket', 'local train', 'uber ride', 'ola ride', 'auto ride', 'car wash', 'bike service',
           'car service', 'tyre puncture', 'bike repair', 'car repair', 'oil change', 'train pass', 'bus fare',
           'cab fare', 'train fare', 'driver salary', 'ev charging', 'vehicle insurance', 'car insurance',
           'bike insurance', 'pollution check', 'road tax', 'scooter rental', 'bicycle repair', 'phone cover',
           'charging cable', 'power bank', 'mobile phone', 'pressure cooker', 'mixer grinder', 'home decor',
           'amazon order', 'flipkart order', 'myntra order', 'meesho order', 'printer ink', 'light bulb',
           'extension board', 'hair dryer', 'cricket bat', 'yoga shoes', 'gym wear', 'school fees',
           'college fees', 'tuition fees', 'online course', 'exam fee', 'exam form', 'admission fee',
           'library fee', 'school supplies', 'school uniform', 'school bag', 'lab fee', 'workshop fee',
           'certification exam', 'test series', 'study material', 'stationery for school', 'hostel fees',
           'semester fees', 'university fees', 'language class', 'music class', 'dance class', 'coding course',
           'course fee', 'movie tickets', 'show tickets', 'amusement park', 'water park', 'video game',
           'game top up', 'board game', 'club entry', 'pub night', 'museum ticket', 'zoo ticket',
           'sports match', 'stadium tickets', 'comedy show', 'standup show', 'event pass', 'escape room',
           'birthday party', 'trekking event', 'electricity bill', 'power bill', 'water bill', 'gas bill',
           'gas cylinder', 'lpg cylinder', 'piped gas', 'internet bill', 'wifi bill', 'phone bill',
           'mobile bill', 'postpaid bill', 'phone recharge', 'mobile recharge', 'dth recharge',
           'data recharge', 'house rent', 'society maintenance', 'property tax', 'municipal tax',
           'credit card bill', 'loan emi', 'insurance premium', 'life insurance', 'health insurance premium',
           'landline bill', 'cable bill', 'dry cleaning', 'maid salary', 'cook salary', 'newspaper bill',
           'milk bill', 'security deposit', 'netflix subscription', 'spotify premium', 'prime video',
           'amazon prime', 'disney hotstar', 'youtube premium', 'apple music', 'icloud storage', 'google one',
           'cloud storage', 'monthly subscription', 'annual subscription', 'magazine subscription',
           'newspaper subscription', 'gym membership', 'club membership', 'app subscription',
           'software subscription', 'music subscription', 'ott subscription', 'streaming subscription',
           'chatgpt plus', 'membership fee', 'domain renewal', 'hosting plan', 'doctor visit', 'hospital bill',
           'dental checkup', 'eye checkup', 'eye drops', 'contact lenses', 'lab test', 'blood test', 'x ray',
           'health checkup', 'first aid', 'ayurvedic medicine', 'protein powder', 'flight ticket',
           'air ticket', 'hotel booking', 'hostel stay', 'holiday trip', 'tour package', 'visa fee',
           'passport fee', 'travel insurance', 'train reservation', 'sleeper ticket', 'bus booking',
           'volvo ticket', 'tour guide', 'weekend trip', 'goa trip', 'airport transfer', 'airport taxi',
           'forex card', 'restaurant meal', 'online course', 'tuition fees', 'gym membership')


def terms():
    """Return unique (term, category); phrases are matched greedily, other words stand alone."""
    phrases = sorted(set(PHRASES), key=lambda p: -len(p.split()))
    result = {}
    for category, lines in LEXICON.items():
        text = ' '.join(' '.join(lines).split())
        words = text.split(' ')
        i = 0
        while i < len(words):
            for phrase in phrases:
                n = len(phrase.split())
                if ' '.join(words[i:i + n]) == phrase:
                    term, i = phrase, i + n
                    break
            else:
                term, i = words[i], i + 1
            # A term listed under two categories is ambiguous and excluded from every split.
            if term in result and result[term] != category:
                result[term] = None
            else:
                result.setdefault(term, category)
    return sorted((t, c) for t, c in result.items() if c is not None)


TRAIN_PREFIXES = ('', '', '', 'cedar ', 'lotus ', 'my ', 'the ', 'evening ', 'weekly ')
TRAIN_SUFFIXES = (' again', ' at mall', ' for kids', ' near work', ' this week')
DEV_PREFIXES = ('maple ', 'extra ', 'new ', 'quick ')
DEV_SUFFIXES = ('', ' for office', ' today')
TEST_PREFIXES = ('silver ', 'orange ', 'small ', 'big ', 'family ')
TEST_SUFFIXES = ('', ' with friends', ' for home')
DEV_UNKNOWN_WORDS = ('payment', 'misc', 'stuff', 'expense', 'entry', 'item')
TEST_UNKNOWN_WORDS = ('thing', 'rahul', 'random', 'others', 'purchase', 'sundry')


def _plural(term, rng):
    return term + 's' if rng.random() < .3 and not term.endswith('s') else term


def _typo(term, rng):
    words = term.split(' ')
    longest = max(range(len(words)), key=lambda i: len(words[i]))
    word = words[longest]
    if len(word) < 6:
        return term
    i = rng.randrange(2, len(word) - 2)
    words[longest] = word[:i] + word[i + 1:]
    return ' '.join(words)


def _gibberish(rng, count):
    letters = 'bcdfghjklmnpqrstvwxyz'
    vowels = 'aeiou'
    return [''.join(rng.choice(letters) + rng.choice(vowels) for _ in range(rng.randint(2, 4))) for _ in range(count)]


def make_splits(seed=5309):
    """Train on every lexicon term (augmented); evaluate on held-out surface variants and unknowns.

    Unknown descriptions (expected None) check abstention. Variants never repeat a training string.
    """
    rng = random.Random(seed)
    rows = terms()
    train, seen = [], set()
    fillers = _gibberish(rng, 400)
    for term, category in rows:
        for _ in range(7):
            variant = _plural(rng.choice(TRAIN_PREFIXES) + term, rng)
            if rng.random() < .35:
                variant = rng.choice(fillers) + ' ' + variant
            if rng.random() < .25:
                variant = variant + rng.choice(TRAIN_SUFFIXES) if rng.random() < .5 else variant + ' ' + rng.choice(fillers)
            if rng.random() < .3:
                variant = variant.upper() if rng.random() < .5 else variant.title()
            train.append((variant, category)); seen.add(variant.lower())
    # Nonsense descriptions train towards a uniform target (label None): unfamiliar words should abstain.
    train.extend((word, None) for word in fillers[:300])
    by_category = {}
    for term, category in rows:
        by_category.setdefault(category, []).append(term)

    def variants(prefixes, suffixes, typo, compound, unknown_words):
        out = []
        for term, category in rows:
            text = rng.choice(prefixes) + (_typo(term, rng) if typo and rng.random() < .3 else term) + rng.choice(suffixes)
            if text.lower() not in seen:
                out.append((text, category))
        if compound:
            for category, items in by_category.items():
                for _ in range(4):
                    a, b = rng.sample(items, 2)
                    out.append((a + ' and ' + b, category))
        out.extend((w, None) for w in unknown_words + tuple(_gibberish(rng, 30)))
        return out

    dev = variants(DEV_PREFIXES, DEV_SUFFIXES, False, False, DEV_UNKNOWN_WORDS)
    test = variants(TEST_PREFIXES, TEST_SUFFIXES, True, True, TEST_UNKNOWN_WORDS)
    return train, dev, test


def term_holdout(seed=5309):
    """Diagnostic only: hold out whole terms to measure unseen-word generalisation."""
    rng = random.Random(seed)
    rows = terms()
    rng.shuffle(rows)
    n = len(rows) // 10
    held, kept = rows[:n], rows[n:]
    train = [(rng.choice(TRAIN_PREFIXES) + t, c) for t, c in kept for _ in range(7)]
    return train, held


WORD = re.compile(r'[a-z]+')
