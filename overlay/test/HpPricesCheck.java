package sts1solver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** HpPrices gives the prices auto_policy.py gives: args[0] is the case table tools/export_hp_prices.py --cases wrote. */
public class HpPricesCheck {
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** How many cases the book answers differently from the table. */
    static int wrong(HpPrices book, JsonObject cases) {
        int wrong = 0;
        for (JsonElement item : cases.getAsJsonArray("bottles")) {
            JsonObject c = item.getAsJsonObject();
            String potion = c.get("potion").getAsString();
            int act = c.get("act").getAsInt();
            if (!book.tierOf(potion, act).equals(c.get("tier").getAsString())) wrong++;
            if (book.keepOf(potion, act, HpPrices.Slot.valueOf(c.get("slot").getAsString())) != c.get("hp").getAsInt()) wrong++;
        }
        if (book.smokeLossOf() != cases.get("smoke_loss").getAsInt()) wrong++;
        for (JsonElement item : cases.getAsJsonArray("revival"))
            if (book.revivalOf(item.getAsJsonObject().get("max_hp").getAsInt()) != item.getAsJsonObject().get("hp").getAsInt()) wrong++;
        for (JsonElement item : cases.getAsJsonArray("boss_heal_weight"))
            if (book.bossHealWeightOf(item.getAsJsonObject().get("ascension").getAsInt()) != item.getAsJsonObject().get("weight").getAsDouble()) wrong++;
        return wrong;
    }

    public static void main(String[] args) throws Exception {
        JsonObject cases = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray bottles = cases.getAsJsonArray("bottles");
        require(bottles.size() >= 200, "case table is too small: " + bottles.size());
        boolean unlisted = false;
        for (JsonElement item : bottles) unlisted |= item.getAsJsonObject().get("potion").getAsString().equals("NotAPotion");
        require(unlisted, "case table has no unlisted bottle");

        String text;
        try (InputStream in = HpPrices.class.getClassLoader().getResourceAsStream(HpPrices.FILE)) {
            require(in != null, HpPrices.FILE + " is not on the class path");
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) > 0;) bytes.write(buffer, 0, n);
            text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        require(wrong(HpPrices.parse(new StringReader(text)), cases) == 0, "Java prices differ from auto_policy.py");
        // the static entry reads the same file
        require(HpPrices.keep("NotAPotion", 1, HpPrices.Slot.KEEP) == HpPrices.parse(new StringReader(text)).keepOf("NotAPotion", 1, HpPrices.Slot.KEEP),
                "static entry differs from the parsed file");
        require(HpPrices.tier("NotAPotion", 3).equals("ordinary"), "an unlisted bottle is ordinary");

        // one changed number must be noticed, whichever number it is
        JsonObject changed = new JsonParser().parse(text).getAsJsonObject();
        JsonArray row = changed.getAsJsonObject("potion_hp").getAsJsonArray("strong");
        row.set(2, new com.google.gson.JsonPrimitive(row.get(2).getAsInt() + 1));
        require(wrong(HpPrices.parse(new StringReader(changed.toString())), cases) > 0, "a changed bottle price passed");
        changed = new JsonParser().parse(text).getAsJsonObject();
        changed.addProperty("revival_hp", changed.get("revival_hp").getAsInt() + 1);
        require(wrong(HpPrices.parse(new StringReader(changed.toString())), cases) > 0, "a changed revival price passed");
        changed = new JsonParser().parse(text).getAsJsonObject();
        changed.add("strong_in_act", new JsonObject());
        require(wrong(HpPrices.parse(new StringReader(changed.toString())), cases) > 0, "a dropped act-bound tier passed");

        // no file, an unknown schema and a missing price are errors that name the file
        for (String broken : new String[]{null, text.replaceFirst("\"schema\": 1", "\"schema\": 2"), text.replaceFirst("\"revival_hp\"", "\"revival\"")}) {
            try {
                if (broken == null) HpPrices.load(new URLClassLoader(new URL[0], null));
                else HpPrices.parse(new StringReader(broken));
                throw new AssertionError("a broken price file was accepted");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains(HpPrices.FILE), "the error does not name the file: " + expected.getMessage());
            }
        }
        System.out.println("PASS: HpPrices equals auto_policy.py on " + bottles.size() + " bottle cases; changed, missing and unreadable files are rejected");
    }
}
