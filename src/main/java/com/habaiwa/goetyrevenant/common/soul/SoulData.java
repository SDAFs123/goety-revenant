package com.habaiwa.goetyrevenant.common.soul;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/** 玩家灵魂状态的读写（存在玩家 persistent data 上，不走新资源）。 */
public final class SoulData {
    private SoulData() {
    }

    private static CompoundTag tag(Player player) {
        return player.getPersistentData();
    }

    public static boolean isSoul(Player player) {
        return tag(player).getBoolean(SoulConstants.KEY_ACTIVE);
    }

    /** 灵魂游离的模式：{@link SoulConstants#MODE_DEATH} / {@link SoulConstants#MODE_DRIFT}。 */
    public static String getSoulMode(Player player) {
        String mode = tag(player).getString(SoulConstants.KEY_SOUL_MODE);
        return mode.isEmpty() ? SoulConstants.MODE_DEATH : mode;
    }

    public static void setSoulMode(Player player, String mode) {
        tag(player).putString(SoulConstants.KEY_SOUL_MODE, mode);
    }

    /** 是不是**主动出窍**那一种（不追踪尸体、回去是残破躯体）。 */
    public static boolean isDrifting(Player player) {
        return SoulConstants.MODE_DRIFT.equals(getSoulMode(player));
    }

    public static void setSoul(Player player, boolean active) {
        tag(player).putBoolean(SoulConstants.KEY_ACTIVE, active);
        if (!active) {
            clear(player);
        }
    }

    public static int getTicks(Player player) {
        return tag(player).getInt(SoulConstants.KEY_TICKS);
    }

    public static void setTicks(Player player, int ticks) {
        tag(player).putInt(SoulConstants.KEY_TICKS, ticks);
    }

    /**
     * 灵魂能量 —— **就是诡厄巫法本体那一份**（{@code SEHelper}），不是我们另开的池子。
     *
     * <p>所以灵魂状态下聚晶/法杖能直接花它，图腾和灵魂方舟也照样把上限往上加。</p>
     */
    public static int getEnergy(Player player) {
        return (int) com.Polarice3.Goety.utils.SEHelper.getSESouls(player);
    }

    public static void setEnergy(Player player, int energy) {
        com.Polarice3.Goety.utils.SEHelper.setSESouls(player, Math.max(0, energy));
    }

    /** 灵魂能量耗尽 → 这次是真的死了。 */
    public static boolean isFinal(Player player) {
        return tag(player).getBoolean(SoulConstants.KEY_FINAL);
    }

    public static void setFinal(Player player, boolean value) {
        tag(player).putBoolean(SoulConstants.KEY_FINAL, value);
    }

    public static void setCorpse(Player player, UUID corpseId, ResourceKey<Level> dimension) {
        tag(player).putUUID(SoulConstants.KEY_CORPSE_UUID, corpseId);
        tag(player).putString(SoulConstants.KEY_CORPSE_DIM, dimension.location().toString());
    }

    @Nullable
    public static UUID getCorpseId(Player player) {
        return tag(player).hasUUID(SoulConstants.KEY_CORPSE_UUID)
                ? tag(player).getUUID(SoulConstants.KEY_CORPSE_UUID) : null;
    }

