package com.habaiwa.goetyrevenant.client.screen;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * **和 Goety: Astrology 的联动** —— 把"当空之宫"接到魂树界面上。
 *
 * <h3>它那边的接口（照抄它的公开签名）</h3>
 * <pre>
 *   Constellations.inSeason(Level)          → ZodiacSign   当空的是哪一座
 *   Constellations.nodes(long, ZodiacSign)  → Vec3[]       那一座的星点在天空的位置
 *   ConstellationBlessing.onPlayerTick(...)                它的庇佑（它自己管，我们不动）
 * </pre>
 *
 * <h3>为什么走反射</h3>
 * <p>它是<b>可选</b>的联动对象：整合包里没有它的时候，这个模组也得能跑。
 * 编译期依赖会让"没装"变成启动崩溃，所以这里全部用反射，
 * 出错就安静地返回 null —— 界面上那行"当空"直接不显示。</p>
 *
 * <h3>为什么不缓存太久</h3>
 * <p>星象是按世界时间走的，隔一会儿会换一座。所以结果缓存 <b>20 秒</b>，
 * 既不会每帧反射一次，也不会一直显示过期的星座。</p>
 */
public final class AstrologyCompat {
    private static final String CONSTELLATIONS = "com.example.goetyastrology.astrology.Constellations";
    private static final String ZODIAC = "com.example.goetyastrology.astrology.ZodiacSign";

    private static final long CACHE_MS = 20_000L;

    private static long cachedAt = 0L;
    private static String cachedName;
    private static String cachedLore;
    private static Vec3[] cachedNodes;

    private AstrologyCompat() {
    }

    /** 模组在不在。 */
    public static boolean loaded() {
        try {
            Class.forName(CONSTELLATIONS);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 当空那一座的中文名（null = 没装 / 读不到）。 */
    public static String signName(Level level) {
        refresh(level);
        return cachedName;
    }

    /** 那一座的描述（就是它 lang 里的 lore）。 */
    public static String signLore(Level level) {
        refresh(level);
        return cachedLore;
    }

    /** 那一座的星点（天空中的位置）—— 界面拿它画星线。 */
    public static Vec3[] signNodes(Level level) {
        refresh(level);
        return cachedNodes;
    }

    /* ------------------------------------------------------------------ */

    private static void refresh(Level level) {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MS) {
            return;
        }
        cachedAt = now;
        cachedName = null;
        cachedLore = null;
        cachedNodes = null;
        if (level == null) {
            return;
        }
        try {
            Class<?> constellations = Class.forName(CONSTELLATIONS);
            Object sign = constellations.getMethod("inSeason", Level.class).invoke(null, level);
            if (!(sign instanceof Enum<?> zodiac)) {
                return;
            }
            String key = "constellation.goety_astrology." + zodiac.name().toLowerCase(Locale.ROOT);
            cachedName = Component.translatable(key).getString();
            cachedLore = Component.translatable(key + ".lore").getString();
            // 星点：nodes(long, ZodiacSign)
            Class<?> zodiacClass = Class.forName(ZODIAC);
            Method nodes = constellations.getMethod("nodes", long.class, zodiacClass);
            Object arr = nodes.invoke(null, level.getGameTime(), sign);
            if (arr instanceof Vec3[] vecs) {
                cachedNodes = vecs;
            }
        } catch (Throwable ignored) {
            // 没装 / 版本对不上 —— 那就当没有这件事
        }
    }
}
