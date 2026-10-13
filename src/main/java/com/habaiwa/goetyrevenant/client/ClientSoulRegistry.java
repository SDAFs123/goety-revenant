package com.habaiwa.goetyrevenant.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 客户端记录**所有**处于回魂灵魂状态的玩家（渲染灵魂外观要用）。 */
public final class ClientSoulRegistry {
    /**
     * **断线清理** —— 这些是静态字段，退出世界后不清干净，
     * 下一个存档/服务器在有效同步包到达之前会继续用旧的 灵魂名单。
     */
    public static void reset() {
        SOULS.clear();
    }

    private static final Set<UUID> SOULS = new HashSet<>();

    private ClientSoulRegistry() {
    }

    public static void set(UUID player, boolean soul) {
        if (soul) {
            SOULS.add(player);
        } else {
            SOULS.remove(player);
        }
    }

    public static boolean isSoul(UUID player) {
        return SOULS.contains(player);
    }
}
