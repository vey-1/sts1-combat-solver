"""Write the HP price constants of overlay/auto_policy.py as hp-prices.json for the Java side (HpPrices).

auto_policy.py stays the only place a price is set; the build calls this, nothing is committed.
--cases also writes what each bottle costs per act and situation, for HpPricesCheck to compare with Java.
"""
import argparse
import json
from pathlib import Path
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('output', type=Path)
parser.add_argument('--cases', type=Path)
parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
args = parser.parse_args()
sys.path.insert(0, str(args.root.resolve() / 'overlay'))
import auto_policy as policy

SLOTS = ('REFILL', 'RESERVE', 'KEEP')  # the order of a POTION_HP row
prices = dict(
    schema=1,
    smoke_loss_hp=policy.SMOKE_LOSS_HP,
    revival_hp=policy.REVIVAL_HP,
    boss_heal_weight=policy.BOSS_HEAL_WEIGHT,
    potion_hp={tier: list(row) for tier, row in sorted(policy.POTION_HP.items())},
    potion_heal_pct=dict(sorted(policy.POTION_HEAL_PCT.items())),
    premium=sorted(policy.PREMIUM),
    strong=sorted(policy.STRONG),
    strong_in_act={potion: sorted(acts) for potion, acts in sorted(policy.STRONG_IN_ACT.items())},
)
args.output.write_text(json.dumps(prices, ensure_ascii=True, indent=1) + '\n', encoding='ascii', newline='\n')

if args.cases:
    potions = sorted(policy.STRONG | set(policy.STRONG_IN_ACT) | set(policy.POTION_HEAL_PCT) | {'Fire Potion', 'NotAPotion'})
    bottles = [dict(potion=potion, act=act, slot=slot, tier=policy.potion_tier(potion, act),
                    hp=policy.POTION_HP[policy.potion_tier(potion, act)][index])
               for potion in potions for act in (1, 2, 3, 4) for index, slot in enumerate(SLOTS)]
    cases = dict(bottles=bottles, smoke_loss=policy.SMOKE_LOSS_HP,
                 revival=[dict(max_hp=max_hp, hp=max_hp + policy.REVIVAL_HP) for max_hp in (1, 75, 80, 99)],
                 boss_heal_weight=[dict(ascension=a, weight=policy.BOSS_HEAL_WEIGHT if a >= 5 else 0) for a in (0, 4, 5, 20)])
    args.cases.write_text(json.dumps(cases, ensure_ascii=True) + '\n', encoding='ascii', newline='\n')
