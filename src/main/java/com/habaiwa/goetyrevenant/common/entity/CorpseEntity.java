package com.habaiwa.goetyrevenant.common.entity;

import com.habaiwa.goetyrevenant.common.tooltip.HighlightHook;

import java.util.Collections;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 玩家尸体。
 *
 * <p>死亡时玩家不会真的死掉：身体留在这里（保留皮肤与全部装备），
 * 灵魂被弹出去找身体。背包索引沿用原版玩家布局（0-35 主背包 / 36-39 护甲 / 40 副手），
 * 所以「附身自己的身体」= 原样把东西还回去。</p>
 */
public class CorpseEntity extends LivingEntity {
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    /**
     * 皮肤属性（base64 的 value / signature）。
     *
     * <p><b>这就是「尸体丢皮肤」的根因</b>：客户端的 `getInsecureSkinLocation(GameProfile)` 只有
     * 在 profile 带 `textures` 属性时才能解析出玩家皮肤，否则一律回退成默认皮肤。
     * 所以死亡时要把皮肤属性一起存下来（并同步给客户端）。</p>
     */
    private static final EntityDataAccessor<String> DATA_SKIN_VALUE =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN_SIGNATURE =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);

    /** 与玩家背包同布局：36 主 + 4 护甲(feet/legs/chest/head) + 1 副手。 */
    public static final int SIZE = 41;
    public static final int ARMOR_START = 36;
    public static final int OFFHAND_INDEX = 40;

    private final NonNullList<ItemStack> corpseItems = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    @Nullable
    private UUID ownerUUID;
    private int storedXp;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER_NAME, "");
        this.entityData.define(DATA_SKIN_VALUE, "");
        this.entityData.define(DATA_SKIN_SIGNATURE, "");
    }

    /** 把死亡玩家的一切搬进尸体。 */
    public static CorpseEntity of(ServerPlayer player) {
        CorpseEntity corpse = new CorpseEntity(com.habaiwa.goetyrevenant.init.ModEntities.CORPSE.get(), player.level());
        corpse.ownerUUID = player.getUUID();
        corpse.entityData.set(DATA_OWNER_NAME, player.getGameProfile().getName());
        // ★ 把皮肤属性一起带走 —— 只带 UUID/名字的话客户端只能查到默认皮肤
        com.mojang.authlib.properties.Property textures =
                player.getGameProfile().getProperties().get("textures").stream().findFirst().orElse(null);
        if (textures != null) {
            corpse.entityData.set(DATA_SKIN_VALUE, textures.getValue());
            corpse.entityData.set(DATA_SKIN_SIGNATURE, textures.getSignature() == null ? "" : textures.getSignature());
        }
        corpse.storedXp = player.totalExperience;
        corpse.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        corpse.setYHeadRot(player.getYHeadRot());

        for (int i = 0; i < 36; i++) {
            corpse.corpseItems.set(i, player.getInventory().items.get(i).copy());
        }
        for (int i = 0; i < 4; i++) {
            corpse.corpseItems.set(ARMOR_START + i, player.getInventory().armor.get(i).copy());
        }
        corpse.corpseItems.set(OFFHAND_INDEX, player.getInventory().offhand.get(0).copy());

        player.getInventory().clearContent();
        return corpse;
    }

    /** 把尸体里的东西全部塞回玩家（塞不下就掉在原地）。 */
    public void giveItemsTo(Player player) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = this.corpseItems.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (i < 36) {
                player.getInventory().items.set(i, stack);
            } else if (i < OFFHAND_INDEX) {
                player.getInventory().armor.set(i - ARMOR_START, stack);
            } else {
                player.getInventory().offhand.set(0, stack);
            }
            this.corpseItems.set(i, ItemStack.EMPTY);
        }
        player.giveExperiencePoints(this.storedXp);
        this.storedXp = 0;
    }

    /** 尸体的名字要说明是**谁的**尸体（皮肤相同时也能分辨）。 */
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty()) {
            return net.minecraft.network.chat.Component.translatable("entity.goety_revenant.corpse");
        }
        return net.minecraft.network.chat.Component.translatable("entity.goety_revenant.corpse.of", owner);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public String getOwnerName() {
        return this.entityData.get(DATA_OWNER_NAME);
    }

    /** 玩家皮肤的 base64 value（可能为空 —— 离线模式/正版验证失败时）。 */
    public String getSkinValue() {
        return this.entityData.get(DATA_SKIN_VALUE);
    }

    public String getSkinSignature() {
        return this.entityData.get(DATA_SKIN_SIGNATURE);
    }

    public boolean isOwner(Player player) {
        return this.ownerUUID != null && this.ownerUUID.equals(player.getUUID());
    }

    /**
     * 灵魂视角看到自己身体发光（发光描边）。
     * 是否点亮由 {@code HighlightHook} 决定 —— 默认服务端恒为 false，客户端只在本人视角为 true。
     */
    @Override
    public boolean isCurrentlyGlowing() {
        return super.isCurrentlyGlowing()
                || com.habaiwa.goetyrevenant.common.tooltip.HighlightHook.isHighlighted(this);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.isOwner(player)
                && !com.habaiwa.goetyrevenant.common.soul.SoulData.isSoul(player)) {
            this.giveItemsTo(player);
            this.discard();
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ===== 无敌 / 不可推动：尸体只能被附身或自己取回 =====

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    // ===== LivingEntity 抽象实现 =====

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.unmodifiableList(this.corpseItems.subList(ARMOR_START, OFFHAND_INDEX));
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> this.corpseItems.get(0);
            case OFFHAND -> this.corpseItems.get(OFFHAND_INDEX);
            case FEET -> this.corpseItems.get(ARMOR_START);
            case LEGS -> this.corpseItems.get(ARMOR_START + 1);
            case CHEST -> this.corpseItems.get(ARMOR_START + 2);
            case HEAD -> this.corpseItems.get(ARMOR_START + 3);
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot) {
            case MAINHAND -> this.corpseItems.set(0, stack);
            case OFFHAND -> this.corpseItems.set(OFFHAND_INDEX, stack);
            case FEET -> this.corpseItems.set(ARMOR_START, stack);
            case LEGS -> this.corpseItems.set(ARMOR_START + 1, stack);
            case CHEST -> this.corpseItems.set(ARMOR_START + 2, stack);
            case HEAD -> this.corpseItems.set(ARMOR_START + 3, stack);
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
        tag.putString("OwnerName", getOwnerName());
        tag.putInt("StoredXp", this.storedXp);
        CompoundTag items = new CompoundTag();
        for (int i = 0; i < SIZE; i++) {
            if (!this.corpseItems.get(i).isEmpty()) {
                items.put("slot" + i, this.corpseItems.get(i).save(new CompoundTag()));
            }
        }
        tag.put("Items", items);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
        this.entityData.set(DATA_OWNER_NAME, tag.getString("OwnerName"));
        this.storedXp = tag.getInt("StoredXp");
        CompoundTag items = tag.getCompound("Items");
        for (int i = 0; i < SIZE; i++) {
            String key = "slot" + i;
            if (items.contains(key)) {
                this.corpseItems.set(i, ItemStack.of(items.getCompound(key)));
            }
        }
    }
}
