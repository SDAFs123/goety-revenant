package com.habaiwa.goetyrevenant.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** 客户端记录"某玩家附身了哪个生物"（渲染成生物时用）。空 = 没有。 */
public final class PossessedAppearance {
    private static final Map<UUID, ResourceLocation> POSSESSED = new HashMap<>();
    /** 附身生物自己的存档数据 —— 变体都在里面。 */
    private static final Map<UUID, net.minecraft.nbt.CompoundTag> DATA = new HashMap<>();

    private PossessedAppearance() {
    }

    public static void set(UUID player, String entityTypeId, net.minecraft.nbt.CompoundTag data) {
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            POSSESSED.remove(player);
            DATA.remove(player);
            return;
        }
        POSSESSED.put(player, ResourceLocation.tryParse(entityTypeId));
        if (data != null && !data.isEmpty()) {
            DATA.put(player, data);
        } else {
            DATA.remove(player);
        }
    }

    @Nullable
    public static ResourceLocation get(UUID player) {
        return POSSESSED.get(player);
    }

    /** 变体数据（可能为 null）。 */
    @Nullable
    public static net.minecraft.nbt.CompoundTag data(UUID player) {
        return DATA.get(player);
    }
}
