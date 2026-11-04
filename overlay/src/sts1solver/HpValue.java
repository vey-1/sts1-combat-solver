package sts1solver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashSet;
import java.util.Set;

/**
 * What lost HP really costs at this point of the run: the part that the act-transition heal and
 * healing relics will not give back for free. The single owner of this model; definition, worked
 * examples and the rules that consume it are in docs/hp-value.md (issue #101).
 */
final class HpValue {
    /** Weight of danger in this act's remaining fights against HP carried past the act heal. */
    static final double LAMBDA = 0.25;
    /** The part of a gap below RISK_KNEE x max HP counts RISK_WEIGHT times. */
    static final double RISK_KNEE = 0.30;
    static final int RISK_WEIGHT = 4;
    /** Assumed damage per future fight by act: {hallway, boss}. Medians of the player's 144 A20 Ironclad runs. */
    static final int[][] DAMAGE = {{4, 13}, {6, 9}, {6, 11}, {6, 69}};
    /** Fights left in the act, Boss included, by floors to the Boss (index 1..15). Same 144 runs. */
    static final int[] FIGHTS_LEFT = {0, 1, 1, 2, 2, 3, 3, 4, 4, 4, 5, 5, 6, 6, 6, 7};
    static final int BOSS_ROW = 15;

    private HpValue() {}

    /** The future this fight leads into. Built from the game state by {@link #context}, or directly in checks. */
    static final class Context {
        final int maxHp, ascension;
        /** Assumed damage of each fight left in this act, in order; Boss fights last. */
        final int[] future;
        /** An act-transition heal follows this act. */
        final boolean transition;
        final Set<String> relics;

        Context(int maxHp, int ascension, int[] future, boolean transition, Set<String> relics) {
            this.maxHp = maxHp; this.ascension = ascension; this.future = future;
            this.transition = transition; this.relics = relics;
        }
    }

    /** Victory heals after one fight, in game order: Meat on the Bone, then Burning or Black Blood. */
    static int relicHeal(Set<String> relics, int hp, int maxHp) {
        if (relics.contains("Mark of the Bloom")) return 0;
        boolean flower = relics.contains("Magic Flower");
        int heal = 0;
        if (relics.contains("Meat on the Bone") && hp <= maxHp / 2.0) heal += flowered(12, flower);
        if (relics.contains("Black Blood")) heal += flowered(12, flower);
        else if (relics.contains("Burning Blood")) heal += flowered(6, flower);
        return heal;
    }

    private static int flowered(int amount, boolean flower) {
        return flower ? (int) Math.floor(amount * 1.5 + 0.5) : amount;
    }

    /** Share of a gap that survives the act-transition heal (75% of missing HP from A5, full below, none with Bloom). */
    static double transitionKeep(Context c) {
        if (c.relics.contains("Mark of the Bloom")) return 1.0;
        return c.ascension >= 5 ? 0.25 : 0.0;
    }

    /** Real cost of ending this fight k HP lower than hp; hp already includes this fight's victory heal. */
    static double cost(int k, int hp, Context c) {
        if (k <= 0) return 0.0;
        int a = Math.min(hp, c.maxHp), b = a - k;
        double low = RISK_KNEE * c.maxHp, gaps = 0;
        for (int damage : c.future) {
            double below = b < low ? Math.max(0, Math.min(a, low) - b) : 0;
            gaps += Math.max(0, a - b) + (RISK_WEIGHT - 1) * below;
            a -= damage; b -= damage;
            a = Math.min(c.maxHp, a + relicHeal(c.relics, a, c.maxHp));
            b = Math.min(c.maxHp, b + relicHeal(c.relics, b, c.maxHp));
        }
        Double after = c.transition ? transitionKeep(c) * Math.max(0, a - b) : null;
        if (c.future.length == 0) return after == null ? 0.0 : after;
        double inAct = gaps / c.future.length;
        // Not rounded: a saving exactly at a potion tier threshold must stay an exact tie with its price.
        return LAMBDA * inAct + (1 - LAMBDA) * (after == null ? inAct : after);
    }

    /** The future of the current fight, read from the exported game state; null when the state lacks it. */
    static Context context(JsonObject game) {
        if (!game.has("solver_auto_context") || !game.has("max_hp") || !game.has("act")) return null;
        JsonObject auto = game.getAsJsonObject("solver_auto_context");
        int act = Math.max(1, Math.min(4, game.get("act").getAsInt()));
        int ascension = game.has("ascension_level") ? game.get("ascension_level").getAsInt() : 0;
        boolean bossRoom = "MonsterRoomBoss".equals(string(auto, "room"));
        int bossesLeft = auto.has("boss_candidates_remaining") ? auto.get("boss_candidates_remaining").getAsInt() : 1;
        boolean finalAct = game.has("solver_final_act") && game.get("solver_final_act").getAsBoolean();
        int[] damage = DAMAGE[act - 1];
        int hallways, bosses;
        boolean transition;
        if (act == 4) {
            // Shield and Spear, then the Heart; the run ends there.
            hallways = 0; bosses = bossRoom ? 0 : 1; transition = false;
        } else {
            // A20 act 3 has two Bosses: during the first, the second is still ahead.
            int extraBoss = act == 3 && ascension >= 20 && bossesLeft > 1 ? 1 : 0;
            if (bossRoom) {
                hallways = 0; bosses = extraBoss;
            } else {
                int y = auto.has("y") ? auto.get("y").getAsInt() : -1;
                int floors = y < 0 ? FIGHTS_LEFT.length - 1 : Math.max(1, Math.min(FIGHTS_LEFT.length - 1, BOSS_ROW - y));
                int left = FIGHTS_LEFT[floors];
                hallways = left - 1; bosses = 1 + extraBoss;
            }
            transition = act < 3 || finalAct;
        }
        int[] future = new int[hallways + bosses];
        for (int i = 0; i < future.length; i++) future[i] = i < hallways ? damage[0] : damage[1];
        Set<String> relics = new HashSet<>();
        if (game.has("relics")) for (JsonElement item : game.getAsJsonArray("relics"))
            relics.add(string(item.getAsJsonObject(), "id"));
        return new Context(game.get("max_hp").getAsInt(), ascension, future, transition, relics);
    }

    /** solver_hp_cost: entry k is the real cost of ending this fight k HP below current HP; null when unknown. */
    static JsonArray export(JsonObject game) {
        Context c = context(game);
        if (c == null || !game.has("current_hp")) return null;
        int hp = game.get("current_hp").getAsInt();
        JsonArray out = new JsonArray();
        for (int k = 0; k <= Math.max(0, hp); k++) out.add(new JsonPrimitive(cost(k, hp, c)));
        return out;
    }

    private static String string(JsonObject value, String name) {
        return value.has(name) && !value.get(name).isJsonNull() ? value.get(name).getAsString() : "";
    }
}
