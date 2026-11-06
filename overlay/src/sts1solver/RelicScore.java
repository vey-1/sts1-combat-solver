package sts1solver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/** Hand-written relic values (0-10) for boss picks and the Sapphire Key trade. Unlisted relics are neutral.
 * Thresholds are the author's draft for an Ironclad autopilot: tune here, nowhere else. */
final class RelicScore {
    static final int NEUTRAL = 5;
    /** The Sapphire Key is worth more than any relic scored below this; it costs the linked relic. */
    static final int SAPPHIRE_KEY = 6;
    /** Below this a boss relic is worse than none. */
    static final int SKIP_BELOW = 2;

    private static final Map<String, Integer> SCORES = new HashMap<>();

    private static void put(int score, String... ids) {
        for (String id : ids) SCORES.put(id, score);
    }

    static {
        // Boss relics: the author's tiers (pick within a tier by rank(), then by the network). Runic Dome only hides the
        // intent icons, the data is still exported, so the solver sees everything. The deck-dependent ones (Fusion Hammer,
        // Pandora's Box, Astrolabe, Mark of Pain, Runic Cube, Empty Cage) hold a default here; bossScore() is what counts.
        put(10, "Runic Pyramid");
        put(9, "Runic Dome");
        put(8, "Coffee Dripper", "Cursed Key", "Empty Cage");
        put(7, "Snecko Eye", "Philosopher's Stone", "Pandora's Box", "Astrolabe");
        put(6, "SlaversCollar", "Calling Bell");
        put(5, "Black Star", "SacredBark");
        put(4, "Tiny House", "Black Blood", "Fusion Hammer", "Mark of Pain");
        put(3, "Sozu", "Runic Cube");
        put(1, "Velvet Choker", "Busted Crown", "Ectoplasm");
        // Common, uncommon, rare, shop and event relics (strong ones for a big Ironclad deck score high).
        put(9, "Lizard Tail", "TungstenRod", "Dead Branch", "Pen Nib", "Girya");
        put(8, "Torii", "Calipers", "Ginger", "Turnip", "Mango", "Peace Pipe", "Meat on the Bone", "Orichalcum", "Gambling Chip",
            "Charon's Ashes", "Shuriken", "Brimstone", "FossilizedHelix", "Thread and Needle");
        put(7, "Vajra", "Anchor", "Lantern", "Centennial Puzzle", "Bronze Scales", "Red Skull", "Paper Frog", "Pantograph",
            "Singing Bowl", "Question Card", "Molten Egg 2", "Self Forming Clay", "Prayer Wheel", "Incense Burner", "CaptainsWheel",
            "StrikeDummy", "Bag of Marbles", "Ancient Tea Set");
        put(6, "Strawberry", "Pear", "Nunchaku", "Oddly Smooth Stone", "Matryoshka", "White Beast Statue", "Eternal Feather",
            "Magic Flower", "Bottled Tornado", "Bottled Flame", "Bottled Lightning", "Mercury Hourglass", "HornCleat",
            "Gremlin Horn", "Dream Catcher", "Regal Pillow", "Shovel", "Old Coin", "Ice Cream", "StoneCalendar", "OrangePellets",
            "ClockworkSouvenir", "Bird Faced Urn", "Lee's Waffle", "Toy Ornithopter", "Akabeko");
        put(5, "Blood Vial", "Bag of Preparation", "Happy Flower", "Whetstone", "War Paint", "Letter Opener", "InkBottle",
            "Orrery", "Membership Card", "Cauldron", "PreservedInsect", "Boot", "Du-Vu Doll", "Mummified Hand",
            "Frozen Egg 2", "Toxic Egg 2", "Ornamental Fan", "Sundial", "Champion Belt");
        put(4, "Omamori", "Juzu Bracelet", "MawBank", "Smiling Mask", "The Courier", "Chemical X", "Toolbox", "DollysMirror",
            "Kunai", "Pocketwatch", "Blue Candle", "Unceasing Top", "Art of War");
        put(3, "Potion Belt", "WingedGreaves", "MealTicket", "CeramicFish", "Darkstone Periapt", "Medical Kit", "Frozen Eye",
            "HandDrill", "Strange Spoon", "Sling", "TheAbacus", "Tiny Chest");
        put(2, "TwistedFunnel", "Spirit Poop", "Symbiotic Virus", "TeardropLocket");
        put(-100, "PrismaticShard");
    }

    static int score(String id) {
        if ("PrismaticShard".equals(id)) return -100;
        Integer value = SCORES.get(id);
        return value == null ? NEUTRAL : value;
    }

