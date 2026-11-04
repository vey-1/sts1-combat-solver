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
        check(table.size() == 61 && table.get(0).getAsDouble() == 0.0 && table.get(20).getAsDouble() == 5.0
            && table.get(60).getAsDouble() == 15.0, "export: one entry per HP from 0 to current, Boss weight 1/4");
        check(HpValue.export(game("{\"max_hp\":80,\"act\":1}")) == null, "export: nothing without context");
        System.out.println("PASS: solver_hp_cost export has entries 0..current HP and is omitted without context");
    }
}
