package com.habaiwa.goetyrevenant.common.reaction;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.common.body.BodyModData;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import com.habaiwa.goetyrevenant.common.config.RevenantConfig;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * **连携链** —— 主聚晶命中之后发生的那一串事。
 *
 * <h3>流程</h3>
 * <pre>
 *   主聚晶命中
 *     → 拿到主元素（最近施放的那枚聚晶）
 *     → 依次唤醒身上**嵌入的聚晶**（叶），把它们各自的元素叠上来
 *     → 元素列表交给 {@link ElementReaction} 查表
 *     → 命中的反应由 {@link ReactionEffect} 落地
 * </pre>
 *
 * <h3>连携"不"做什么</h3>
 * <p>连携<b>不会</b>把嵌入的聚晶再放一遍 —— 它不决定伤害数值、攻击形态、属性、释放方式，
 * 只决定「触发什么反应」。所以这里只把元素叠起来，产出的是反应效果。</p>
 *
 * <h3>层数</h3>
 * <p>层数 = 参与的元素个数（主 + 唤醒的嵌入）。2 层能触发双元素反应，
 * 3 层三元素，4 层混沌 —— 层数越高消耗也越大。</p>
 */
public final class ComboChain {
    /** 最近一次施放的聚晶元素。 */
    private static final String KEY_CAST_ELEMENT = "GRCastElement";
    /** 最近一次施放的时间戳（tick）。 */
    private static final String KEY_CAST_TICK = "GRCastTick";
    /** 当前链上已经叠了几层。 */
    private static final String KEY_CHAIN = "GRChainLen";
    /** 这一条链什么时候开始的。 */
    private static final String KEY_CHAIN_TICK = "GRChainTick";

    private ComboChain() {
    }

    /** 施放聚晶时记一笔（主元素从这来）。 */
    public static void recordCast(ServerPlayer player, @Nullable ISpell spell) {
        CompoundTag tag = player.getPersistentData();
        tag.putString(KEY_CAST_ELEMENT, ReactionElement.of(spell == null ? null : spell.getSpellType()).id());
        tag.putInt(KEY_CAST_TICK, player.tickCount);
    }

    /**
     * **主聚晶命中时调**。
     *
     * @return 这次命中该用的伤害倍率（没有反应就是 1.0）
     */
    public static float onMainHit(ServerPlayer player, LivingEntity target) {
        if (!RevenantConfig.comboEnabled) {
            return 1.0F;
        }
        CompoundTag tag = player.getPersistentData();
        int castTick = tag.getInt(KEY_CAST_TICK);
        int age = player.tickCount - castTick;
        if (castTick == 0 || age < 0 || age > RevenantConfig.comboWindow) {
            return 1.0F;   // 不是在施法之后打中的，不算主聚晶命中
        }

        // 链：窗口内继续叠，超时重开
        int lastTick = tag.getInt(KEY_CHAIN_TICK);
        int chain = (player.tickCount - lastTick <= RevenantConfig.comboWindow)
                ? tag.getInt(KEY_CHAIN)
                : 0;
        chain++;
        tag.putInt(KEY_CHAIN, chain);
        tag.putInt(KEY_CHAIN_TICK, player.tickCount);

        // 元素列表：主元素 + 唤醒的嵌入聚晶
        List<ReactionElement> elements = new ArrayList<>();
        ReactionElement main = ReactionElement.byId(tag.getString(KEY_CAST_ELEMENT));
        elements.add(main == null ? ReactionElement.SOUL : main);

        int awakened = awakenEmbedded(player, elements, main);
        if (awakened == 0) {
            return 1.0F;   // 身上没嵌东西，连携不起来
        }
        // 层数：链越长，能触发的档位越高
        int allowed = Math.min(RevenantConfig.comboMaxChain, elements.size());
        while (elements.size() > allowed) {
            elements.remove(elements.size() - 1);
        }

        ElementReaction.Rule rule = ElementReaction.resolve(elements);
        if (rule == null) {
            return 1.0F;
        }
        // 消费灵魂能量：层数越多越贵
        int cost = RevenantConfig.comboCostPerLayer * elements.size();
        if (cost > 0 && !SEHelper.decreaseSESouls(player, cost)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.combo_no_energy"), true);
            return 1.0F;
        }

        player.displayClientMessage(Component.translatable("message.goety_revenant.combo_trigger",
                rule.name(), elements.size()), true);
        ReactionEffect.apply(player, target, rule);
        return rule.multiplier() <= 0.0F ? 1.0F : rule.multiplier();
    }

    /**
     * **唤醒身上嵌入的聚晶**，把它们的元素叠进列表。
     *
     * @return 叠进来了几个
     */
    private static int awakenEmbedded(ServerPlayer player, List<ReactionElement> elements,
                                      ReactionElement main) {
        int count = 0;
        for (OrganType organ : OrganType.values()) {
            if (count >= RevenantConfig.comboMaxChain - 1) {
                break;
            }
            ItemStack focus = BodyModData.getFocus(player, organ);
            if (focus == null || focus.isEmpty() || !(focus.getItem() instanceof IFocus focusItem)) {
                continue;
            }
            ISpell spell = focusItem.getSpell();
            if (spell == null) {
                continue;
            }
            ReactionElement element = ReactionElement.of(spell.getSpellType());
            if (element == main && count > 0) {
                continue;   // 同元素只叠一次，避免白烧能量
            }
            elements.add(element);
            count++;
        }
        return count;
    }

    /** 主动清掉这条链（死亡 / 退出灵魂状态时用）。 */
    public static void clear(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        tag.putInt(KEY_CHAIN, 0);
        tag.putInt(KEY_CHAIN_TICK, 0);
        tag.putInt(KEY_CAST_TICK, 0);
    }
}
