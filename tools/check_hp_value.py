"""Checks for the backend side of HP value (issue #101): reading solver_hp_cost and the fallback."""
from pathlib import Path
import random
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'overlay'))
from auto_policy import recommend
from recovery import boss_heal_weight, effective_loss, hp_cost_table

# Without the Mod's table, effective loss is exactly loss x Boss-heal weight: the rule before #101.
rng = random.Random(101)
for _ in range(5000):
    top = rng.randint(1, 150)
    game = dict(current_hp=rng.randint(1, top), max_hp=top, ascension_level=rng.choice([0, 4, 5, 20]),
                solver_auto_context=rng.choice([{}, {'boss_heal': True}, {'boss_heal': False}, {'boss_heal': 1}]))
    branch = dict(loss=rng.randint(0, 200))
    assert effective_loss(game, branch) == branch['loss'] * boss_heal_weight(game), (game, branch)
for context, ascension, weight in [({}, 20, 1), ({'boss_heal': True}, 20, .25), ({'boss_heal': True}, 4, 0),
                                   ({'boss_heal': 1}, 20, 1), (None, 20, 1)]:
    assert boss_heal_weight(dict(solver_auto_context=context, ascension_level=ascension)) == weight
print('PASS: without solver_hp_cost the backend keeps loss x Boss-heal weight (5000 random cases)')

# With the table: entry k is the cost of losing k; the act heal is inside it, so it is not applied again.
table = [0, 0.5, 1.0, 1.5, 2.0]
game = dict(current_hp=4, max_hp=80, ascension_level=20, solver_auto_context={'boss_heal': True}, solver_hp_cost=table)
assert [effective_loss(game, dict(loss=k)) for k in range(5)] == table
assert effective_loss(game, dict(loss=6)) == 3.0          # past current HP (a curse): extend by the last step
assert effective_loss({**game, 'solver_hp_cost': [0]}, dict(loss=3)) == 3  # one entry: extend by 1 per HP
for bad in (None, [], [1, 2], ['0', 1], [0, -1], [0, True], 'x'):
    assert hp_cost_table({**game, 'solver_hp_cost': bad}) is None, bad
    assert effective_loss({**game, 'solver_hp_cost': bad}, dict(loss=4)) == 4 * .25, bad
print('PASS: solver_hp_cost is read per HP lost, extended past current HP, and a malformed table falls back')

# Through the real recommend(): a cheap table keeps the bottle that the raw loss would spend.
base = dict(current_hp=60, max_hp=80, act=1, ascension_level=20, screen_type='NONE',
            combat_state={'turn': 1, 'monsters': []}, potions=[{'id': 'Strength Potion'}],
            solver_auto_context={'potion_drop_probability': .4, 'deck': [{'id': 'Strike_R', 'cost': 1, 'base_damage': 6}]})
keep = dict(id='no-potion', available=True, won=True, loss=14, potion_uses=[], empty_potion_slots=0)
drink = dict(id='potion-0', available=True, won=True, loss=0, potion_uses=[{'slot': 0}], empty_potion_slots=1)
assert recommend(base, [keep, drink])['branch'] == 'potion-0'          # raw: 14 HP > Strength's price 10
cheap = [round(k * 0.5, 3) for k in range(61)]
assert recommend({**base, 'solver_hp_cost': cheap}, [keep, drink])['branch'] == 'no-potion'  # 7 < 10
assert recommend({**base, 'solver_hp_cost': cheap}, [drink])['branch'] == 'potion-0'          # survival still drinks
print('PASS: recommend() uses solver_hp_cost: a cheaper HP keeps the bottle, the only winning line still drinks')
