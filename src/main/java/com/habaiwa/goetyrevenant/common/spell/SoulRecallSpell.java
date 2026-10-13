package com.habaiwa.goetyrevenant.common.spell;

import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.SpellType;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.habaiwa.goetyrevenant.common.soul.SoulConstants;
import com.habaiwa.goetyrevenant.common.soul.SoulData;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * 回魂：把队友的尸体召回到施法者身边（多人模式用，一人召唤一人用）。
 *
 * <p>消耗 10000 灵魂能量，把「当前处于灵魂状态、离施法者最近的那名玩家」的尸体
 * 从死亡地点直接拽到施法者脚下 —— 灵魂就不用横跨半个地图去找身体了。</p>
 */
public class SoulRecallSpell implements ISpell {
    @Override
    public int defaultSoulCost() {
        return SoulConstants.RECALL_FOCUS_COST;
    }

    @Override
    public int defaultCastDuration() {
        return 20;
    }

    @Override
    public int defaultSpellCooldown() {
        return 200;
    }

    @Override
    public SpellType getSpellType() {
        return SpellType.NECROMANCY;
    }

    @Override
    public List<Enchantment> acceptedEnchantments() {
        return List.of();
    }

    @Override
    public void useSpell(ServerLevel level, LivingEntity caster, ItemStack stack, int castTime, SpellStat stat) {
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }

        ServerPlayer target = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player || !SoulData.isSoul(other) || SoulData.isPossessing(other)) {
                continue;
            }
            double distance = other.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                target = other;
            }
        }

        if (target == null) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.recall_no_target"), true);
            return;
        }

        CorpseEntity corpse = findCorpse(player, target);
        if (corpse == null) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.recall_no_corpse"), true);
            return;
        }

        corpse.teleportTo(player.getX(), player.getY(), player.getZ());
        player.displayClientMessage(Component.translatable("message.goety_revenant.recall_success"), false);
        target.displayClientMessage(Component.translatable("message.goety_revenant.recall_notify",
                player.getDisplayName()), false);
    }

    private static CorpseEntity findCorpse(ServerPlayer caster, ServerPlayer soul) {
        java.util.UUID corpseId = SoulData.getCorpseId(soul);
        ResourceKey<Level> dimension = SoulData.getCorpseDimension(soul);
        if (corpseId == null || dimension == null) {
            return null;
        }
        ServerLevel corpseLevel = caster.server.getLevel(dimension);
        if (corpseLevel == null) {
            return null;
        }
        return corpseLevel.getEntity(corpseId) instanceof CorpseEntity corpse ? corpse : null;
    }
}
