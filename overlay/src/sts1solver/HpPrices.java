package sts1solver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The combat HP prices, read from hp-prices.json in the mod jar. The build writes that file from the constants at the
 * top of overlay/auto_policy.py (tools/export_hp_prices.py), so a price is set in one place and both sides read it.
 * A missing or unreadable file is an error: no price has a default here.
 */
final class HpPrices {
    static final String FILE = "hp-prices.json";

    /** The situation a bottle is priced in; the order of a potion_hp row. */
    enum Slot { REFILL, RESERVE, KEEP }

    private static HpPrices book;

    private final int smokeLoss, revival;
    private final double bossHealWeight;
    private final Map<String, int[]> potionHp = new HashMap<>();
    private final Set<String> premium = new HashSet<>(), strong = new HashSet<>();
    private final Map<String, Set<Integer>> strongInAct = new HashMap<>();

    private HpPrices(JsonObject json) {
        if (json.get("schema").getAsInt() != 1) throw new IllegalStateException("schema is not 1");
        smokeLoss = json.get("smoke_loss_hp").getAsInt();
        revival = json.get("revival_hp").getAsInt();
        bossHealWeight = json.get("boss_heal_weight").getAsDouble();
        for (String tier : new String[]{"premium", "strong", "ordinary"}) {
            JsonArray row = json.getAsJsonObject("potion_hp").getAsJsonArray(tier);
            if (row.size() != Slot.values().length) throw new IllegalStateException("potion_hp." + tier + " needs three prices");
            int[] prices = new int[row.size()];
            for (int i = 0; i < prices.length; i++) prices[i] = row.get(i).getAsInt();
            potionHp.put(tier, prices);
        }
        for (JsonElement id : json.getAsJsonArray("premium")) premium.add(id.getAsString());
        for (JsonElement id : json.getAsJsonArray("strong")) strong.add(id.getAsString());
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("strong_in_act").entrySet()) {
            Set<Integer> acts = new HashSet<>();
            for (JsonElement act : entry.getValue().getAsJsonArray()) acts.add(act.getAsInt());
            strongInAct.put(entry.getKey(), acts);
        }
    }

    static HpPrices parse(Reader reader) {
        try {
            return new HpPrices(new JsonParser().parse(reader).getAsJsonObject());
        } catch (RuntimeException error) {
            throw new IllegalStateException("price file " + FILE + " is not readable: " + error.getMessage(), error);
        }
    }

    static HpPrices load(ClassLoader loader) {
        try (InputStream in = loader.getResourceAsStream(FILE)) {
            if (in == null) throw new IllegalStateException("price file " + FILE + " is missing from the mod jar");
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("price file " + FILE + " is not readable: " + error.getMessage(), error);
        }
    }

    private static synchronized HpPrices book() {
        if (book == null) book = load(HpPrices.class.getClassLoader());
        return book;
    }

    String tierOf(String potion, int act) {
        if (premium.contains(potion)) return "premium";
        Set<Integer> acts = strongInAct.get(potion);
        return strong.contains(potion) || (acts != null && acts.contains(act)) ? "strong" : "ordinary";
    }

    int keepOf(String potion, int act, Slot slot) { return potionHp.get(tierOf(potion, act))[slot.ordinal()]; }
    int smokeLossOf() { return smokeLoss; }
    int revivalOf(int maxHp) { return maxHp + revival; }
    double bossHealWeightOf(int ascension) { return ascension >= 5 ? bossHealWeight : 0; }

    /** premium, strong or ordinary: the potion_hp row that prices this bottle in this act. */
    static String tier(String potion, int act) { return book().tierOf(potion, act); }
    /** What keeping this bottle is worth in HP, in this act and situation. */
    static int keep(String potion, int act, Slot slot) { return book().keepOf(potion, act, slot); }
    /** Raw loss a first-turn Smoke probe win may cost before escaping is preferred. */
    static int smokeLoss() { return book().smokeLossOf(); }
    /** The price of a spent Lizard Tail or Fairy. */
    static int revival(int maxHp) { return book().revivalOf(maxHp); }
    /** Share of a healing Boss fight's HP loss still missing when the next act starts. */
    static double bossHealWeight(int ascension) { return book().bossHealWeightOf(ascension); }
}