    @Nullable
    public static ResourceKey<Level> getCorpseDimension(Player player) {
        String raw = tag(player).getString(SoulConstants.KEY_CORPSE_DIM);
        if (raw.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? null : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
    }

    public static void setPreviousGameMode(Player player, GameType mode) {
        tag(player).putInt(SoulConstants.KEY_PREV_MODE, mode.getId());
    }

    public static GameType getPreviousGameMode(Player player) {
        return GameType.byId(tag(player).getInt(SoulConstants.KEY_PREV_MODE));
    }

    public static void setPossession(Player player, String typeId, int ticks, boolean undead, float attunement) {
        CompoundTag t = tag(player);
        t.putString(SoulConstants.KEY_POSSESS_TYPE, typeId);
        t.putInt(SoulConstants.KEY_POSSESS_TICKS, ticks);
        t.putBoolean(SoulConstants.KEY_POSSESS_UNDEAD, undead);
        t.putFloat(SoulConstants.KEY_ATTUNEMENT, attunement);
    }

    public static String getPossessType(Player player) {
        return tag(player).getString(SoulConstants.KEY_POSSESS_TYPE);
    }

    public static boolean isPossessing(Player player) {
        return !tag(player).getString(SoulConstants.KEY_POSSESS_TYPE).isEmpty();
    }

    public static int getPossessTicks(Player player) {
        return tag(player).getInt(SoulConstants.KEY_POSSESS_TICKS);
    }

    public static void setPossessTicks(Player player, int ticks) {
        tag(player).putInt(SoulConstants.KEY_POSSESS_TICKS, ticks);
    }

    public static boolean isPossessingUndead(Player player) {
        return tag(player).getBoolean(SoulConstants.KEY_POSSESS_UNDEAD);
    }

    public static float getAttunement(Player player) {
        return tag(player).getFloat(SoulConstants.KEY_ATTUNEMENT);
    }

    /** 自身身体的契合度（活着平时是 100%，死后被召回会掉，随时间慢慢补回来）。 */
    public static float getSelfAttunement(Player player) {
        CompoundTag t = tag(player);
        if (!t.contains(SoulConstants.KEY_SELF_ATTUNEMENT)) {
            return SoulConstants.SELF_ATTUNEMENT_START;
        }
        return t.getFloat(SoulConstants.KEY_SELF_ATTUNEMENT);
    }

    public static void setSelfAttunement(Player player, float value) {
        float clamped = Math.max(SoulConstants.SELF_ATTUNEMENT_MIN, Math.min(SoulConstants.SELF_ATTUNEMENT_START, value));
        tag(player).putFloat(SoulConstants.KEY_SELF_ATTUNEMENT, clamped);
    }

    public static void penalizeSelfAttunement(Player player, float amount) {
        setSelfAttunement(player, getSelfAttunement(player) - amount);
    }

    /** 随时间慢慢补回来，封顶 100%。 */
    public static void recoverSelfAttunement(Player player) {
        float current = getSelfAttunement(player);
        if (current < SoulConstants.SELF_ATTUNEMENT_START) {
            setSelfAttunement(player, current + SoulConstants.ATTUNEMENT_RECOVER_AMOUNT);
        }
    }

    /** 上一个 tick 的灵魂能量值：用于把亡灵身体的恢复速度压到 25%。 */
    public static int getLastSe(Player player) {
        return tag(player).getInt(SoulConstants.KEY_LAST_SE);
    }

    public static void setLastSe(Player player, int value) {
        tag(player).putInt(SoulConstants.KEY_LAST_SE, value);
    }

    /** 结束附身（清除附身字段，但保留灵魂状态本身）。 */
    public static void clearPossession(Player player) {
        CompoundTag t = tag(player);
        t.remove(SoulConstants.KEY_POSSESS_TYPE);
        t.remove(SoulConstants.KEY_POSSESS_TICKS);
        t.remove(SoulConstants.KEY_POSSESS_UNDEAD);
        t.remove(SoulConstants.KEY_ATTUNEMENT);
    }

    private static void clear(Player player) {
        CompoundTag t = tag(player);
        t.remove(SoulConstants.KEY_TICKS);
        t.remove(SoulConstants.KEY_SOUL_MODE);
        t.remove(SoulConstants.KEY_ENERGY);
        t.remove(SoulConstants.KEY_FINAL);
        t.remove(SoulConstants.KEY_CORPSE_UUID);
        t.remove(SoulConstants.KEY_CORPSE_DIM);
        t.remove(SoulConstants.KEY_PREV_MODE);
        clearPossession(player);
    }
}
