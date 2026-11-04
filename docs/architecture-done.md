# Combat Solver architecture: superseded text

Text replaced in [architecture.md](architecture.md), kept unchanged.

## Route item 6, replaced 2026-11-04

6. HP value, H1 and H2 (proposed): H1 adds `HpValue` and `PotionValue`, their `solver_hp_cost` and `solver_potion_price` exports and the first consumer, the combat potion recommendation; without the fields the backend keeps the old rules (loss times the Boss-heal weight, the old potion prices). Acceptance: the spec's worked examples and tier prices pass in Java, Boss fights followed by an act heal reproduce the old HP rule, and the player reviews every changed recommendation in a before/after comparison. H2 moves the outside-combat HP rules listed in the spec to `HpValue` one at a time, each with its own evaluation evidence. The Smoke Bomb escape rule is the first, taken ahead of the rest after an in-game test (2026-11-04) showed a full-HP escape from an act 1 elite. The second is the event rules that pay HP for a reward (Knowing Skull, Shining Light, Scrap Ooze, World of Goop, Cursed Tome, Sensory Stone): reward prices in raw HP at the typical situation, fitted to the player's 154 A20 Ironclad runs; without `solver_auto_context` they keep the old rules. Search scoring and the search's potion gate stay on raw HP.
