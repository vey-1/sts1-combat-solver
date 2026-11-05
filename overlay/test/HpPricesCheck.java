package sts1solver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;

/** docs/hp-value.md, chapter "药水的价值": the tier table, tier prices, stages and solver_potion_price (issue #101). */
public final class HpPricesCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    static JsonObject game(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    /** Spec tier names to thresholds in raw HP. */
    static final Map<String, Integer> TIER = new HashMap<>();
    static {
        TIER.put("随便喝", 0); TIER.put("低", 5); TIER.put("中", 10);
        TIER.put("较高", 16); TIER.put("高", 22); TIER.put("很高", 32);
    }

    /** The spec's 30-row table: game id, act 1, acts 2-3, act 4, act 1 pre-Boss, acts 2-3 pre-Boss. */
    static final String[][] SPEC = {
        {"Fire Potion", "较高", "中", "随便喝", "较高", "低"},
        {"Block Potion", "中", "中", "中", "中", "中"},
        {"Dexterity Potion", "低", "中", "较高", "中", "中"},
        {"Energy Potion", "较高", "中", "中", "中", "中"},
        {"Explosive Potion", "较高", "低", "随便喝", "低", "低"},
        {"FearPotion", "较高", "中", "中", "高", "较高"},
        {"Strength Potion", "中", "中", "中", "较高", "中"},
        {"Swift Potion", "较高", "高", "高", "较高", "高"},
        {"Weak Potion", "低", "低", "较高", "低", "中"},
        {"Ancient Potion", "中", "中", "高", "中", "中"},
        {"AttackPotion", "较高", "中", "随便喝", "较高", "中"},
        {"SkillPotion", "中", "中", "中", "中", "中"},
        {"PowerPotion", "较高", "高", "较高", "高", "高"},
        {"ColorlessPotion", "较高", "高", "较高", "较高", "高"},
        {"BlessingOfTheForge", "较高", "中", "低", "较高", "中"},
        {"HeartOfIron", "中", "低", "随便喝", "中", "低"},
        {"CultistPotion", "较高", "中", "低", "高", "高"},
        {"DistilledChaos", "高", "高", "高", "高", "高"},
        {"DuplicationPotion", "中", "较高", "较高", "较高", "较高"},
        {"ElixirPotion", "低", "中", "较高", "较高", "较高"},
        {"EntropicBrew", "中", "中", "中", "中", "中"},
        {"EssenceOfSteel", "低", "低", "随便喝", "低", "低"},
        {"GamblersBrew", "较高", "高", "很高", "较高", "高"},
        {"GhostInAJar", "很高", "很高", "很高", "很高", "很高"},
        {"LiquidBronze", "中", "低", "中", "高", "低"},
        {"LiquidMemories", "较高", "高", "较高", "较高", "高"},
        {"SneckoOil", "中", "中", "较高", "中", "中"},
        {"SpeedPotion", "低", "中", "较高", "低", "中"},
        {"SteroidPotion", "较高", "中", "中", "较高", "中"},
        {"Regen Potion", "低", "低", "低", "低", "低"},
    };

    /** Price of one potion exported at an act, room and map row. */
    static double price(int act, String room, int y, String id) {
        JsonArray out = HpPrices.export(game("{\"act\":" + act + ",\"solver_auto_context\":{\"room\":\"" + room
            + "\",\"y\":" + y + "}," + potions(id) + "}"));
        return out.get(0).getAsDouble();
    }

    static String potions(String... ids) {
        StringBuilder b = new StringBuilder("\"potions\":[");
        for (int i = 0; i < ids.length; i++) b.append(i == 0 ? "" : ",").append("{\"id\":\"").append(ids[i]).append("\"}");
        return b.append("]").toString();
    }

    public static void main(String[] args) {
        check(SPEC.length == 30 && HpPrices.TIERS.size() == 30, "table: 30 rows, got " + HpPrices.TIERS.size());
        for (String[] row : SPEC) {
            int[] t = HpPrices.TIERS.get(row[0]);
            check(t != null, "table: missing " + row[0]);
            for (int s = 0; s < 5; s++)
                check(t[s] == TIER.get(row[s + 1]), "table: " + row[0] + " column " + s + " expected " + row[s + 1] + ", got " + t[s]);
        }
        System.out.println("PASS: the tier table has exactly the 30 game ids of the spec, each with the spec's five tiers (3 stages + 2 pre-Boss)");

        int[] thresholds = {0, 5, 10, 16, 22, 32};
        double[] early = {0, 2.1875, 4.375, 7, 9.625, 14};
        for (int i = 0; i < thresholds.length; i++) {
            for (int s = 0; s < 2; s++) {
                double got = HpPrices.tierPrice(thresholds[i], s);
                check(near(got, early[i]), "tier price " + thresholds[i] + " stage " + s + ": expected " + early[i] + ", got " + got);
            }
            double got = HpPrices.tierPrice(thresholds[i], 2);
            check(near(got, thresholds[i]), "tier price " + thresholds[i] + " stage 2: expected " + thresholds[i] + ", got " + got);
        }
        System.out.println("PASS: tier prices at the typical situation (stages 0-1: 0/2.188/4.375/7/9.625/14; stage 2: 0/5/10/16/22/32)");

        // A saving exactly at the threshold must stay an exact tie (the backend drinks on a tie). Logged fight
        // src1/6231061488872913214@f6: act 1 elite, 68/68, Burning Blood, five fights left; Speed Potion (低, 5)
        // saved 24 -> 19. Rounding each cost to 3 decimals turned 2.1875 = 2.1875 into 2.187 < 2.188.
        HpValue.Context speed = new HpValue.Context(68, 20, new int[]{4, 4, 4, 4, 13}, true,
            new java.util.HashSet<>(java.util.Arrays.asList("Burning Blood")));
        double saved = HpValue.cost(24, 68, speed) - HpValue.cost(19, 68, speed);
        check(saved == HpPrices.tierPrice(HpPrices.LOW, 0),
            "threshold tie: cost saved " + saved + " must equal the 低 price " + HpPrices.tierPrice(HpPrices.LOW, 0));
        System.out.println("PASS: a saving exactly at the tier threshold is an exact tie with the price (no rounding)");

        double[] fire = {7, 4.375, 4.375, 0};
        for (int act = 1; act <= 4; act++) {
            JsonArray out = HpPrices.export(game("{\"act\":" + act + "," + potions("Fire Potion") + "}"));
            check(out != null && out.size() == 1 && near(out.get(0).getAsDouble(), fire[act - 1]),
                "Fire Potion act " + act + ": expected " + fire[act - 1] + ", got " + out);
            JsonArray unknown = HpPrices.export(game("{\"act\":" + act + "," + potions("FocusPotion") + "}"));
            double mid = HpPrices.tierPrice(10, act == 4 ? 2 : act == 1 ? 0 : 1);
            check(near(unknown.get(0).getAsDouble(), mid) && near(mid, act == 4 ? 10 : 4.375),
                "unknown potion act " + act + " prices as 中: expected " + mid + ", got " + unknown);
        }
        System.out.println("PASS: stages from the act (1 -> act 1, 2-3 -> acts 2-3, 4 -> act 4) via Fire Potion; unknown id prices as 中");

        // Pre-Boss: within 3 floors of an act 1-3 Boss (map rows 12-14) the pre-Boss column replaces the stage column.
        check(near(price(1, "MonsterRoom", 11, "FearPotion"), 7), "Fear act 1 row 11: stage tier 较高 (7)");
        check(near(price(1, "MonsterRoom", 12, "FearPotion"), 9.625), "Fear act 1 row 12: pre-Boss tier 高 (9.625)");
        check(near(price(1, "MonsterRoomElite", 14, "FearPotion"), 9.625), "Fear act 1 row 14: pre-Boss tier 高 (9.625)");
        // A lower pre-Boss tier applies too (taking the higher of the two would give 7 here).
        check(near(price(1, "MonsterRoom", 12, "Explosive Potion"), 2.1875), "Explosive act 1 row 12: pre-Boss tier 低 (2.1875), not 较高");
        check(near(price(1, "MonsterRoomBoss", 15, "FearPotion"), 7), "Fear in the act 1 Boss room keeps the stage tier");
        check(near(price(3, "MonsterRoom", 13, "FearPotion"), 7), "Fear act 3 row 13: acts 2-3 pre-Boss tier 较高 (7)");
        check(near(price(3, "MonsterRoom", 11, "FearPotion"), 4.375), "Fear act 3 row 11: stage tier 中 (4.375)");
        check(near(price(4, "MonsterRoomElite", 2, "FearPotion"), 10), "act 4 has no pre-Boss: Fear 中 (10)");
        check(near(price(1, "MonsterRoom", 12, "FocusPotion"), 4.375), "unknown potion before the Boss prices as 中");
        JsonArray noRow = HpPrices.export(game("{\"act\":1,\"solver_auto_context\":{\"room\":\"MonsterRoom\"},"
            + potions("FearPotion") + "}"));
        check(near(noRow.get(0).getAsDouble(), 7), "without a map row the stage tier applies: " + noRow);
        System.out.println("PASS: pre-Boss tiers replace the stage tier on rows 12-14 of acts 1-3 (higher or lower), "
            + "not in the Boss room, not in act 4, not without a map row");

        JsonArray slots = HpPrices.export(game("{\"act\":2," + potions("Potion Slot", "FairyPotion", "Swift Potion",
            "Fruit Juice", "SmokeBomb", "Weak Potion") + "}"));
        check(slots.size() == 6, "export: one entry per slot, got " + slots.size());
        check(slots.get(0).isJsonNull() && slots.get(1).isJsonNull() && slots.get(3).isJsonNull() && slots.get(4).isJsonNull(),
            "export: empty slot, FairyPotion, Fruit Juice, SmokeBomb are null: " + slots);
        check(near(slots.get(2).getAsDouble(), 9.625) && near(slots.get(5).getAsDouble(), 2.1875),
            "export: slot order kept (Swift 高 9.625 at 2, Weak 低 2.1875 at 5): " + slots);
        System.out.println("PASS: empty slot and separately handled potions export null; length and slot order kept");

        String boss = "\"act\":1,\"max_hp\":80,\"current_hp\":60,\"ascension_level\":20,"
            + "\"solver_auto_context\":{\"room\":\"MonsterRoomBoss\",\"y\":15},";
        JsonObject g = game("{" + boss + "\"relics\":[{\"id\":\"Burning Blood\"}]," + potions("BloodPotion") + "}");
        double expected = HpValue.cost(80 * 20 / 100, 60, HpValue.context(g));
        double blood = HpPrices.export(g).get(0).getAsDouble();
        check(near(blood, expected) && near(blood, 4.0), "Blood Potion, act 1 Boss: heal 16 costs 4.0, got " + blood);
        JsonObject bark = game("{" + boss + "\"relics\":[{\"id\":\"Burning Blood\"},{\"id\":\"SacredBark\"}]," + potions("BloodPotion") + "}");
        double barked = HpPrices.export(bark).get(0).getAsDouble();
        check(near(barked, HpValue.cost(32, 60, HpValue.context(bark))) && near(barked, 8.0),
            "Blood Potion with SacredBark: heal 32 costs 8.0, got " + barked);
        JsonArray noContext = HpPrices.export(game("{\"act\":1,\"max_hp\":80,\"current_hp\":60," + potions("BloodPotion", "Fire Potion") + "}"));
        check(noContext.get(0).isJsonNull() && near(noContext.get(1).getAsDouble(), 7),
            "Blood Potion without solver_auto_context is null, others still priced: " + noContext);
        System.out.println("PASS: Blood Potion is the real cost of its heal (16 -> 4.0, SacredBark 32 -> 8.0), null without auto context");

        check(HpPrices.export(game("{" + potions("Fire Potion") + "}")) == null, "export: null without act");
        check(HpPrices.export(game("{\"act\":1}")) == null, "export: null without potions");
        System.out.println("PASS: solver_potion_price is omitted without act or potions");

        smokeAllowance();
    }

    static JsonObject smokeGame(int act, int max, int hp, String room, String relics, String... held) {
        return game("{\"act\":" + act + ",\"max_hp\":" + max + ",\"current_hp\":" + hp + ",\"ascension_level\":20,"
            + "\"solver_auto_context\":{\"room\":\"" + room + "\",\"y\":5},\"relics\":[" + relics + "]," + potions(held) + "}");
    }

    /** Smallest loss whose real cost exceeds the escape cost: from here the probe's win no longer keeps the bomb. */
    static int escapeAt(JsonObject g) {
        JsonArray cost = HpValue.export(g);
        double allowance = HpPrices.smokeAllowance(g);
        int k = 1;
        while (k < cost.size() && cost.get(k).getAsDouble() <= allowance) k++;
        return k;
    }

    /** docs/hp-value.md, 烟雾弹: the escape costs the bomb's tier (中) plus the forfeited reward (hallway 中, elite 很高). */
    static void smokeAllowance() {
        String bb = "{\"id\":\"Burning Blood\"}";
        check(near(HpPrices.smokeAllowance(smokeGame(1, 80, 56, "MonsterRoom", bb, "SmokeBomb")), 8.75), "hallway: 中 + 中 = 8.75");
        check(near(HpPrices.smokeAllowance(smokeGame(1, 80, 56, "EventRoom", bb, "SmokeBomb")), 8.75), "event fight prices as a hallway");
        check(near(HpPrices.smokeAllowance(smokeGame(2, 80, 56, "MonsterRoomElite", bb, "Potion Slot", "SmokeBomb")), 18.375),
            "elite: 中 + 很高 = 18.375, any slot");
        check(near(HpPrices.smokeAllowance(smokeGame(4, 80, 56, "MonsterRoomElite", bb, "SmokeBomb")), 42), "act 4 elite: 10 + 32");
        check(HpPrices.smokeAllowance(smokeGame(1, 80, 56, "MonsterRoomBoss", bb, "SmokeBomb")) == null, "Boss room: null");
        check(HpPrices.smokeAllowance(smokeGame(1, 80, 56, "MonsterRoom", bb, "GamblersBrew")) == null, "no Smoke Bomb: null");
        check(HpPrices.smokeAllowance(game("{\"act\":1," + potions("SmokeBomb") + "}")) == null, "no auto context: null");

        // S1, the F6 Gremlin Nob of 2026-11-04: 80/82, Burning Blood, act 1 elite. Old rule escaped at 15.
        JsonObject nob = smokeGame(1, 82, 80, "MonsterRoomElite", bb, "SmokeBomb", "GamblersBrew");
        check(HpValue.export(nob).get(20).getAsDouble() <= HpPrices.smokeAllowance(nob), "S1: a 20-HP win at 80/82 keeps the bomb");
        check(escapeAt(nob) == 46, "S1: escape only past 45 HP, got " + escapeAt(nob));
        // S3: 20/80, act 2 hallway, no healing relic: 15 HP is dearer than the escape.
        JsonObject low = smokeGame(2, 80, 20, "MonsterRoom", "", "SmokeBomb");
        check(HpValue.export(low).get(15).getAsDouble() > HpPrices.smokeAllowance(low), "S3: 15 HP at 20/80 escapes");
        check(escapeAt(low) == 8, "S3: escape from 8 HP, got " + escapeAt(low));
        // S4: same HP and loss (56/80, 25 HP costs 10.938): the elite keeps, the hallway escapes.
        check(escapeAt(smokeGame(1, 80, 56, "MonsterRoomElite", bb, "SmokeBomb")) > 25
            && escapeAt(smokeGame(1, 80, 56, "MonsterRoom", bb, "SmokeBomb")) <= 25, "S4: the forfeited elite reward is counted");
        System.out.println("PASS: solver_smoke_allowance = Smoke Bomb 中 + reward (hallway/event 中, elite 很高); null in a Boss room "
            + "or without a bomb; F6 Nob at 80/82 escapes only past 45 HP, 20/80 from 8");

        rewards();
    }

    /** docs/hp-value.md, "奖励值多少血": event rewards in raw HP at the typical situation, summed and priced once. */
    static void rewards() {
        int[][] raw = {{HpPrices.RELIC, 32}, {HpPrices.BOOK, 22}, {HpPrices.COLORLESS_CARD, 10},
            {HpPrices.WORTHY_UPGRADE, 16}, {HpPrices.OTHER_UPGRADE, 5}, {HpPrices.UNREAD_LIGHT, 22}, {HpPrices.GOLD_PER_HP, 10}};
        for (int[] r : raw) check(r[0] == r[1], "reward table: expected " + r[1] + ", got " + r[0]);
        for (int act = 1; act <= 4; act++) {
            int s = HpPrices.stage(act);
            check(HpPrices.reward().relics(1).price(act) == HpPrices.tierPrice(32, s), "relic = 32 HP, act " + act);
            check(HpPrices.reward().books(1).price(act) == HpPrices.tierPrice(22, s), "book = 22 HP, act " + act);
            check(HpPrices.reward().colorlessCards(2).price(act) == HpPrices.tierPrice(20, s), "two colorless cards = 20 HP");
            check(HpPrices.reward().upgrades(1, 1).price(act) == HpPrices.tierPrice(21, s), "a worthy and another upgrade = 21 HP");
            check(HpPrices.reward().unreadLight().price(act) == HpPrices.tierPrice(22, s), "unread light = 22 HP");
            check(HpPrices.reward().gold(115).price(act) == HpPrices.tierPrice(11, s)
                && HpPrices.reward().gold(119).price(act) == HpPrices.tierPrice(11, s), "gold: 10 per HP, rounded down");
            check(HpPrices.reward().price(act) == 0.0, "nothing is worth 0");
        }
        check(near(HpPrices.reward().relics(1).price(2), 14) && near(HpPrices.reward().gold(90).price(2), 3.9375),
            "act 2: a relic 14, 90 gold 3.9375");
        System.out.println("PASS: event rewards (relic 32, book 22, colorless card 10, upgrades 16/5, unread light 22, 10 gold per HP)"
            + " add up in raw HP and are priced once");
    }
}
