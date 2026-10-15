package com.habaiwa.goetyrevenant.common.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * **数据包标签自检** —— 服务器起来之后，把几个关键标签的**实际内容**打进日志。
 *
 * <h3>为什么需要它</h3>
 * <p>镰刀附魔互通靠"往 Malum 的标签里追加 Goety 镰刀"实现
 * （见 `data/malum/tags/items/*.json`）。但标签**合并**是否真的发生，
 * 在代码里看不出来 —— 只有运行时才知道。而"没生效"在游戏里表现为
 * "附魔台什么都刷不出来"，和"附魔本身有问题"看起来一模一样。</p>
 *
 * <p>所以启动后直接列出来：每个标签**实际**有多少项、里面有没有我们的镰刀。
 * 用户看日志第一行就能判断是数据包没生效，还是附魔判定的问题。</p>
 *
 * <p>（用 `ServerStartedEvent` 而不是静态初始化 —— 标签是服务器数据加载完才有的。）</p>
 */
public final class TagAudit {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** 需要自检的标签（命名空间 : 路径）。 */
    private static final String[] WATCHED = {
            "malum:scythe",
            "malum:soul_hunter_weapon",
            "malum:magic_capable_weapon",
            "goety_revenant:scythes",
    };

    /** 我们关心的几把镰刀 —— 看它们有没有被合进去。 */
    private static final String[] WATCHED_ITEMS = {
            "goety:dark_scythe",
            "goety:dark_metal_scythe",
            "goety:death_scythe",
            "malum:crude_scythe",
            "malum:soul_stained_steel_scythe",
    };

