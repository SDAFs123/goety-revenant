package com.habaiwa.goetyrevenant.common.possession;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * **附身适配层** —— 数据驱动的"这个生物有什么独有本事"。
 *
 * <h3>为什么要有一层适配</h3>
 * <p>外观和"怎么动"可以通用（那是渲染和 AI 的事），但<b>每个生物的独有技能不是通用数据</b> ——
 * 诡厄巫法生物放的法术、铁魔法生物念的咒语、Boss 的专属技能，都是各写各的。
 * 连做这套体系的愚者整合包自己也是"为模组内的怪物逐个适配专属技能和动画"。</p>
 *
 * <h3>关键是：逐个适配 ≠ 逐个改代码</h3>
 * <p>所以能力放在 <b>JSON 数据文件</b> 里（{@code data/<命名空间>/possess_abilities/*.json}），
 * 加一个生物就是加一段配置，不用重新编译：</p>
 *
 * <pre>
 * {
 *   "blacklist": ["minecraft:ender_dragon"],
 *   "mobs": {
 *     "minecraft:blaze": {
 *       "abilities": [
 *         { "type": "effect", "effect": "minecraft:fire_resistance" },
 *         { "type": "attribute", "attribute": "minecraft:generic.attack_damage", "value": 8.0 },
 *         { "type": "command", "command": "say 我附身了烈焰人" }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 *
 * <p><b>没有配置的生物不会出问题</b> —— 自动降级，只用通用层的移动/外观/姿态。</p>
 */
public final class PossessProfile {
    /** 一个能力：类型 + 参数。 */
    public record Ability(String type, Map<String, String> args) {
    }

    /** 某个生物的配置。 */
    public record Profile(List<Ability> abilities) {
    }

    private static final Map<ResourceLocation, Profile> TABLE = new HashMap<>();
    private static final Set<ResourceLocation> BLACKLIST = new HashSet<>();

    private PossessProfile() {
    }

    /* ------------------------------------------------------------------ */
    /*  加载（服务端启动 / reload 时读一遍）                                 */
    /* ------------------------------------------------------------------ */

    public static void load(MinecraftServer server) {
        TABLE.clear();
        BLACKLIST.clear();
        try {
            var resources = server.getResourceManager()
                    .listResources("possess_abilities", rl -> rl.getPath().endsWith(".json"));
            for (var entry : resources.entrySet()) {
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    readFile(JsonParser.parseReader(reader).getAsJsonObject());
                } catch (Throwable t) {
                    System.err.println("[Goety: Revenant] 附身配置读取失败 " + entry.getKey() + ": " + t);
                }
            }
            System.out.println("[Goety: Revenant] 附身适配层已加载：" + TABLE.size() + " 个生物，"
                    + BLACKLIST.size() + " 个黑名单");
        } catch (Throwable t) {
            System.err.println("[Goety: Revenant] 附身适配层加载失败（已忽略，游戏照常）: " + t);
        }
    }

    private static void readFile(JsonObject root) {
        if (root.has("blacklist")) {
            for (JsonElement e : root.getAsJsonArray("blacklist")) {
                ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
                if (rl != null) {
                    BLACKLIST.add(rl);
                }
            }
        }
        if (!root.has("mobs")) {
            return;
        }
        for (var en : root.getAsJsonObject("mobs").entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(en.getKey());
            if (id == null) {
                continue;
            }
            List<Ability> list = new ArrayList<>();
            JsonObject obj = en.getValue().getAsJsonObject();
            if (obj.has("abilities")) {
                JsonArray arr = obj.getAsJsonArray("abilities");
                for (JsonElement ae : arr) {
                    JsonObject ao = ae.getAsJsonObject();
                    String type = ao.has("type") ? ao.get("type").getAsString() : "";
                    Map<String, String> args = new HashMap<>();
                    for (var a : ao.entrySet()) {
                        if (!a.getKey().equals("type") && a.getValue().isJsonPrimitive()) {
                            args.put(a.getKey(), a.getValue().getAsString());
                        }
                    }
                    list.add(new Ability(type, args));
                }
            }
            TABLE.put(id, new Profile(list));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  查询 / 应用                                                         */
    /* ------------------------------------------------------------------ */

    /** 这个生物被禁止附身吗（愚者那种"变身黑名单"，挡掉会崩的生物）。 */
    public static boolean blacklisted(LivingEntity body) {
        return BLACKLIST.contains(BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()));
    }

    /** 附身成功时，把这具身体的独有本事发给玩家。 */
    public static void apply(ServerPlayer player, LivingEntity body) {
        if (!com.habaiwa.goetyrevenant.common.config.RevenantConfig.possessAbilityLayer) {
            return;   // 配置里关掉了专属技能层：只用通用层（移动 / 外观 / 姿态）
        }
        Profile profile = TABLE.get(BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()));
        if (profile == null) {
            return;   // 没适配过的生物：只用通用层，不报错
        }
        for (Ability ability : profile.abilities()) {
            try {
                runAbility(player, ability);
            } catch (Throwable t) {
                System.err.println("[Goety: Revenant] 附身能力执行失败 " + ability.type() + ": " + t);
            }
        }
    }

    /** 能力类型注册表 —— 想支持新玩法就在这里加一个分支（配置里只用写 type）。 */
    private static void runAbility(ServerPlayer player, Ability ability) {
        switch (ability.type()) {
            case "effect" -> {
                // { "type": "effect", "effect": "minecraft:strength", "amplifier": 1, "seconds": 无限 }
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(
                        ResourceLocation.tryParse(ability.args().getOrDefault("effect", "")));
                if (effect != null) {
                    int amp = (int) parseDouble(ability.args().get("amplifier"), 0);
                    int ticks = (int) parseDouble(ability.args().get("seconds"), -1) * 20;
                    player.addEffect(new MobEffectInstance(effect, ticks <= 0 ? Integer.MAX_VALUE : ticks,
                            amp, true, false, false));
                }
            }
            case "attribute" -> {
                // { "type": "attribute", "attribute": "minecraft:generic.max_health", "value": 40, "mode": "add" }
                Attribute attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                        ResourceLocation.tryParse(ability.args().getOrDefault("attribute", "")));
                if (attr != null) {
                    AttributeInstance inst = player.getAttribute(attr);
                    if (inst != null) {
                        double value = parseDouble(ability.args().get("value"), 0.0D);
                        double current = inst.getValue();
                        AttributeModifier.Operation op = "multiply".equals(ability.args().get("mode"))
                                ? AttributeModifier.Operation.MULTIPLY_TOTAL
                                : AttributeModifier.Operation.ADDITION;
                        double delta = op == AttributeModifier.Operation.ADDITION ? value - current : value - 1.0D;
                        inst.addTransientModifier(new AttributeModifier(
                                java.util.UUID.nameUUIDFromBytes(("goety_revenant:possess_attr:" + ability.args().get("attribute"))
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                "goety_revenant.possess_ability", delta, op));
                    }
                }
            }
            case "command" -> {
                // 兜底：任何模组的技能都能用指令挂上来（最通用的适配手段）
                String cmd = ability.args().getOrDefault("command", "");
                if (!cmd.isEmpty() && player.getServer() != null) {
                    player.getServer().getCommands()
                            .performPrefixedCommand(player.createCommandSourceStack(), cmd);
                }
            }
            default -> {
                // 未知类型：忽略（说明配置里写了新玩法但代码还没加 handler）
            }
        }
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
