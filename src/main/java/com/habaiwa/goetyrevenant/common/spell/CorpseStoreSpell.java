package com.habaiwa.goetyrevenant.common.spell;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.SpellType;
import com.Polarice3.Goety.common.magic.SpellStat;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.habaiwa.goetyrevenant.common.soul.SoulConstants;
import com.habaiwa.goetyrevenant.init.ModEntities;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.AABB;

/**
 * 纳尸：把尸体收进聚晶里带着走，再放出来。
 *
 * <p>用于「尸体离灵魂太远」或者「想搬尸体去灵魂方舟」的场合，
 * 也是没有 Carry On 时的替代搬运手段。</p>
 */
public class CorpseStoreSpell implements ISpell {
    @Override
    public int defaultSoulCost() {
        return SoulConstants.STORE_FOCUS_COST;
    }

    @Override
    public int defaultCastDuration() {
        return 10;
    }

    @Override
    public int defaultSpellCooldown() {
        return 40;
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
        ItemStack focus = IWand.getFocus(stack);
        if (focus.isEmpty()) {
            return;
        }
        CompoundTag tag = focus.getOrCreateTag();

        // 已有尸体 → 放出来
        if (tag.contains(SoulConstants.KEY_STORED_CORPSE)) {
            CompoundTag data = tag.getCompound(SoulConstants.KEY_STORED_CORPSE);
            tag.remove(SoulConstants.KEY_STORED_CORPSE);
            CorpseEntity corpse = ModEntities.CORPSE.get().create(level);
            if (corpse != null) {
                corpse.load(data);
                corpse.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                level.addFreshEntity(corpse);
                player.displayClientMessage(Component.translatable("message.goety_revenant.corpse_released"), true);
            }
            return;
        }

        // 收纳最近的尸体
        CorpseEntity corpse = nearest(player);
        if (corpse == null) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.corpse_none"), true);
            return;
        }
        CompoundTag data = new CompoundTag();
        corpse.saveWithoutId(data);
        tag.put(SoulConstants.KEY_STORED_CORPSE, data);
        corpse.discard();
        player.displayClientMessage(Component.translatable("message.goety_revenant.corpse_stored"), true);
    }

    private static CorpseEntity nearest(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(SoulConstants.STORE_RANGE);
        List<CorpseEntity> list = player.level().getEntitiesOfClass(CorpseEntity.class, box);
        return list.isEmpty() ? null : list.get(0);
    }
}
