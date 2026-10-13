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
    }
}
