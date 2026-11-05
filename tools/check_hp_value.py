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

# V2: past the table (a curse's HP), the extension stops at solver_hp_death; a missing or bad death keeps the old extension.
from recovery import hp_cost, hp_death
capped = {**game, 'solver_hp_death': 4.5}
assert effective_loss(capped, dict(loss=6)) == 3.0                     # 3.0 is under the death price
assert effective_loss({**capped, 'solver_hp_death': 2.5}, dict(loss=6)) == 2.5   # capped at death
assert [effective_loss({**capped, 'solver_hp_death': 1.0}, dict(loss=k)) for k in range(5)] == table  # the table itself is untouched
assert hp_cost([0, 5, 4], 4, death=100) == 2                          # a falling last step (Meat on the Bone) still extends down
for bad in (None, 'x', True, -1):
    assert hp_death({'solver_hp_death': bad}) is None, bad
    assert effective_loss({**game, 'solver_hp_death': bad}, dict(loss=6)) == 3.0, bad
assert hp_cost([0, 10, 20], 9, death=100) == 90 and hp_cost([0, 10, 20], 12, death=100) == 100
print('PASS: a loss past current HP extends the table up to solver_hp_death; without it the extension is uncapped')

# The revival price is solver_hp_death when present, max HP + 20 otherwise (the same number from the Mod).
tail = dict(id='tail', available=True, won=True, loss=0, potion_uses=[], empty_potion_slots=0, used_tail=True)
hurt = dict(id='hurt', available=True, won=True, loss=60, potion_uses=[], empty_potion_slots=0)
flat = {**base, 'solver_hp_cost': [float(k) for k in range(61)]}
assert recommend({**flat, 'solver_hp_death': 100.0}, [tail, hurt])['branch'] == 'hurt'   # 60 < 100
assert recommend({**flat, 'solver_hp_death': 50.0}, [tail, hurt])['branch'] == 'tail'    # 50 < 60
assert recommend(flat, [tail, hurt])['branch'] == 'hurt'                                 # fallback 80 + 20
print('PASS: a used revival is priced at solver_hp_death, or max HP + 20 without it')