    private TagAudit() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LOG.info("[Goety: Revenant] ===== 标签自检（镰刀附魔互通）=====");
        for (String raw : WATCHED) {
            String[] parts = raw.split(":", 2);
            TagKey<Item> key = TagKey.create(Registries.ITEM,
                    new ResourceLocation(parts[0], parts[1]));
            int total = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                if (new ItemStack(item).is(key)) {
                    total++;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String idStr : WATCHED_ITEMS) {
                String[] ip = idStr.split(":", 2);
                Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(ip[0], ip[1]));
                boolean in = it != net.minecraft.world.item.Items.AIR && new ItemStack(it).is(key);
                sb.append(in ? " [在] " : " [  ] ").append(idStr);
            }
            LOG.info("[Goety: Revenant]   #{}  共 {} 项 ｜{}", raw, total, sb);
        }
        LOG.info("[Goety: Revenant] ===== 若上面 #malum:scythe 里 goety:* 显示 [  ]，说明数据包没生效 =====");

        // ------------------------------------------------------------------
        //  **Mixin 自检** —— 直接问一句"Malum 的镰刀附魔认不认 Goety 的镰刀"。
        //
        //  这句话会走到 Enchantment.canEnchant（Mixin 改的那个方法）：
        //    返回 true  → Mixin 生效了
        //    返回 false → Mixin 没注入（名字对不上 / 配置没被加载）
        //
        //  之所以要这么绕一层：Mixin 失败时 mixins.json 里 required=false 不会报错，
        //  游戏能正常启动、只是功能静默失效 —— 必须主动问一次才看得出来。
        // ------------------------------------------------------------------
        LOG.info("[Goety: Revenant] ===== Mixin 自检（镰刀附魔互通）=====");
        checkMixin("malum", "animated", "goety", "dark_scythe");
        checkMixin("malum", "rebound", "goety", "dark_scythe");
        checkMixin("malum", "ascension", "malum", "crude_scythe");
        LOG.info("[Goety: Revenant] ===== 期望三行都是 true；有 false 说明 EnchantmentMixin 没生效 =====");

        // **精魂 → 灵魂能量的换算自检**
        // 我们靠反射调 Malum 的 SpiritHarvestHandler.getSpiritDropsRaw 拿"本该产几个精魂"，
        // 折算成灵魂能量。反射失败时那条路会静默回落到"按血量给"——
        // 那种静默失效必须能看出来，所以这里直接问一次。
        LOG.info("[Goety: Revenant] ===== 精魂换算自检 =====");
        try {
            Class<?> handler = Class.forName("com.sammy.malum.core.handlers.SpiritHarvestHandler");
            java.lang.reflect.Method m = handler.getMethod("getSpiritDropsRaw",
                    net.minecraft.world.entity.LivingEntity.class);
            LOG.info("[Goety: Revenant]   SpiritHarvestHandler.getSpiritDropsRaw 可用 → {}", m != null);
        } catch (Throwable t) {
            LOG.info("[Goety: Revenant]   ★ 反射失败（会回落到按血量给魂）：{}", t);
        }
        LOG.info("[Goety: Revenant]   精魂→灵魂能量换算率：{} / 个", MalumCompat.SPIRIT_TO_SOULS);

        // **镰刀粒子颜色自检**
        // 颜色靠 Mixin 的【接口注入】—— 给 Goety 的镰刀类加上 Malum 的
        // ISpiritAffiliatedItem。接口注入比方法注入更容易被静默拒绝，
        // 所以直接问一句"这个物品实例到底算不算那个接口"。
        LOG.info("[Goety: Revenant] ===== 镰刀粒子颜色自检 =====");
        Class<?> spiritIface = null;
        try {
            spiritIface = Class.forName("com.sammy.malum.common.item.ISpiritAffiliatedItem");
        } catch (Throwable ignored) {
            LOG.info("[Goety: Revenant]   Malum 缺席，跳过");
        }
        if (spiritIface != null) {
            for (String raw : new String[]{"goety:dark_scythe", "goety:dark_metal_scythe",
                    "goety:death_scythe", "malum:crude_scythe"}) {
                String[] p = raw.split(":", 2);
                Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(p[0], p[1]));
                boolean impl = it != net.minecraft.world.item.Items.AIR && spiritIface.isInstance(it);
                LOG.info("[Goety: Revenant]   {} instanceof ISpiritAffiliatedItem = {}", raw, impl);
            }
            LOG.info("[Goety: Revenant] ===== malum:crude_scythe 是原生对照（天然 true）；"
                    + "goety:* 若为 false 说明接口注入没生效 =====");
        }

        // ==================================================================
        //  **Malum 反射链路体检**
        //
        //  本模组和 Malum 的联动大量依赖反射（因为 Malum 是 compileOnly 软依赖）。
        //  反射失败是**静默**的 —— 拿不到就返回 null / false，功能悄悄没了，
        //  游戏里看不出原因。这几轮踩的坑（Mixin 静默不注入、标签误判）
        //  本质都是同一类问题，所以把每一条链路**逐跳**打出来：
        //  哪一跳断了，日志里那一行就是 [FAIL]。
        // ==================================================================
        LOG.info("[Goety: Revenant] ===== Malum 反射链路体检 =====");
        probe("Rebound.throwScythe", () -> Class.forName(
                        "com.sammy.malum.common.enchantment.scythe.ReboundEnchantment")
                .getMethod("throwScythe",
                        net.minecraft.world.level.Level.class,
                        net.minecraft.world.entity.player.Player.class,
                        net.minecraft.world.InteractionHand.class,
                        ItemStack.class).getName());
        probe("Ascension.triggerAscension", () -> Class.forName(
                        "com.sammy.malum.common.enchantment.scythe.AscensionEnchantment")
                .getMethod("triggerAscension",
                        net.minecraft.world.level.Level.class,
                        net.minecraft.world.entity.player.Player.class,
                        net.minecraft.world.InteractionHand.class,
                        ItemStack.class).getName());
        probe("LivingData.soulData", () -> Class.forName(
                        "com.sammy.malum.common.capability.MalumLivingEntityDataCapability")
                .getField("soulData").getName());
        probe("SoulDataHandler.exposedSoulDuration", () -> Class.forName(
                        "com.sammy.malum.core.handlers.SoulDataHandler")
                .getField("exposedSoulDuration").getName());
        probe("PlayerData.soulWardHandler", () -> Class.forName(
                        "com.sammy.malum.common.capability.MalumPlayerDataCapability")
                .getField("soulWardHandler").getName());
        probe("SoulWardHandler.soulWard", () -> Class.forName(
                        "com.sammy.malum.core.handlers.SoulWardHandler")
                .getField("soulWard").getName());
        probe("属性 malum:scythe_proficiency", () -> net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
                .containsKey(new ResourceLocation("malum", "scythe_proficiency")));
        probe("属性 malum:magic_proficiency", () -> net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
                .containsKey(new ResourceLocation("malum", "magic_proficiency")));
        LOG.info("[Goety: Revenant] ===== 上面每条都该是 [OK]；[FAIL] 的那条链路功能是静默失效的 =====");
    }

    /** 逐跳探测一条反射链路 —— 断了就把那一跳打在日志里。 */
    private static void probe(String label, java.util.concurrent.Callable<Object> body) {
        try {
            Object r = body.call();
            LOG.info("[Goety: Revenant]   [OK]   {} → {}", label, r);
        } catch (Throwable t) {
            LOG.info("[Goety: Revenant]   [FAIL] {} → {}", label, t);
        }
    }

    /** 打印"某条附魔认不认某个物品"。 */
    private static void checkMixin(String enchNs, String enchPath, String itemNs, String itemPath) {
        net.minecraft.world.item.enchantment.Enchantment ench =
                BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation(enchNs, enchPath));
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemNs, itemPath));
        if (ench == null || item == net.minecraft.world.item.Items.AIR) {
            LOG.info("[Goety: Revenant]   {}/{} → {}/{} : 模组缺席，跳过",
                    enchNs, enchPath, itemNs, itemPath);
            return;
        }
        ItemStack test = new ItemStack(item);
        boolean can;
        boolean table;
        try {
            can = ench.canEnchant(test);
            table = ench.canApplyAtEnchantingTable(test);
        } catch (Throwable t) {
            LOG.info("[Goety: Revenant]   {}/{} → {}/{} : 判定抛异常 {}",
                    enchNs, enchPath, itemNs, itemPath, t);
            return;
        }
        LOG.info("[Goety: Revenant]   {}/{} → {}/{} : canEnchant={}  附魔台={}",
                enchNs, enchPath, itemNs, itemPath, can, table);
    }
}
