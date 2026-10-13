package com.habaiwa.goetyrevenant.common.reaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;

/**
 * **元素反应表（71 条）** —— 全部放在数据文件里，加一条不用改代码。
 *
 * <pre>
 *   双元素 21    三元素 35    混沌（四元素） 8    同元素 7
 * </pre>
 *
 * <h3>优先级（高的覆盖低的，不叠加）</h3>
 * <ol>
 *   <li>混沌（四元素）—— 连携链 4 层</li>
 *   <li>三元素 —— 连携链 3 层</li>
 *   <li>双元素 —— 两种元素同时命中</li>
 *   <li>同元素 —— 同属性叠加</li>
 * </ol>
 */
public final class ElementReaction {
    /** 一条反应。 */
    public record Rule(List<ReactionElement> combo, String name, float multiplier,
                       List<String> effects, int wildcards) {
        /** 参与反应的元素个数（含通配位）。 */
        public int size() {
            return this.combo.size() + this.wildcards;
        }
    }

    /** 反应层级。 */
    public enum Tier {
        SAME(1), DUAL(2), TRIPLE(3), CHAOS(4);

        private final int level;

        Tier(int level) {
            this.level = level;
        }

        public int level() {
            return this.level;
        }
    }

    private static final List<Rule> SAME = new ArrayList<>();
    private static final List<Rule> DUAL = new ArrayList<>();
    private static final List<Rule> TRIPLE = new ArrayList<>();
    private static final List<Rule> CHAOS = new ArrayList<>();

    private ElementReaction() {
    }

    /* ------------------------------------------------------------------ */

    public static void load(MinecraftServer server) {
        SAME.clear();
        DUAL.clear();
        TRIPLE.clear();
        CHAOS.clear();
        try {
            var resources = server.getResourceManager()
                    .listResources("element_reactions", rl -> rl.getPath().endsWith(".json"));
            for (var entry : resources.entrySet()) {
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    readFile(JsonParser.parseReader(reader).getAsJsonObject());
                } catch (Throwable t) {
                    System.err.println("[Goety: Revenant] 元素反应表读取失败 " + entry.getKey() + ": " + t);
                }
            }
            System.out.println("[Goety: Revenant] 元素反应表已加载：同元素 " + SAME.size()
                    + " / 双元素 " + DUAL.size() + " / 三元素 " + TRIPLE.size()
                    + " / 混沌 " + CHAOS.size());
        } catch (Throwable t) {
            System.err.println("[Goety: Revenant] 元素反应表加载失败（已忽略，游戏照常）: " + t);
        }
    }

    private static void readFile(JsonObject root) {
        // 派系 → 元素 的指派表
        JsonObject map = obj(root, "派系映射");
        for (var e : map.entrySet()) {
            ReactionElement element = ReactionElement.byId(e.getValue().getAsString());
            if (element != null) {
                ReactionElement.map(e.getKey(), element);
            }
        }
        if (root.has("默认元素")) {
            ReactionElement.setFallback(ReactionElement.byId(root.get("默认元素").getAsString()));
        }
        readRules(root, "同元素", SAME);
        readRules(root, "双元素", DUAL);
        readRules(root, "三元素", TRIPLE);
        readRules(root, "混沌", CHAOS);
    }

    private static void readRules(JsonObject root, String section, List<Rule> target) {
        JsonElement e = root.get(section);
        if (e == null || !e.isJsonArray()) {
            return;
        }
        for (JsonElement item : e.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            List<ReactionElement> combo = new ArrayList<>();
            int wildcards = 0;
            JsonArray arr = o.getAsJsonArray("组合");
            if (arr != null) {
                for (JsonElement el : arr) {
                    String s = el.getAsString();
                    if ("任意".equals(s)) {
                        wildcards++;           // 「任意四元素」这种通配位
                        continue;
                    }
                    ReactionElement element = ReactionElement.byId(s);
                    if (element != null) {
                        combo.add(element);
                    }
                }
            }
            if (combo.isEmpty() && wildcards == 0) {
                continue;
            }
            String name = o.has("反应") ? o.get("反应").getAsString() : "反应";
            float mult = o.has("倍率") ? o.get("倍率").getAsFloat() : 1.0F;
            List<String> effects = new ArrayList<>();
            JsonArray ef = o.getAsJsonArray("效果");
            if (ef != null) {
                for (JsonElement el : ef) {
                    effects.add(el.getAsString());
                }
            }
            target.add(new Rule(combo, name, mult, effects, wildcards));
        }
    }

    private static JsonObject obj(JsonObject root, String name) {
        JsonElement e = root.get(name);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    /* ------------------------------------------------------------------ */

    /**
     * **这次命中该触发哪条反应**。
     *
     * @param elements 本次连携链里同时命中的元素（可重复，重复代表同元素）
     * @return 命中的反应；没有则为 null
     */
    @Nullable
    public static Rule resolve(List<ReactionElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return null;
        }
        // 1) 混沌：四种不同元素
        Rule r = match(CHAOS, elements);
        if (r != null) {
            return r;
        }
        // 2) 三元素
        r = match(TRIPLE, elements);
        if (r != null) {
            return r;
        }
        // 3) 双元素
        r = match(DUAL, elements);
        if (r != null) {
            return r;
        }
        // 4) 同元素
        return match(SAME, elements);
    }

    /** 组合里的元素是否都在命中列表里（不需要顺序，但需要齐全）。 */
    private static Rule match(List<Rule> rules, List<ReactionElement> hit) {
        for (Rule rule : rules) {
            if (containsAll(hit, rule.combo())
                    && distinct(hit) >= rule.size()) {
                return rule;
            }
        }
        return null;
    }

    private static int distinct(List<ReactionElement> hit) {
        return (int) hit.stream().distinct().count();
    }

    private static boolean containsAll(List<ReactionElement> hit, List<ReactionElement> combo) {
        List<ReactionElement> pool = new ArrayList<>(hit);
        for (ReactionElement need : combo) {
            if (!pool.remove(need)) {
                return false;
            }
        }
        return true;
    }

    /** 连携链层数能触发到哪一档。 */
    public static Tier tierForChain(int chainLength) {
        if (chainLength >= 4) {
            return Tier.CHAOS;
        }
        if (chainLength == 3) {
            return Tier.TRIPLE;
        }
        if (chainLength == 2) {
            return Tier.DUAL;
        }
        return Tier.SAME;
    }

    /** 表里现在有多少条（调试用）。 */
    public static String summary() {
        return "同元素 " + SAME.size() + " / 双元素 " + DUAL.size()
                + " / 三元素 " + TRIPLE.size() + " / 混沌 " + CHAOS.size();
    }

    /** 按名字查一条反应（调试 / 提示用）。 */
    @Nullable
    public static Rule byName(String name) {
        for (List<Rule> list : Arrays.asList(SAME, DUAL, TRIPLE, CHAOS)) {
            for (Rule r : list) {
                if (r.name().equals(name) || r.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                    return r;
                }
            }
        }
        return null;
    }
}