    /** Strike Dummy affects every original-game STRIKE-tagged card, not just starter Strikes. */
    static int score(String id, JsonArray deck) {
        if(!id.equals("StrikeDummy"))return score(id);
        int strikes=0;
        for(JsonElement item:deck) {
            String cardId=item.getAsJsonObject().get("id").getAsString();
            if(cardId.equals("Strike_R") || cardId.equals("Strike_G") || cardId.equals("Strike_B")
                    || cardId.equals("Strike_P") || cardId.equals("Pommel Strike") || cardId.equals("Twin Strike")
                    || cardId.equals("Wild Strike") || cardId.equals("Perfected Strike") || cardId.equals("Swift Strike"))strikes++;
        }
        return strikes==0?0:strikes==1?3:strikes==2?5:7;
    }

    /** Boss-only adjustments use the current deck, never assumed future synergies. */
    static int bossScore(String id, JsonArray deck) {
        if(!id.equals("Runic Cube") && !id.equals("Empty Cage") && !id.equals("Fusion Hammer")
                && !id.equals("Pandora's Box") && !id.equals("Astrolabe") && !id.equals("Mark of Pain"))return score(id);
        int junk=0, basics=0, transformable=0;
        boolean selfDamage=false, apotheosis=false, armaments=false, evolve=false;
        for(JsonElement item:deck) {
            JsonObject card=item.getAsJsonObject();
            String cardId=card.get("id").getAsString();
            String type=card.has("type")?card.get("type").getAsString():"";
            boolean basic=cardId.equals("Strike_R") || cardId.equals("Defend_R");
            if(basic)basics++;
            if(basic || cardId.equals("Bash"))transformable++;
            if((type.equals("CURSE") && !EventRules.unremovable(cardId)) || basic || cardId.equals("Bash"))junk++;
            selfDamage |= cardId.equals("Bloodletting") || cardId.equals("Offering")
                || cardId.equals("Hemokinesis") || cardId.equals("Brutality")
                || cardId.equals("Combust") || cardId.equals("Pain");
            apotheosis |= cardId.equals("Apotheosis");
            armaments |= cardId.equals("Armaments");
            evolve |= cardId.equals("Evolve");
        }
        // ponytail: presence is a coarse synergy threshold; use trigger frequency if this proves too generous.
        switch(id) {
            case "Runic Cube": return selfDamage?8:3;
            case "Fusion Hammer": return apotheosis?8:armaments?6:4;
            case "Pandora's Box": return Math.min(7,Math.max(2,basics));
            case "Astrolabe": return transformable>=3?7:6;
            case "Mark of Pain": return evolve?5:4;
            default: return junk>=2?8:junk==1?6:1;
        }
    }

    private static final Map<String, Integer> RANKS = new HashMap<>();

    private static void rank(int score, int rank, String... ids) {
        for (String id : ids) RANKS.put(id+"@"+score, rank);
    }

    static {
        // Order within a tier (1 = first); the same relic can sit in different tiers, so the score is part of the key.
        rank(10, 1, "Runic Pyramid");
        rank(9, 1, "Runic Dome");
        rank(8, 1, "Fusion Hammer"); rank(8, 2, "Coffee Dripper"); rank(8, 3, "Cursed Key");
        rank(8, 4, "Runic Cube"); rank(8, 5, "Empty Cage");
        rank(7, 1, "Snecko Eye"); rank(7, 2, "Pandora's Box", "Astrolabe"); rank(7, 3, "Philosopher's Stone");
        rank(6, 1, "SlaversCollar"); rank(6, 2, "Calling Bell"); rank(6, 3, "Fusion Hammer");
        rank(6, 4, "Astrolabe"); rank(6, 5, "Empty Cage");
        rank(5, 1, "Mark of Pain"); rank(5, 2, "Black Star"); rank(5, 3, "SacredBark");
        rank(4, 1, "Tiny House"); rank(4, 2, "Black Blood"); rank(4, 3, "Fusion Hammer"); rank(4, 4, "Mark of Pain");
        rank(3, 1, "Runic Cube"); rank(3, 2, "Sozu");
        rank(2, 1, "Pandora's Box");
        rank(1, 1, "Velvet Choker", "Busted Crown", "Ectoplasm", "Empty Cage");
    }

    /** Place of a boss relic within the tier of its bossScore (1 = first). Unlisted pairs, such as Pandora's Box at 3-6,
     * come after every listed one; equal ranks are the network's call. */
    static int rank(String id, int score) {
        Integer value = RANKS.get(id+"@"+score);
        return value == null ? Integer.MAX_VALUE : value;
    }

    /** True when the Sapphire Key is worth more than the relic it replaces. */
    static boolean takeSapphireKey(String linkedRelic) {
        return SAPPHIRE_KEY > score(linkedRelic);
    }
}
