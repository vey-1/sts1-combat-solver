package sts1solver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The price book of issue #101: what held potions, the Smoke Bomb escape and event rewards are worth, in HpValue's
 * real-HP units. Every price is a raw-HP amount at a typical situation, converted through HpValue; consumers compare
 * a cost from HpValue with a price from here and hold no prices of their own (HpOwnerCheck). Tiers and rules:
 * docs/hp-value.md, chapters "药水的价值" and "事件的血价".
 */
final class HpPrices {
    /** Tier thresholds in raw HP saved: free, low, mid, higher, high, top. */
    static final int FREE = 0, LOW = 5, MID = 10, HIGHER = 16, HIGH = 22, TOP = 32;
    /** Potions outside the table (other characters, other mods). */
    static final int UNKNOWN = MID;
    /** Not priced here: revival, out-of-combat drink and escape keep their own rules. */
    static final Set<String> SEPARATE = new HashSet<>(Arrays.asList("FairyPotion", "Fruit Juice", "SmokeBomb"));
    /** Thresholds by column {act 1, acts 2-3, act 4, act 1 pre-Boss, acts 2-3 pre-Boss}; the player's table of 2026-11-02. */
    static final Map<String, int[]> TIERS = new HashMap<>();

    static {
        put("Fire Potion", HIGHER, MID, FREE, HIGHER, LOW);
        put("Block Potion", MID, MID, MID, MID, MID);
        put("Dexterity Potion", LOW, MID, HIGHER, MID, MID);
        put("Energy Potion", HIGHER, MID, MID, MID, MID);
        put("Explosive Potion", HIGHER, LOW, FREE, LOW, LOW);
        put("FearPotion", HIGHER, MID, MID, HIGH, HIGHER);
        put("Strength Potion", MID, MID, MID, HIGHER, MID);
        put("Swift Potion", HIGHER, HIGH, HIGH, HIGHER, HIGH);
        put("Weak Potion", LOW, LOW, HIGHER, LOW, MID);
        put("Ancient Potion", MID, MID, HIGH, MID, MID);
        put("AttackPotion", HIGHER, MID, FREE, HIGHER, MID);
        put("SkillPotion", MID, MID, MID, MID, MID);
        put("PowerPotion", HIGHER, HIGH, HIGHER, HIGH, HIGH);
        put("ColorlessPotion", HIGHER, HIGH, HIGHER, HIGHER, HIGH);
        put("BlessingOfTheForge", HIGHER, MID, LOW, HIGHER, MID);
        put("HeartOfIron", MID, LOW, FREE, MID, LOW);
        put("CultistPotion", HIGHER, MID, LOW, HIGH, HIGH);
        put("DistilledChaos", HIGH, HIGH, HIGH, HIGH, HIGH);
        put("DuplicationPotion", MID, HIGHER, HIGHER, HIGHER, HIGHER);
        put("ElixirPotion", LOW, MID, HIGHER, HIGHER, HIGHER);
        put("EntropicBrew", MID, MID, MID, MID, MID);
        put("EssenceOfSteel", LOW, LOW, FREE, LOW, LOW);
        put("GamblersBrew", HIGHER, HIGH, TOP, HIGHER, HIGH);
        put("GhostInAJar", TOP, TOP, TOP, TOP, TOP);
        put("LiquidBronze", MID, LOW, MID, HIGH, LOW);
        put("LiquidMemories", HIGHER, HIGH, HIGHER, HIGHER, HIGH);
        put("SneckoOil", MID, MID, HIGHER, MID, MID);
        put("SpeedPotion", LOW, MID, HIGHER, LOW, MID);
        put("SteroidPotion", HIGHER, MID, MID, HIGHER, MID);
        put("Regen Potion", LOW, LOW, LOW, LOW, LOW);
    }

    private static void put(String id, int act1, int acts23, int act4, int preBoss1, int preBoss23) {
        TIERS.put(id, new int[]{act1, acts23, act4, preBoss1, preBoss23});
    }

    private HpPrices() {}

    /** Stage index: 0 act 1, 1 acts 2-3, 2 act 4. */
    static int stage(int act) {
        return act <= 1 ? 0 : act <= 3 ? 1 : 2;
    }

    /**
     * The typical situation a tier is anchored to: max HP 80, at 56, Burning Blood, A20, four fights left
     * (three hallways and the Boss) with act 1's damage for stage 0 and act 2's for stage 1; in act 4 only the
     * Heart is left and no act heal follows.
     */
    static HpValue.Context typical(int stage) {
        Set<String> relics = new HashSet<>(Arrays.asList("Burning Blood"));
        if (stage == 2) return new HpValue.Context(80, 20, new int[]{HpValue.DAMAGE[3][1]}, false, relics);
        int[] d = HpValue.DAMAGE[stage];
        return new HpValue.Context(80, 20, new int[]{d[0], d[0], d[0], d[1]}, true, relics);
    }

    static final int TYPICAL_HP = 56;

    /** Price of a raw-HP threshold at a stage: what HpValue says losing that much costs in the typical situation. */
    static double tierPrice(int threshold, int stage) {
        return HpValue.cost(threshold, TYPICAL_HP, typical(stage));
    }

    /** Floors before the act 1-3 Boss that use the pre-Boss tiers: map rows 12-14 (the Boss is row 15). */
    static final int PRE_BOSS_FLOORS = 3;

