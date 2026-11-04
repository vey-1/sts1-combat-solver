"""Checks for the Smoke Bomb escape priced in real HP (docs/hp-value.md, 烟雾弹): solver_smoke_allowance."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'overlay'))
from auto_policy import smoke_probe_keeps, smoke_threshold

HALLWAY, ELITE = 8.75, 18.375  # 中 + 中, 中 + 很高 in acts 1-3 (PotionValueCheck pins these from Java)


def game(hp, cost, allowance=None, **extra):
    out = dict(current_hp=hp, max_hp=80, solver_hp_cost=[cost(k) for k in range(hp + 1)],
               solver_auto_context={'smoke_loss_threshold': 15}, **extra)
    if allowance is not None:
        out['solver_smoke_allowance'] = allowance
    return out


def win(hp, post_hp=None, **extra):
    return dict(outcome=1, hp=hp, post_hp=hp if post_hp is None else post_hp, **extra)


cheap = lambda k: 0.35 * k   # high HP with Burning Blood: 20 raw HP costs 7
dear = lambda k: 1.5 * k     # low HP, no healing relic: the low-HP risk makes HP dearer than raw

# S1: the F6 Gremlin Nob of 2026-11-04 (80/82, Burning Blood, elite): a 20-HP potion-free win keeps the bomb.
g = game(80, cheap, ELITE)
assert smoke_probe_keeps(win(60), g)
assert smoke_threshold(g) == 15, smoke_threshold(g)  # 53 by price, capped by smokeLossThreshold
# S2: no potion-free win still escapes.
assert not smoke_probe_keeps(dict(outcome=0, hp=0, post_hp=0), g)
assert not smoke_probe_keeps(dict(outcome=2, hp=0, post_hp=0), g)
assert not smoke_probe_keeps(win(60, used_fairy=1), g)
assert not smoke_probe_keeps(win(60, used_tail=True), g)
assert not smoke_probe_keeps(win(60, new_permanent_curse=True), g)
# S3: low HP, hallway: losing 15 costs 22.5 > 8.75, escape.
low = game(20, dear, HALLWAY)
assert not smoke_probe_keeps(win(5), low)
assert smoke_threshold(low) == 6  # 1.5 * 6 = 9 > 8.75, below the cap
print('PASS: S1 high HP keeps the bomb on a 20-HP elite win; S2 no win / revival / curse escapes; S3 low HP escapes')

# S4: same HP and loss, elite keeps, hallway escapes: the forfeited reward is in the rule.
assert smoke_probe_keeps(win(50), game(80, cheap, ELITE))       # 30 HP costs 10.5 <= 18.375
assert not smoke_probe_keeps(win(50), game(80, cheap, HALLWAY))  # 10.5 > 8.75
# S5: a cost exactly at the allowance is a tie and keeps.
assert smoke_probe_keeps(win(70), game(80, lambda k: k * 0.875, HALLWAY))  # 10 * 0.875 = 8.75
assert smoke_threshold(game(80, lambda k: k * 0.875, HALLWAY)) == 11
assert smoke_threshold({**game(80, cheap, ELITE), 'solver_auto_context': {'smoke_loss_threshold': 60}}) == 53
# The table is read after this fight's victory heal (post_hp), like health()['loss'].
assert smoke_probe_keeps(win(55, post_hp=61), game(80, cheap, HALLWAY))      # 19 HP costs 6.65
assert not smoke_probe_keeps(win(50, post_hp=50), game(80, cheap, HALLWAY))  # 30 HP costs 10.5
print('PASS: S4 elite keeps where a hallway escapes; S5 a tie keeps; the loss is read after the victory heal')

# S6: without a valid allowance or HP table the old raw rule applies unchanged (15, strict, raw hp).
for old in (game(80, cheap), game(80, cheap, -1), game(80, cheap, True), game(80, cheap, 'x'),
            {**game(80, cheap, ELITE), 'solver_hp_cost': None}, {**game(80, cheap, ELITE), 'solver_hp_cost': [1, 2]}):
    assert smoke_threshold(old) == 15
    assert smoke_probe_keeps(win(66, post_hp=80), old)       # raw 14 < 15
    assert not smoke_probe_keeps(win(65, post_hp=80), old)   # raw 15, the victory heal is not counted
custom = {**game(80, cheap), 'solver_auto_context': {'smoke_loss_threshold': 17}}
assert smoke_threshold(custom) == 17 and smoke_probe_keeps(win(64), custom)
print('PASS: S6 a missing or malformed allowance or HP table keeps the old smokeLossThreshold rule')
