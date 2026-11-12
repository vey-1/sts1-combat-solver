# Combat HP prices: where they are set and who reads them

Every hand-set "this is worth so much HP" number of the automatic potion choice is set at the top of
`overlay/auto_policy.py`. Nothing else stores a copy: the Java side reads a file the build writes from those constants.

This page is the contract between the three parts. Changing a price means editing `auto_policy.py` and the fixtures in
`tools/check_potion_policy.py` that pin it; nothing on this page changes.

## 1. Python: `effective_loss(game, branch, profile)`

`overlay/auto_policy.py`. Prices one winning branch on one HP scale and returns

| Key | Meaning |
| --- | --- |
| `loss` | the branch's HP loss, weighted by `hp_weight(game)` (a healing Boss discounts it) |
| `potions` | the keep cost of every bottle the branch drinks, plus a heal the bottle would have given anyway |
| `revival` | max HP + `REVIVAL_HP` for each Lizard Tail or Fairy the branch spends |
| `total` | the sum; `recommend` picks the smallest |

`potion_tier(potion, act)` names the `POTION_HP` row (`premium`, `strong`, `ordinary`) that prices a bottle.

## 2. The price file `hp-prices.json`

Written by `tools/export_hp_prices.py <output>` during `overlay/build.py`, into the root of the mod jar. It is build
output and is not committed.

| Key | From | Meaning |
| --- | --- | --- |
| `schema` | — | `1` |
| `smoke_loss_hp` | `SMOKE_LOSS_HP` | raw loss a first-turn Smoke probe win may cost before escaping is preferred |
| `revival_hp` | `REVIVAL_HP` | a spent revival costs max HP plus this |
| `boss_heal_weight` | `BOSS_HEAL_WEIGHT` | share of a healing Boss fight's loss still missing in the next act (A5+; 0 below) |
| `potion_hp` | `POTION_HP` | per tier, three keep costs in this order: `REFILL`, `RESERVE`, `KEEP` |
| `potion_heal_pct` | `POTION_HEAL_PCT` | bottles that heal the same whenever drunk, in % of max HP |
| `premium`, `strong` | `PREMIUM`, `STRONG` | bottle ids of the two upper tiers; `strong` includes `premium` |
| `strong_in_act` | `STRONG_IN_ACT` | bottles that are strong only in the listed acts |

The three situations of a `potion_hp` row: `REFILL` — drinking frees a slot that a drop will fill; `RESERVE` — the deck
screen says to keep this kind of bottle for a near threat; `KEEP` — otherwise.

## 3. Java: `sts1solver.HpPrices`

| Method | Returns |
| --- | --- |
| `tier(String potion, int act)` | `premium`, `strong` or `ordinary` |
| `keep(String potion, int act, Slot slot)` | the keep cost in HP; `Slot` is `REFILL`, `RESERVE` or `KEEP` |
| `smokeLoss()` | `smoke_loss_hp` |
| `revival(int maxHp)` | max HP + `revival_hp` |
| `bossHealWeight(int ascension)` | `boss_heal_weight` from A5, else 0 |

A missing file, a schema other than 1 or a missing key throws `IllegalStateException` naming `hp-prices.json` on the
first call. There are no default prices. No rule calls `HpPrices` yet; `PotionRewards` and `EventRules` keep their own
tables until they are moved over.

`overlay/test/HpPricesCheck.java` (run by the build) compares Java with `auto_policy.py` for every listed bottle and one
unlisted bottle, in acts 1 to 4 and all three situations, and requires that a changed number, a dropped
`strong_in_act` entry, a missing file and an unreadable file are each rejected.

## Game state the prices read

Today's prices read the fields marked "read". The others are already exported and are listed because a price that
depends on how much of the act is left (HP worth more when low, #101) needs them; nothing reads them for pricing yet.

| Field | Written by | Status |
| --- | --- | --- |
| `current_hp`, `max_hp`, `act`, `ascension_level`, `relics`, `potions` | CommunicationMod state export | read |
| `solver_auto_context.boss_heal` | `SolverMod.autoContext()` | read |
| `solver_auto_context.potion_drop_probability`, `.potion_reward_blocked` | `SolverMod.autoContext()` | read |
| `solver_auto_context.room`, `.x`, `.y`, `.deck` | `SolverMod.autoContext()` | read (deck screen: reserve or not) |
| `solver_auto_context.smoke_loss_threshold`, `.smoke_opening`, `.run_auto` | `SolverMod.autoContext()` | read |
| `solver_auto_context.boss_candidates_remaining` | `SolverMod.autoContext()` | exported, not read (A20 act 3: is a second Boss still ahead) |
| `solver_final_act` | `RecoveryExportPatch` | exported, not read (does act 3 lead into act 4) |

A host that runs `auto_policy.py` without the Mod (an evaluation harness) has to supply the same fields with the same
meaning, or the prices it measures are not the Mod's.
