package com.habaiwa.goetyrevenant.common.ritual;

import com.habaiwa.goetyrevenant.common.body.BodyModData;
import com.habaiwa.goetyrevenant.common.body.Embedding;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 血肉仪式**做完之后到底发生什么**。
 *
 * <p>拆出来单独放，是因为"仪式怎么发动"（{@link FleshRitual}）和"仪式做什么"是两件事，
 * 后者以后还要改（比如三我聚晶的效果）。</p>
 */
public final class FleshRites {
    private FleshRites() {
    }

    /**
     * **吞合** —— 把聚晶吞进对应的窍。
     *
     * <p>走的是和普通嵌入同一条路（{@link Embedding}），
     * 区别只是：材料由仪式的基座出，不需要玩家背包里有那一枚。</p>
     */
    /** 手上这一枚能不能"吞" —— 得是聚晶，而且真的带法术。 */
    public static boolean isEmbeddable(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof com.Polarice3.Goety.api.items.magic.IFocus item)) {
            return false;
        }
        return item.getSpell() != null;
    }

    public static void devour(ServerPlayer player, ItemStack focus) {
        if (!isEmbeddable(focus)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.ritual_no_focus"), true);
            return;
        }
        var item = (com.Polarice3.Goety.api.items.magic.IFocus) focus.getItem();
        var spell = item.getSpell();
        OrganType organ = OrganType.forSpellType(spell.getSpellType());
        if (BodyModData.isSlotUnlocked(player, organ) == false) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_slot_locked",
                    organ.displayName()), true);
            return;
        }
        // 直接装（不走背包、不走扣灵魂能量的那条路 —— 代价已经在仪式里付了）
        BodyModData.setFocus(player, organ, focus.copy());
        Embedding.applyAll(player);
        RevenantNetwork.sendBodyState(player);
        player.displayClientMessage(Component.translatable("message.goety_revenant.ritual_devoured",
                focus.getHoverName(), organ.displayName()), false);
    }

    /**
     * **献祭** —— 把五脏献出去，走向成神。
     *
     * <p>按需求：<b>五脏本身清空、不能再嵌</b>，里面的聚晶一并被献祭掉。</p>
     *
     * <p><b>三我聚晶还没做</b> —— 那是"献祭的聚晶要对"才给的奖励，
     * 具体哪五个聚晶才算"对"、三我聚晶又是什么效果，用户还没定。
     * 这里先把该清清的清了，等定了接上。</p>
     */
    public static void sacrifice(ServerPlayer player) {
        int sacrificed = 0;
        for (OrganType organ : OrganType.viscera()) {
            ItemStack focus = BodyModData.getFocus(player, organ);
            if (focus != null && !focus.isEmpty()) {
                sacrificed++;
            }
            BodyModData.setFocus(player, organ, ItemStack.EMPTY);
            // 永久封锁：这个窍从此不能再嵌
            BodyModData.setSacrificed(player, organ, true);
        }
        Embedding.applyAll(player);
        RevenantNetwork.sendBodyState(player);
        player.displayClientMessage(Component.translatable(
                "message.goety_revenant.ritual_sacrificed", sacrificed), false);
        // TODO 三我聚晶：等用户定"哪五个聚晶算对"以及它的效果
    }
}
