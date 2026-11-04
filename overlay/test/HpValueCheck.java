package sts1solver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** docs/hp-value.md worked examples and the game-state reading of HpValue (issue #101). */
public final class HpValueCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    static Set<String> relics(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    /** Remaining fights of an act: hallways at the act's hallway damage, then one Boss. */
    static int[] act(int act, int fights) {
        int[] out = new int[fights];
        for (int i = 0; i < fights; i++) out[i] = HpValue.DAMAGE[act - 1][i == fights - 1 ? 1 : 0];
        return out;
    }

    static void example(String name, int k, int hp, HpValue.Context c, double expected) {
        double got = HpValue.cost(k, hp, c);
        // The spec shows 3 decimals; HpValue itself does not round.
        check(Math.abs(got - expected) < 5e-4, name + ": expected " + expected + ", got " + got);
    }

    static JsonObject game(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    static int[] future(String json) {
        return HpValue.context(game(json)).future;
    }

    public static void main(String[] args) {
        Set<String> bb = relics("Burning Blood");
        // docs/hp-value.md, worked examples (lambda 0.25, knee 30%, weight 4, A20, max HP 80).
        example("E1", 10, 70, new HpValue.Context(80, 20, act(1, 4), true, bb), 4.375);
        example("E2", 10, 79, new HpValue.Context(80, 20, act(1, 4), true, bb), 2.875);
        example("E3", 10, 79, new HpValue.Context(80, 20, act(1, 4), true, relics()), 4.375);
        example("E4", 20, 50, new HpValue.Context(80, 20, new int[0], true, bb), 5.0);
        example("E5", 20, 50, new HpValue.Context(80, 4, new int[0], true, bb), 0.0);
        example("E6", 10, 70, new HpValue.Context(80, 20, act(2, 4), true, relics("Burning Blood", "Mark of the Bloom")), 10.0);
        example("E7", 10, 60, new HpValue.Context(80, 20, act(2, 1), true, bb), 4.375);
        example("E8", 15, 80, new HpValue.Context(80, 20, act(4, 1), false, bb), 15.0);
        example("E9", 12, 78, new HpValue.Context(80, 20, act(2, 6), true, relics("Black Blood")), 0.917);
        example("E10", 10, 45, new HpValue.Context(80, 20, act(1, 4), true, relics("Burning Blood", "Meat on the Bone")), 0.625);
        example("E11", 10, 30, new HpValue.Context(80, 20, act(2, 1), true, bb), 7.375);
        example("E12", 10, 50, new HpValue.Context(80, 20, act(2, 1), true, bb), 4.375);
        example("E13", 10, 30, new HpValue.Context(80, 20, act(2, 4), true, relics("Burning Blood", "Mark of the Bloom")), 16.375);
        // This fight's victory heal is already in the backend's loss: a Boss fight has no fight left to heal in,
        // so counting Burning Blood once more would give 0.25 x (20 - 6) = 3.5 instead of E4's 5.0.
        example("E0 no gain or loss", 0, 50, new HpValue.Context(80, 20, act(1, 4), true, bb), 0.0);
        example("Magic Flower scales Burning Blood to 9", 10, 79, new HpValue.Context(80, 20, act(1, 4), true,
            relics("Burning Blood", "Magic Flower")), 1.063);
        System.out.println("PASS: docs/hp-value.md worked examples E1-E13, zero loss, Magic Flower");

        String base = "\"max_hp\":80,\"current_hp\":60,\"ascension_level\":20,\"relics\":[{\"id\":\"Burning Blood\"}],";
        // Act 1 hallway on row 8: 7 floors to the Boss, 4 fights left by the table: 3 hallways then the Boss.
        check(Arrays.equals(future("{" + base + "\"act\":1,\"solver_auto_context\":{\"room\":\"MonsterRoom\",\"y\":8}}"),
            new int[]{4, 4, 4, 13}), "act 1 row 8");
        // Act 1 Boss room: nothing left in the act; the act heal follows.
        HpValue.Context boss = HpValue.context(game("{" + base + "\"act\":1,\"solver_auto_context\":{\"room\":\"MonsterRoomBoss\",\"y\":15}}"));
        check(boss.future.length == 0 && boss.transition, "act 1 Boss room");
        // A20 act 3: during the first Boss the second is ahead; the act heal needs the final act.
        HpValue.Context first = HpValue.context(game("{" + base + "\"act\":3,\"solver_final_act\":true,"
            + "\"solver_auto_context\":{\"room\":\"MonsterRoomBoss\",\"y\":15,\"boss_candidates_remaining\":2}}"));
        check(Arrays.equals(first.future, new int[]{11}) && first.transition, "A20 act 3 first Boss");
        HpValue.Context last = HpValue.context(game("{" + base + "\"act\":3,\"solver_final_act\":false,"
            + "\"solver_auto_context\":{\"room\":\"MonsterRoomBoss\",\"y\":15,\"boss_candidates_remaining\":1}}"));
        check(last.future.length == 0 && !last.transition && HpValue.cost(10, 60, last) == 0.0,
            "act 3 last Boss without act 4 ends the run: lost HP costs nothing");
        check(Arrays.equals(future("{" + base + "\"act\":3,\"solver_final_act\":true,"
            + "\"solver_auto_context\":{\"room\":\"MonsterRoom\",\"y\":12,\"boss_candidates_remaining\":2}}"), new int[]{6, 11, 11}),
            "A20 act 3 hallway with two Bosses ahead");
        check(Arrays.equals(future("{\"max_hp\":80,\"current_hp\":60,\"ascension_level\":19,\"act\":3,\"solver_final_act\":true,"
            + "\"solver_auto_context\":{\"room\":\"MonsterRoom\",\"y\":12,\"boss_candidates_remaining\":2}}"), new int[]{6, 11}),
            "below A20 act 3 has one Boss");
        // Act 4: Shield and Spear, then the Heart, no act heal.
        HpValue.Context spear = HpValue.context(game("{" + base + "\"act\":4,\"solver_auto_context\":{\"room\":\"MonsterRoomElite\",\"y\":2}}"));
        check(Arrays.equals(spear.future, new int[]{69}) && !spear.transition, "act 4 elite");
        check(Arrays.equals(future("{" + base + "\"act\":1,\"solver_auto_context\":{\"room\":\"MonsterRoom\",\"y\":-1}}"),
            new int[]{4, 4, 4, 4, 4, 4, 13}), "off the map: the longest table entry");
        check(HpValue.context(game("{\"max_hp\":80,\"act\":1}")) == null, "no auto context: no table");
        System.out.println("PASS: fights left from the game state (table, Boss room, A20 act 3, act 4, final act, off the map)");

        JsonArray table = HpValue.export(game("{" + base + "\"act\":1,\"solver_auto_context\":{\"room\":\"MonsterRoomBoss\",\"y\":15}}"));
        check(table.size() == 60 && table.get(0).getAsDouble() == 0.0 && table.get(20).getAsDouble() == 5.0
            && table.get(59).getAsDouble() == 14.75, "export: one entry per living loss, 0..current HP - 1, Boss weight 1/4");
        check(HpValue.export(game("{\"max_hp\":80,\"act\":1}")) == null, "export: nothing without context");
        System.out.println("PASS: solver_hp_cost export has entries 0..current HP - 1 and is omitted without context");

        boundaries();
    }

    /** docs/hp-value.md, "边界": each boundary at its value and just past it. */
    static void boundaries() {
        HpValue.Context typical = PotionValue.typical(1); // act 2: 80 max HP, A20, Burning Blood, 4 fights left
        // Loss: nothing lost costs nothing; at or past the HP held is death, max HP + 20.
        check(HpValue.cost(0, 56, typical) == 0.0 && HpValue.cost(-3, 56, typical) == 0.0, "no loss costs 0");
        check(HpValue.cost(56, 56, typical) == 100.0 && HpValue.cost(70, 56, typical) == 100.0, "losing all HP is death: 100");
        example("one HP short of death, the dead-line rule", 55, 56, typical, 54.8125);
        // A line the assumed damage kills gets no victory heal: 10 -> 2 HP, then 6 damage. Before V1 it healed back (9.5).
        example("a line dead in a later fight stays dead", 8, 10, typical, 23.0);
        // Low HP: below 30% of max HP (24 of 80), not at it.
        example("ending exactly at 30% is not low HP", 8, 32, typical, 3.5);
        example("one HP under 30% counts 4 times", 9, 32, typical, 4.6875);
        // Healing now is capped at max HP; healing held for later (Blood Potion) is not capped by the HP missing now.
        check(HpValue.gain(30, 70, typical) == HpValue.cost(10, 80, typical) && HpValue.gain(30, 70, typical) == 4.375,
            "gain: 30 from 70/80 heals 10");
        check(HpValue.gain(5, 80, typical) == 0.0 && HpValue.gain(-5, 40, typical) == 0.0, "gain: nothing at max HP or for a negative heal");
        example("reserve: 16 held at 10 HP is worth losing 16 from 26", 16, 26, typical, HpValue.reserve(16, 10, typical));
        check(HpValue.reserve(16, 10, typical) == 17.5 && HpValue.reserve(16, 10, typical) < HpValue.death(typical),
            "reserve at low HP is finite, not death");
        System.out.println("PASS: boundaries: zero and negative loss, death (max HP + 20), dead lines stay dead, the 30% edge, gain capped, reserve");

        // Over a grid of situations: nothing alive costs more than death, and the cost never falls as the loss grows,
        // except with Meat on the Bone, whose heal below half HP can make a bigger loss cheaper (worked example E10).
        int cases = 0;
        int[][] futures = {{}, {13}, {4, 4, 4, 13}, {6, 6, 6, 6, 6, 9}, {6, 11, 11}, {69}};
        String[][] relicSets = {{}, {"Burning Blood"}, {"Black Blood", "Meat on the Bone", "Magic Flower"}, {"Mark of the Bloom"}};
        for (int maxHp : new int[]{40, 80, 200}) for (int[] future : futures) for (String[] r : relicSets)
            for (int ascension : new int[]{4, 20}) for (boolean transition : new boolean[]{true, false}) {
                HpValue.Context c = new HpValue.Context(maxHp, ascension, future, transition, relics(r));
                boolean meat = c.relics.contains("Meat on the Bone");
                for (int hp = 1; hp <= maxHp; hp++) {
                    double previous = 0;
                    for (int k = 0; k <= hp + 1; k++) {
                        double v = HpValue.cost(k, hp, c);
                        check((meat || v >= previous - 1e-9) && v <= HpValue.death(c) + 1e-9,
                            "monotone and capped: max " + maxHp + " hp " + hp + " k " + k + " = " + v);
                        previous = v; cases++;
                    }
                }
            }
        // The exception is real: from 56/80 in act 2, losing 3 HP is cheaper than losing 2 with Meat on the Bone.
        HpValue.Context meat = new HpValue.Context(80, 20, new int[]{6, 6, 6, 6, 6, 9}, true,
            relics("Black Blood", "Meat on the Bone", "Magic Flower"));
        boolean dip = false;
        for (int k = 1; k < 56; k++) dip |= HpValue.cost(k, 56, meat) < HpValue.cost(k - 1, 56, meat);
        check(dip, "Meat on the Bone makes some bigger losses cheaper");
        System.out.println("PASS: cost never exceeds death, and never falls as the loss grows except with Meat on the Bone (" + cases + " cases)");

        // Missing key inputs: unknown, so callers keep their old rules. Missing row and relics have defaults.
        check(HpValue.context(game("{\"max_hp\":80,\"current_hp\":60,\"act\":1,\"solver_auto_context\":{\"y\":3}}")) == null,
            "no ascension: unknown");
        check(HpValue.context(game("{\"current_hp\":60,\"ascension_level\":20,\"act\":1,\"solver_auto_context\":{\"y\":3}}")) == null,
            "no max HP: unknown");
        check(HpValue.context(game("{\"max_hp\":80,\"current_hp\":60,\"ascension_level\":20,\"solver_auto_context\":{\"y\":3}}")) == null,
            "no act: unknown");
        HpValue.Context bare = HpValue.context(game("{\"max_hp\":80,\"current_hp\":60,\"ascension_level\":20,\"act\":1,\"solver_auto_context\":{}}"));
        check(bare != null && bare.relics.isEmpty() && bare.future.length == 7, "no row and no relics: defaults (7 fights, none)");
        System.out.println("PASS: missing max HP, act or ascension is unknown; a missing row or relic list uses defaults");
    }
}
