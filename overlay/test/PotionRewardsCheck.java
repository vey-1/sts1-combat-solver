package sts1solver;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Phase boundaries, actual replacement advice and disabled resurrection. */
public final class PotionRewardsCheck {
    static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    static JsonObject bottle(String id) {
        JsonObject p=new JsonObject();p.addProperty("id",id);p.addProperty("name",id);
        p.addProperty("can_discard",true);return p;
    }
    static boolean hasPurge(OutsidePacket packet) {
        for(OutsidePacket.Choice c:packet.choices)if((c.bits>>27)==5)return true;
        return false;
    }
    public static void main(String[] args) throws Exception {
        JsonObject game=new JsonObject();game.addProperty("act",3);game.addProperty("floor",42);
        JsonArray deck=new JsonArray();deck.add(bottle("Demon Form"));game.add("deck",deck);
        String[] priority={"FairyPotion","Swift Potion","GamblersBrew","DistilledChaos","PowerPotion",
            "LiquidMemories","CultistPotion","DuplicationPotion","GhostInAJar"};
        int[] early=new int[priority.length];
        for(int i=0;i<priority.length;i++)early[i]=PotionRewards.value(priority[i],game);
        game.addProperty("floor",43);
        for(int i=0;i<priority.length;i++)check(PotionRewards.value(priority[i],game)>early[i],priority[i]+" late boost");
        check(PotionRewards.value("FairyPotion",game)>PotionRewards.value("Fruit Juice",game),"resurrection priority");
        check(PotionRewards.value("Fire Potion",game)==14,"damage potion unchanged");
        check(PotionRewards.value("UnknownPotion",game)==-1,"unknown remains manual");
        check(PotionRewards.value("GamblersBrew",game)==60,"Gambler's Brew under its game id");
        check(PotionRewards.value("BlessingOfTheForge",game)==9,"Blessing of the Forge: low value, no late bonus");
        game.addProperty("act",2);game.addProperty("floor",50);
        for(int i=0;i<priority.length;i++)check(PotionRewards.value(priority[i],game)==early[i],"Act 2 unchanged");
        game.addProperty("act",4);game.addProperty("floor",54);
        check(PotionRewards.value("PowerPotion",game)==52,"Act 4 shop power potion boost");
        game.addProperty("floor",55);
        check(PotionRewards.value("Swift Potion",game)==60,"spear/shield preparation boost");
        game.addProperty("floor",56);
        check(PotionRewards.value("Swift Potion",game)==42,"pre-spear scope boundary");
        game.addProperty("act",3);game.addProperty("floor",43);
        game.addProperty("screen_type","COMBAT_REWARD");game.addProperty("room_phase","COMPLETE");
        JsonArray inventory=new JsonArray();inventory.add(bottle("CultistPotion"));inventory.add(bottle("FairyPotion"));
        game.add("potions",inventory);
        JsonObject screen=new JsonObject(),reward=new JsonObject();
        reward.addProperty("reward_type","POTION");reward.add("potion",bottle("PowerPotion"));
        JsonArray rewards=new JsonArray();rewards.add(reward);screen.add("rewards",rewards);game.add("screen_state",screen);
        check(!PotionRewards.advise(game).actionable(),"do not replace saved scaling with power potion at equal value");
        inventory.set(0,bottle("Dexterity Potion"));
        check(PotionRewards.advise(game).actionable() && PotionRewards.advise(game).slot==0,"late power replaces generic stat bottle");
        reward.add("potion",bottle("Swift Potion"));
        check(PotionRewards.advise(game).actionable() && PotionRewards.advise(game).slot==0,"late draw replaces generic stat bottle");
        game.addProperty("floor",42);
        inventory.set(0,bottle("CultistPotion"));
        check(!PotionRewards.advise(game).actionable(),"early draw does not replace scaling at equal value");
        game.addProperty("floor",43);
        JsonArray relics=new JsonArray();relics.add(bottle("Mark of the Bloom"));game.add("relics",relics);
        check(PotionRewards.value("FairyPotion",game)==0,"Bloom disables late resurrection bonus");
        check(PotionRewards.value("BloodPotion",game)==0,"Bloom healing unchanged");
        relics.add(bottle("Sozu"));
        check(!PotionRewards.advise(game).actionable(),"Sozu still blocks pickup");
        JsonObject fixture=new JsonParser().parse(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject shopGame=fixture.getAsJsonObject("state").getAsJsonObject("game_state");
        JsonArray shop=shopGame.getAsJsonObject("screen_state").getAsJsonArray("potions");
        shop.get(0).getAsJsonObject().addProperty("id","PowerPotion");
        shop.get(1).getAsJsonObject().addProperty("id","Swift Potion");
        shop.get(2).getAsJsonObject().addProperty("id","Fire Potion");
        shopGame.addProperty("act",3);
        for(int floor:new int[]{42,43}) {
            shopGame.addProperty("floor",floor);
            OutsidePacket packet=OutsidePacket.build(Distill2.load(),fixture.getAsJsonObject("state"),fixture.getAsJsonObject("visible"));
            Distill2.Result base=packet.score(), adjusted=packet.policyScore();
            int seen=0;
            for(int i=0;i<packet.choices.size();i++) {
                long bits=packet.choices.get(i).bits;
                if((bits>>27)!=3)continue;
                int index=(int)(bits&((1L<<27)-1));
                float expected=floor==42?0:index==0?2.4f:index==1?1.8f:0;
                check(Math.abs(adjusted.scores[i]-base.scores[i]-expected)<0.0001f,"actual shop priority bonus");
                seen++;
            }
            check(seen==3,"all three affordable shop potions tested");
        }
        // Shop purge button: only a removable curse or starter card makes it worth listing (Ascender's Bane is not removable).
        JsonObject base=fixture.getAsJsonObject("state");
        JsonArray onlyBane=new JsonArray();
        for(String[] spec:new String[][]{{"AscendersBane","CURSE"},{"ASCENDERS_BANE","CURSE"},{"Shrug It Off","SKILL"}}) {
            JsonObject card=new JsonParser().parse(shopGame.getAsJsonArray("deck").get(0).toString()).getAsJsonObject();
            card.addProperty("id",spec[0]);card.addProperty("type",spec[1]);onlyBane.add(card);
        }
        shopGame.add("deck",onlyBane);
        check(!hasPurge(OutsidePacket.build(Distill2.load(),base,fixture.getAsJsonObject("visible"))),"no purge button for unremovable curses only");
        JsonObject injury=new JsonParser().parse(onlyBane.get(0).toString()).getAsJsonObject();
        injury.addProperty("id","Injury");onlyBane.add(injury);
        check(hasPurge(OutsidePacket.build(Distill2.load(),base,fixture.getAsJsonObject("visible"))),"a removable curse lists the purge button");
        // Out of combat: never use or discard a potion, except Fruit Juice at once and Entropic Brew with another free slot.
        String[][][] bottles={{{"Fruit Juice"},{"Potion Slot"}},{{"BloodPotion"},{"Potion Slot"}},
            {{"EntropicBrew"},{"Potion Slot"}},{{"EntropicBrew"},{"Fire Potion"}}};
        String[] wanted={"potion use 0",null,"potion use 0",null};
        for(int k=0;k<bottles.length;k++) {
            JsonArray slots=new JsonArray();
            for(String[] b:bottles[k]) {
                JsonObject p=bottle(b[0]);p.addProperty("can_use",!b[0].equals("Potion Slot"));p.addProperty("requires_target",false);
                p.addProperty("can_discard",!b[0].equals("Potion Slot"));slots.add(p);
            }
            shopGame.add("potions",slots);
            OutsidePacket packet=OutsidePacket.build(Distill2.load(),base,fixture.getAsJsonObject("visible"));
            String command=packet.choices.get(packet.policyScore().best).command;
            check(wanted[k]==null?!command.startsWith("potion "):command.equals(wanted[k]),"out-of-combat potion case "+k+": "+command);
            if(wanted[k]==null)for(int i=0;i<packet.choices.size();i++)if(packet.choices.get(i).command.startsWith("potion ")) {
                float[] s=packet.score().scores.clone(); s[i]=1e9f; // the network's favourite potion action never survives
                Distill2.Result masked=packet.potionRule(new Distill2.Result(s,i));
                check(!packet.choices.get(masked.best).command.startsWith("potion ") && packet.ruleNote!=null,"potion action masked in case "+k);
            }
        }
        System.out.println("PASS: late potion values, phase boundaries, replacement advice, unknown/Sozu/Bloom, purge button, out-of-combat potions");
        priced();
    }

    /** A reward screen with HpValue's context: act 1 hallway on row 3, 56/80, A20, Burning Blood. */
    static JsonObject rewardGame(String[] held, String... offered) {
        JsonObject g=new JsonParser().parse("{\"screen_type\":\"COMBAT_REWARD\",\"room_phase\":\"COMPLETE\",\"act\":1,\"floor\":4,"
            +"\"ascension_level\":20,\"current_hp\":56,\"max_hp\":80,\"relics\":[{\"id\":\"Burning Blood\"}],"
            +"\"solver_auto_context\":{\"room\":\"MonsterRoom\",\"y\":3}}").getAsJsonObject();
        JsonArray slots=new JsonArray();
        for(String id:held){JsonObject b=bottle(id);b.addProperty("can_discard",!id.equals("Potion Slot"));slots.add(b);}
        g.add("potions",slots);
        JsonArray rewards=new JsonArray();
        for(String id:offered){JsonObject r=new JsonObject();r.addProperty("reward_type","POTION");r.add("potion",bottle(id));rewards.add(r);}
        JsonObject screen=new JsonObject();screen.add("rewards",rewards);g.add("screen_state",screen);
        return g;
    }

    /** docs/hp-value.md, "领药": real-HP prices from the tier table; a swap needs more than one 低 tier of gain. */
    static void priced() {
        // Act 1 tiers: Weak 低 (2.1875), Block 中 (4.375), Fire 较高 (7), Ghost 很高 (14).
        PotionRewards.Advice a=PotionRewards.advise(rewardGame(new String[]{"Weak Potion","Potion Slot"},"Block Potion","Fire Potion"));
        check(a.actionable()&&a.slot==1&&a.incoming.equals("Fire Potion"),"empty slot: the dearest offer, got "+a.incoming);
        a=PotionRewards.advise(rewardGame(new String[]{"Weak Potion","Block Potion"},"Fire Potion"));
        check(a.actionable()&&a.outgoing.equals("Weak Potion")&&a.incoming.equals("Fire Potion"),"full: 低 -> 较高 swaps the cheapest");
        a=PotionRewards.advise(rewardGame(new String[]{"Weak Potion","Weak Potion"},"Block Potion"));
        check(!a.actionable(),"full: 低 -> 中 is one 低 tier of gain, kept");
        a=PotionRewards.advise(rewardGame(new String[]{"Block Potion","Block Potion"},"Fire Potion"));
        check(a.actionable()&&a.message.contains("4.4")&&a.message.contains("7.0"),"full: 中 -> 较高 (2.625) swaps, prices in the note: "+a.message);
        a=PotionRewards.advise(rewardGame(new String[]{"Weak Potion","Block Potion"},"FocusPotion"));
        check(!a.actionable()&&a.message.contains("未知"),"full: an unknown offer is left to the player");
        a=PotionRewards.advise(rewardGame(new String[]{"FocusPotion","Weak Potion"},"GhostInAJar"));
        check(a.actionable()&&a.outgoing.equals("Weak Potion"),"an unknown held potion is never swapped out");
        a=PotionRewards.advise(rewardGame(new String[]{"Block Potion","Block Potion"},"FairyPotion"));
        check(a.actionable()&&a.incoming.equals("FairyPotion"),"a Fairy is worth a death");
        JsonObject bloom=rewardGame(new String[]{"Block Potion","Block Potion"},"BloodPotion","Regen Potion","FairyPotion");
        bloom.getAsJsonArray("relics").add(new JsonParser().parse("{\"id\":\"Mark of the Bloom\"}"));
        check(!PotionRewards.advise(bloom).actionable(),"Mark of the Bloom: healing potions are worth nothing");
        // Without HpValue's context (no max HP here) the old point scores apply.
        JsonObject old=rewardGame(new String[]{"Block Potion","Block Potion"},"Swift Potion");
        old.remove("solver_auto_context");
        check(PotionRewards.advise(old).message.contains("组合分"),"no context: the old inventory scores");
        System.out.println("PASS: real-HP potion rewards: dearest offer fills a slot, a swap needs more than one 低 tier, unknown and Bloom rules, old scores without context");
    }
}
