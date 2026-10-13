package com.habaiwa.goetyrevenant.client.state;

import com.habaiwa.goetyrevenant.common.network.ShenShiSyncMessage;
import java.util.List;

/** 客户端暂存的**神识感知结果**（画面全在客户端画，服务端只说"感知到了什么"）。 */
public final class ClientShenShi {
    private static List<ShenShiSyncMessage.Contact> contacts = List.of();
    private static double radius;
    /** 主动扫视的起始时刻（毫秒），0 = 没在放波纹。 */
    private static long pulseAt;
    /** 收到过任何一次感知结果没有（没开窍的人一次都收不到）。 */
    private static boolean active;

    private ClientShenShi() {
    }

    public static void accept(ShenShiSyncMessage.Contact[] incoming, boolean pulse, double radiusValue) {
        contacts = List.of(incoming);
        radius = radiusValue;
        // 半径 0 = 服务端说"神识关了"，把整层感知收掉
        active = radiusValue > 0.0D;
        if (pulse) {
            pulseAt = System.currentTimeMillis();
        }
    }

    public static List<ShenShiSyncMessage.Contact> contacts() {
        return contacts;
    }

    public static double radius() {
        return radius;
    }

    public static boolean active() {
        return active;
    }

    /** 距离上一次主动扫视过了多少毫秒（没在放返回 -1）。 */
    public static long pulseAgeMs() {
        return pulseAt == 0L ? -1L : System.currentTimeMillis() - pulseAt;
    }

    /** 客户端自己也能清（比如退出灵魂状态 / 开窍数变化）。 */
    public static void clear() {
        contacts = List.of();
        active = false;
        pulseAt = 0L;
    }
}
