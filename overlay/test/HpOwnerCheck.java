package sts1solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One owner for HP value (docs/architecture.md route item 6, V2; issue #101). Outside HpValue and HpPrices, code may
 * ask HpValue for a cost and HpPrices for a price, and nothing else: no tier constants, no typical situation, no
 * tierPrice, no model parameters, and no cost difference built from two cost calls (HpValue.extra owns it).
 * usage: HpOwnerCheck SRC_DIR
 */
public final class HpOwnerCheck {
    static final Set<String> OWNERS = new HashSet<>(Arrays.asList("HpValue.java", "HpPrices.java"));
    /** The questions consumers may ask. */
    static final Set<String> HP_VALUE = new HashSet<>(Arrays.asList("Context", "context", "cost", "gain", "reserve", "extra",
        "export", "exportDeath"));
    static final Set<String> HP_PRICES = new HashSet<>(Arrays.asList("Reward", "reward", "export", "smokeAllowance"));
    static final Pattern MEMBER = Pattern.compile("\\b(HpValue|HpPrices)\\s*\\.\\s*(\\w+)");
    /** Two cost calls in one statement joined by a minus: a marginal cost that HpValue.extra owns. */
    static final Pattern DIFFERENCE = Pattern.compile("\\bHpValue\\s*\\.\\s*cost\\s*\\([^;]*\\)\\s*-\\s*HpValue\\s*\\.\\s*cost\\s*\\(");

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    /** What this consumer source does that only the owners may do. */
    static List<String> violations(String source) {
        String code = PolicyBoundaryCheck.code(source);
        List<String> found = new ArrayList<>();
        Matcher m = MEMBER.matcher(code);
        while (m.find()) {
            Set<String> allowed = m.group(1).equals("HpValue") ? HP_VALUE : HP_PRICES;
            if (!allowed.contains(m.group(2))) found.add(m.group(1) + "." + m.group(2));
        }
        if (DIFFERENCE.matcher(code).find()) found.add("cost difference");
        return found;
    }

    public static void main(String[] args) throws Exception {
        // the violating cases: a consumer pricing a reward itself, reading the model, or building a marginal cost
        check(violations("double p = HpPrices.tierPrice(HpPrices.TOP, HpPrices.stage(act));")
            .containsAll(Arrays.asList("HpPrices.tierPrice", "HpPrices.TOP", "HpPrices.stage")), "a consumer pricing a tier is rejected");
        check(violations("HpValue.Context t = HpPrices.typical(1);").contains("HpPrices.typical"), "the typical situation is rejected");
        check(violations("int d = HpValue.DAMAGE[0][0]; double x = HpValue.death(c);")
            .containsAll(Arrays.asList("HpValue.DAMAGE", "HpValue.death")), "model internals are rejected");
        check(violations("double m = HpValue.cost(k + 6, hp, v) - HpValue.cost(6, hp, v);").contains("cost difference"),
            "a cost difference is rejected");
        // the allowed questions, and comments and strings, pass
        check(violations("double c = HpValue.cost(k, hp, v), e = HpValue.extra(k, 6, hp, v);"
            + " double p = HpPrices.reward().relics(1).price(act); HpPrices.Reward r = null; // HpPrices.TOP"
            + " String s = \"HpValue.death\";").isEmpty(), "allowed questions pass");

        Path dir = Paths.get(args[0]);
        int consumers = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.java")) {
            for (Path file : files) {
                if (OWNERS.contains(file.getFileName().toString())) continue;
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (!MEMBER.matcher(PolicyBoundaryCheck.code(source)).find()) continue;
                consumers++;
                List<String> found = violations(source);
                check(found.isEmpty(), file.getFileName() + " prices HP outside HpValue/HpPrices: " + found);
            }
        }
        check(consumers >= 3, "expected the known consumers (EventRules, OutsidePacket, SolverMod), found " + consumers);
        System.out.println("HpOwnerCheck passed: " + consumers + " consumers ask HpValue/HpPrices only");
    }
}
