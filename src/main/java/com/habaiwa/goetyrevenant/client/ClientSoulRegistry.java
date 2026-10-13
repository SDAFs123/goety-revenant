package com.habaiwa.goetyrevenant.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 客户端记录**所有**处于回魂灵魂状态的玩家（渲染灵魂外观要用）。 */
public final class ClientSoulRegistry {
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
