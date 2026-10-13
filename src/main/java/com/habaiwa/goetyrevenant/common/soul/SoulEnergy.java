package com.habaiwa.goetyrevenant.common.soul;

import com.Polarice3.Goety.utils.SEHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;

/**
 * **灵魂能量 = 玩家自己的那一份**（诡厄巫法的灵魂能量，不是我们另开的池子）。
 *
 * <h3>为什么直接用本体的</h3>
 * <p>这样聚晶、法杖、仪式**什么都不用改就能直接用** ——
 * 它们本来就是在花 {@code SEHelper} 里那份能量，我们没必要在旁边再养一个平行的池子
 * （那样反而要处处做转换，还容易对不上）。</p>
 *
 * <h3>上限 = 基础量 + 方舟（+ 图腾）</h3>
 * <p>上限直接读诡厄巫法自己的配置：</p>
 * <ul>
 *   <li>没绑灵魂方舟 → {@code MainConfig.MaxSouls}（默认 <b>10000</b>）</li>
 *   <li>绑了灵魂方舟 → {@code MainConfig.MaxArcaSouls}（默认 <b>100000</b>）</li>
 * </ul>
 * <p>图腾那一档由本体自己叠（它有自己的容器/饰品逻辑），所以这里只要把**基数**读对，
 * 「基础量 + 图腾 + 方舟」这套就是本体原本的算法。</p>
 *
 * <p>全程反射读配置：没装/读不到就用兜底值，绝不因为读配置失败而崩。</p>
 */
public final class SoulEnergy {
    /** 读不到配置时的兜底。 */
    public static final int FALLBACK_MAX = 10000;
    public static final int FALLBACK_ARCA_MAX = 100000;

    private static boolean resolved;
    private static Object baseValue;
    private static Object arcaValue;
    private static Method getter;

    private SoulEnergy() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> config = Class.forName("com.Polarice3.Goety.config.MainConfig");
            Field base = config.getField("MaxSouls");
            Field arca = config.getField("MaxArcaSouls");
            baseValue = base.get(null);
            arcaValue = arca.get(null);
            getter = baseValue.getClass().getMethod("get");
        } catch (Throwable t) {
            baseValue = null;
            arcaValue = null;
            getter = null;
        }
    }

    private static int read(Object value, int fallback) {
        if (value == null || getter == null) {
            return fallback;
        }
        try {
            Object result = getter.invoke(value);
            return result instanceof Integer i ? i : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /**
     * **灵魂体自己的基础值**。灵魂是单独算的，这一万是灵魂自己的底子。
     */
    public static final int SOUL_BASE = 10000;

    /** 当前灵魂能量。 */
    public static int current(ServerPlayer player) {
        return (int) SEHelper.getSESouls(player);
    }

    /**
     * 灵魂能量上限 = **灵魂自己的基础值 + 外物加上去的上限**。
     *
     * <p>这不是「取两者较大的那个」，而是**相加**：</p>
     * <ul>
     *   <li>什么都没有 → <b>10000</b>（灵魂自己的底子）</li>
     *   <li>绑了灵魂方舟 → 10000 + <b>100000</b> = <b>110000</b></li>
     *   <li>再带一枚**染魂钢图腾** → 10000 + 100000 + <b>30000</b> = <b>140000</b></li>
     * </ul>
     */
    public static int max(ServerPlayer player) {
        resolve();
        // 照着本体自己的判断顺序来（SEHelper#getSoulContainer）：
        //   绑了灵魂方舟 → 用方舟那一档
        //   没绑         → 看有没有戴图腾，用图腾自己的容量
        // 图腾的「在哪」和「能装多少」都交给本体自己的 API，
        // 我们不再自己扫背包、也不写死数字。
        // 基数与方舟档都走配置；配置里写 0 = 跟随诡厄巫法本体的配置
        int base = com.habaiwa.goetyrevenant.common.config.RevenantConfig.soulBase > 0 ? com.habaiwa.goetyrevenant.common.config.RevenantConfig.soulBase : read(baseValue, SOUL_BASE);
        int bonus;
        if (SEHelper.getSEActive(player)) {
            bonus = com.habaiwa.goetyrevenant.common.config.RevenantConfig.soulArcaBonus > 0
                    ? com.habaiwa.goetyrevenant.common.config.RevenantConfig.soulArcaBonus
                    : read(arcaValue, FALLBACK_ARCA_MAX);
        } else {
            bonus = totemCapacity(player);
        }
        // 强化槽里的「魂涌 / 噬灵 / 链雷」都在改这个上限
        return Math.max(1, (int) ((base + bonus) * com.habaiwa.goetyrevenant.common.body.UpgradeSlots.soulMaxFactor(player)));
    }

    /**
     * 戴在身上的图腾能给多少上限。
     *
     * <p>用的是本体自己的两个入口：</p>
     * <ul>
     *   <li>{@code TotemFinder.FindTotem} —— 本体找图腾的官方顺序：
     *       <b>Curios → 副手 → 快捷栏 0~9</b>，判定是 `instanceof ITotem`</li>
     *   <li>{@code ITotem.maximumSouls} / {@code getMaxSouls} —— 图腾自己报容量</li>
     * </ul>
     */
    public static int totemCapacity(ServerPlayer player) {
        try {
            net.minecraft.world.item.ItemStack totem =
                    com.Polarice3.Goety.utils.TotemFinder.FindTotem(player);
            if (totem == null || totem.isEmpty()) {
                return 0;
            }
            int max = com.Polarice3.Goety.api.items.magic.ITotem.maximumSouls(totem);
            if (max <= 0 && totem.getItem() instanceof com.Polarice3.Goety.api.items.magic.ITotem item) {
                // 刚拿到手、NBT 还没铺开的图腾
                max = item.getMaxSouls();
            }
            return Math.max(0, max);
        } catch (Throwable ignored) {
            return 0;
        }
    }



    /** 当前 / 上限（0~1），给 HUD 画条用。 */
    public static float ratio(ServerPlayer player) {
        int max = max(player);
        if (max <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, current(player) / (float) max));
    }

    /** 是否有灵魂方舟（客户端没法查，只在服务端用）。 */
    public static boolean boundToArca(ServerPlayer player) {
        return SEHelper.getArcaBlock(player) != null;
    }
}