    /**
     * Table column for the current place: the stage (0, 1, 2), or the pre-Boss column (3 for act 1, 4 for acts 2-3)
     * within PRE_BOSS_FLOORS of an act 1-3 Boss. The Boss fight itself and act 4 keep their stage column.
     */
    static int column(JsonObject game) {
        int stage = stage(game.get("act").getAsInt());
        if (stage == 2 || !game.has("solver_auto_context")) return stage;
        JsonObject auto = game.getAsJsonObject("solver_auto_context");
        if (!auto.has("y") || "MonsterRoomBoss".equals(auto.has("room") ? auto.get("room").getAsString() : "")) return stage;
        int y = auto.get("y").getAsInt();
        return y >= HpValue.BOSS_ROW - PRE_BOSS_FLOORS && y < HpValue.BOSS_ROW ? 3 + stage : stage;
    }

    /** The pricing stage of a column: pre-Boss columns are priced in their own stage's typical situation. */
    static int priceStage(int column) {
        return column == 3 ? 0 : column == 4 ? 1 : column;
    }

    static int threshold(String id, int column) {
        int[] t = TIERS.get(id);
        return t == null ? UNKNOWN : t[column];
    }

    /** Blood Potion: the value of the HP it heals, held for later (HpValue.reserve); its heal is already in the drinking line's loss. */
    static Double bloodPrice(JsonObject game) {
        HpValue.Context c = HpValue.context(game);
        if (c == null || !game.has("current_hp")) return null;
        int heal = c.maxHp * (c.relics.contains("SacredBark") ? 40 : 20) / 100;
        return HpValue.reserve(heal, game.get("current_hp").getAsInt(), c);
    }

    /** Smoke Bomb escape: the bomb's own tier, plus the rewards an escape gives up (an elite's relic; a hallway or event fight's card and gold). */
    static final int SMOKE = MID, HALLWAY_REWARD = MID, ELITE_REWARD = TOP;

    /**
     * solver_smoke_allowance: what escaping with a Smoke Bomb costs, in real HP. The backend escapes only when the
     * real cost of the best potion-free win is higher. Null without a Smoke Bomb, in a Boss room (it cannot be used)
     * or without the room.
     */
    static Double smokeAllowance(JsonObject game) {
        if (!game.has("act") || !game.has("potions") || !game.has("solver_auto_context")) return null;
        boolean held = false;
        for (JsonElement item : game.getAsJsonArray("potions"))
            held |= item.getAsJsonObject().has("id") && "SmokeBomb".equals(item.getAsJsonObject().get("id").getAsString());
        JsonObject auto = game.getAsJsonObject("solver_auto_context");
        String room = auto.has("room") && !auto.get("room").isJsonNull() ? auto.get("room").getAsString() : "";
        if (!held || room.isEmpty() || "MonsterRoomBoss".equals(room)) return null;
        int stage = stage(game.get("act").getAsInt());
        int reward = "MonsterRoomElite".equals(room) ? ELITE_REWARD : HALLWAY_REWARD;
        return tierPrice(SMOKE, stage) + tierPrice(reward, stage);
    }

    /**
     * Event rewards (docs/hp-value.md, "事件的血价"): raw HP at the typical situation, fitted to the player's A20
     * Ironclad event choices. Shining Light: per target the upgrade grid ranks highly, per other target, and both
     * targets when not pre-read.
     */
    static final int GOLD_PER_HP = 10;
    static final int RELIC = TOP, BOOK = HIGH, COLORLESS_CARD = MID;
    static final int WORTHY_UPGRADE = HIGHER, OTHER_UPGRADE = LOW, UNREAD_LIGHT = HIGH;

    /** What one event choice brings. The raw amounts add up first and are priced once, as one reward. */
    static final class Reward {
        private int raw;

        Reward relics(int n) { raw += RELIC * n; return this; }
        Reward books(int n) { raw += BOOK * n; return this; }
        Reward colorlessCards(int n) { raw += COLORLESS_CARD * n; return this; }
        /** Shining Light, pre-read: upgrades the grid ranks highly, and the others. */
        Reward upgrades(int worthy, int other) { raw += WORTHY_UPGRADE * worthy + OTHER_UPGRADE * other; return this; }
        /** Shining Light without a pre-read: two upgrades of unknown worth. */
        Reward unreadLight() { raw += UNREAD_LIGHT; return this; }
        /** Gold, whole HP only (10 gold per HP, rounded down). */
        Reward gold(int amount) { raw += amount / GOLD_PER_HP; return this; }

        /** Real-HP price in this act. */
        double price(int act) { return tierPrice(raw, stage(act)); }
    }

    static Reward reward() { return new Reward(); }

    /** solver_potion_price: one entry per potion slot; null for an empty slot or a separately handled potion. */
    static JsonArray export(JsonObject game) {
        if (!game.has("act") || !game.has("potions")) return null;
        int column = column(game), stage = priceStage(column);
        JsonArray out = new JsonArray();
        for (JsonElement item : game.getAsJsonArray("potions")) {
            String id = item.getAsJsonObject().has("id") ? item.getAsJsonObject().get("id").getAsString() : "";
            Double price;
            if (id.isEmpty() || id.equals("Potion Slot") || SEPARATE.contains(id)) price = null;
            else if (id.equals("BloodPotion")) price = bloodPrice(game);
            else price = tierPrice(threshold(id, column), stage);
            out.add(price == null ? JsonNull.INSTANCE : new JsonPrimitive(price));
        }
        return out;
    }
}
