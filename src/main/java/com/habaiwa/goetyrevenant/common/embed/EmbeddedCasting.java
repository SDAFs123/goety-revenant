package com.habaiwa.goetyrevenant.common.embed;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.possession.Possession;
import com.habaiwa.goetyrevenant.common.soul.SoulData;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 嵌入式聚晶的**施法管线**（服务端）。
 *
 * <p>这就是「不用携带法杖也能放法术」的入口：UI 定稿后，轮盘选完器官直接调
 * {@link #cast(ServerPlayer, OrganType)} 即可 —— 所有规则都收在这里，和 UI 解耦。</p>
 *
 * <p>和法杖**互不冲突**：法杖走的是物品自身的施法流程，这里是完全独立的一条路径，
 * 两边各有各的冷却，可以同时用。</p>
 */
public final class EmbeddedCasting {
    private static final String KEY_CD_PREFIX = "GREmbedCd_";

    private EmbeddedCasting() {
    }

    /**
     * 施放某个器官里嵌的聚晶。
     *
     * @return 是否真的放出去了
     */
    public static boolean cast(ServerPlayer player, OrganType organ) {
        ItemStack focus = BodyModData.getFocus(player, organ);
        if (focus == null || focus.isEmpty()) {
            return false;
        }

        // 附身在别人的身体里：五脏不认你的聚晶，用不了
        if (SoulData.isPossessing(player)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_cast_possessing"), true);
            return false;
        }
        // 原版旁观者不行 —— 但那不是我们的灵魂状态，别混为一谈。
        // **灵魂状态现在可以放嵌入的聚晶**：灵魂能量用的就是玩家自己那一份
        // （聚晶本来就是在花它），所以没理由拦着。
        if (player.isSpectator()) {
            return false;
        }

        if (!(focus.getItem() instanceof IFocus focusItem)) {
            return false;
        }
        ISpell spell = focusItem.getSpell();
        if (spell == null) {
            return false;
        }

        int cooldown = getCooldown(player, organ);
        if (cooldown > 0) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_cast_cooldown",
                    (cooldown + 19) / 20), true);
            return false;
        }

        int cost = spell.soulCost(player, focus);
        if (cost > 0 && !SEHelper.decreaseSESouls(player, cost)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_no_energy"), true);
            return false;
        }

        SpellStat stat = spell.defaultStats();
        spell.useSpell(player.serverLevel(), player, focus, spell.defaultCastDuration(), stat);
        // 冷却受灵魂温度影响：低温放得快、高温放得慢
        setCooldown(player, organ, Math.round(spell.defaultSpellCooldown()
                * com.habaiwa.goetyrevenant.common.soul.SoulTemperature.cooldownScale(player)
                // 强化「速」：冷却再短一截
                * com.habaiwa.goetyrevenant.common.embed.UpgradeSlots.cooldownFactor(player)));
        // 放一发就降温（温度是底层资源）
        com.habaiwa.goetyrevenant.common.soul.SoulTemperature.onCast(player);
        // 记一笔「刚放了什么元素」—— 连携链的主元素从这来
        com.habaiwa.goetyrevenant.common.reaction.ComboChain.recordCast(player, spell);
        return true;
    }

    /** 这个器官里嵌了什么（给轮盘 UI 读）。 */
    @Nullable
    public static ItemStack focusAt(ServerPlayer player, OrganType organ) {
        ItemStack stack = BodyModData.getFocus(player, organ);
        return stack == null || stack.isEmpty() ? null : stack;
    }

    // ===== 冷却（每个器官各一条，和法杖自己的冷却互不干扰）=====

    public static int getCooldown(ServerPlayer player, OrganType organ) {
        return player.getPersistentData().getInt(KEY_CD_PREFIX + organ.getId());
    }

    public static void setCooldown(ServerPlayer player, OrganType organ, int ticks) {
        player.getPersistentData().putInt(KEY_CD_PREFIX + organ.getId(), Math.max(0, ticks));
    }

    /** 每 tick 递减冷却。 */
    public static void tickCooldowns(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        for (OrganType organ : OrganType.values()) {
            String key = KEY_CD_PREFIX + organ.getId();
            int left = tag.getInt(key);
            if (left > 0) {
                tag.putInt(key, left - 1);
            }
        }
    }

    public static String modId() {
        return GoetyRevenant.MODID;
    }
}
