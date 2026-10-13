package com.habaiwa.goetyrevenant.common.soul;

import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 灵魂状态下的寻尸指引：把「身体在哪个方向、多远」算出来。 */
public final class Guidance {
    private static final String[] COMPASS = {"north", "northeast", "east", "southeast",
            "south", "southwest", "west", "northwest"};

    private Guidance() {
    }

    /** 距离（米）。 */
    public static int distance(ServerPlayer player, @Nullable CorpseEntity corpse) {
        if (corpse == null) {
            return -1;
        }
        return (int) Math.sqrt(player.distanceToSqr(corpse));
    }

    /** 八方向罗盘文字。 */
    public static Component direction(ServerPlayer player, @Nullable CorpseEntity corpse) {
        if (corpse == null) {
            return Component.empty();
        }
        double dx = corpse.getX() - player.getX();
        double dz = corpse.getZ() - player.getZ();
        if (dx * dx + dz * dz < 16.0D) {
            return Component.translatable("dir.goety_revenant.here");
        }
        // 以 -Z（北）为 0 度，顺时针
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.round(angle / 45.0D) & 7;
        return Component.translatable("dir.goety_revenant." + COMPASS[index]);
    }

    /**
     * 从玩家指向尸体的**方位角**（度），用 Minecraft 自己的 yaw 约定：
     * `0 = +Z（南）`、`90 = -X（西）`。客户端拿它减掉自己的视角朝向就是屏幕上的相对角度。
     *
     * @return 没有尸体时返回 {@link Float#NaN}
     */
    public static float bearing(ServerPlayer player, @Nullable CorpseEntity corpse) {
        if (corpse == null) {
            return Float.NaN;
        }
        double dx = corpse.getX() - player.getX();
        double dz = corpse.getZ() - player.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** 尸体是否和玩家不在同一个维度。 */
    public static boolean otherDimension(ServerPlayer player, @Nullable CorpseEntity corpse) {
        return corpse != null && corpse.level().dimension() != player.level().dimension();
    }
}
