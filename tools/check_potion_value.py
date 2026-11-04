"""Checks for the backend side of potion value (docs/hp-value.md, 药水的价值): reading solver_potion_price."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'overlay'))
from auto_policy import potion_price_table, recommend

STRIKES = [{'id': 'Strike_R', 'cost': 1, 'base_damage': 6}]
MEDIUM = 4.375  # tier 中 in act 1: the real cost of 10 raw HP in the typical situation

# Typical situation (act 1, max 80, current 56, Burning Blood): real cost of k raw HP is 0.4375 k.
typical = dict(current_hp=56, max_hp=80, act=1, ascension_level=20, screen_type='NONE',
               combat_state={'turn': 1, 'monsters': []}, relics=[{'id': 'Burning Blood'}],
               potions=[{'id': 'Strength Potion'}, {'id': 'Potion Slot'}],
               solver_hp_cost=[0.4375 * k for k in range(57)], solver_potion_price=[MEDIUM, None],
               solver_auto_context={'potion_drop_probability': .4, 'deck': STRIKES})


def keep(loss):
    return dict(id='no-potion', available=True, won=True, loss=loss, potion_uses=[], empty_potion_slots=1)


def drink(slot=0, loss=0, empty=2, **extra):
    uses = [{'slot': s} for s in (slot if isinstance(slot, tuple) else (slot,))]
    return dict(id='potion', available=True, won=True, loss=loss, potion_uses=uses, empty_potion_slots=empty, **extra)


def threshold(game, branch):
    """The price recommend() reports for a chosen potion branch (baseline saving 60 forces the drink)."""
    reason = recommend(game, [keep(min(60, game['current_hp'])), branch])['reason']
    assert reason.startswith('省 ') and '用药门槛 ' in reason, reason
    return float(reason.split('用药门槛 ')[1].split('；')[0])


# 1. Threshold alignment: the tier's 10 raw HP is exactly where the solver starts drinking.
assert potion_price_table(typical) == [MEDIUM, None]
assert recommend(typical, [keep(11), drink()])['branch'] == 'potion'     # 11 x 0.4375 = 4.8125 > 4.375
assert recommend(typical, [keep(9), drink()])['branch'] == 'no-potion'   # 9 x 0.4375 = 3.9375 < 4.375
# Saving exactly 10: equal score 4.375; the tie-break key (score, loss, uses) takes the lower raw loss, the drink.
assert recommend(typical, [keep(10), drink()])['branch'] == 'potion'
assert threshold(typical, drink()) == MEDIUM
print('PASS: typical act-1 situation drinks a medium-tier bottle at 11 and 10 raw HP saved, keeps it at 9 (tie goes to the lower loss)')

# 2. Reserve surcharge: near the Boss with a weak deck the old path charges 20; the table path does not.
near_boss = {**typical, 'solver_auto_context': {'potion_drop_probability': .4, 'deck': STRIKES,
                                                'room': 'MonsterRoom', 'y': 12}, 'map': []}
old_near_boss = {k: v for k, v in near_boss.items() if k not in ('solver_potion_price', 'solver_hp_cost')}
assert recommend(near_boss, [keep(1), drink()])['assessment']['reserve']
assert threshold(near_boss, drink()) == MEDIUM
assert threshold(old_near_boss, drink()) == 20
assert recommend(near_boss, [keep(13), drink()])['branch'] == 'potion'
assert recommend(old_near_boss, [keep(19), drink()])['branch'] == 'no-potion'  # old: 19 < 20
print('PASS: with solver_potion_price the near-Boss reserve surcharge is gone (4.375), without it the old 20 applies')

# 3. Refill x0.5: guaranteed drop (White Beast Statue) or full bar with drop chance >= 60%, and a freed slot.
statue = {**typical, 'relics': [{'id': 'White Beast Statue'}]}
assert threshold(statue, drink(empty=1)) == MEDIUM / 2
assert threshold(statue, drink(empty=0)) == MEDIUM
assert recommend(statue, [keep(7), drink(empty=1)])['branch'] == 'potion'     # 3.0625 > 2.1875
assert recommend(statue, [keep(7), drink(empty=0)])['branch'] == 'no-potion'  # 3.0625 < 4.375
full = {**typical, 'potions': [{'id': 'Strength Potion'}, {'id': 'Block Potion'}], 'solver_potion_price': [MEDIUM, MEDIUM],
        'solver_auto_context': {'potion_drop_probability': .6, 'deck': STRIKES}}
assert threshold(full, drink(empty=1)) == MEDIUM / 2
assert threshold(full, drink(empty=0)) == MEDIUM
assert threshold({**full, 'solver_auto_context': {'potion_drop_probability': .59, 'deck': STRIKES}}, drink(empty=1)) == MEDIUM
assert threshold({**full, 'potions': [{'id': 'Strength Potion'}, {'id': 'Potion Slot'}]}, drink(empty=1)) == MEDIUM  # not full
assert threshold({**statue, 'relics': [{'id': 'White Beast Statue'}, {'id': 'Sozu'}]}, drink(empty=1)) == MEDIUM
print('PASS: a guaranteed or likely refill halves the table price only when the branch frees a slot')

# 4. Null entries fall back per use; an invalid table falls back as a whole.
plain = {**typical, 'potions': [{'id': 'FairyPotion'}, {'id': 'Strength Potion'}], 'solver_potion_price': [None, MEDIUM]}
assert threshold(plain, drink(slot=0)) == 5                      # old path: ordinary bottle
assert threshold(plain, drink(slot=1)) == MEDIUM
assert threshold(plain, drink(slot=(0, 1))) == 5 + MEDIUM        # mixed branch: each use priced on its own path
for bad in ([MEDIUM], [MEDIUM, None, None], [None, -1], [None, True], [None, '5.25'], 'x', {1: MEDIUM}, None):
    game = {**plain, 'solver_potion_price': bad}
    assert potion_price_table(game) is None, bad
    assert threshold(game, drink(slot=1)) == 10, bad              # old path: strong bottle
assert potion_price_table({**plain, 'solver_potion_price': [None, 0]}) == [None, 0]
assert threshold({**plain, 'solver_potion_price': [None, 0]}, drink(slot=1)) == 0
print('PASS: a null entry uses the old price for that bottle; a wrong-length, negative, bool, string or non-list table is ignored')
