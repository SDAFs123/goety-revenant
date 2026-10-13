package com.habaiwa.goetyrevenant.common.compat;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.possession.Attunement;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 与其它诡厄巫法附属的联动。
 *
 * <p>两家都是**闭源/半闭源**的，所以联动一律走「软依赖 + 注册名」：
 * 缺席时什么都不做，装上了就多几具可附身的身体、并给它们各自的契合度。</p>
 *
 * <ul>
 *   <li><b>诡厄巫法：启示录</b>（Goety: Revelation）—— modId `goety_revelation`</li>
 *   <li><b>诡厄巫法：觉醒</b>（Goety: Awaken）—— modId `goetyawaken`</li>
 * </ul>
 */
public final class RevenantCompat {
    public static final String REVELATION = "goety_revelation";
    public static final String AWAKEN = "goetyawaken";

    private static final Logger LOGGER = LogUtils.getLogger();

    private RevenantCompat() {
    }

    public static boolean isLoaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }

    public static boolean revelation() {
        return isLoaded(REVELATION);
    }

    public static boolean awaken() {
        return isLoaded(AWAKEN);
    }

    public static void init() {
        if (revelation()) {
            // 启示录：凋灵仆从 / 幻翼仆从 —— 越"死"的身体越不合身
            Attunement.registerCompat("goety_revelation:wither_servant", 0.35F);
            Attunement.registerCompat("goety_revelation:phantom_servant", 0.25F);
            Attunement.registerCompat("goety_revelation:summon_apollyon", 0.15F);
            LOGGER.info("[Goety: Revenant] 联动「诡厄巫法：启示录」已启用");
        }
        if (awaken()) {
            // 觉醒：按身体类型给的契合度（亡灵仆从普遍不高，越强越不合身）
            Attunement.registerCompat("goetyawaken:wight_servant", 0.50F);
            Attunement.registerCompat("goetyawaken:zombie_royal_guard_servant", 0.55F);
            Attunement.registerCompat("goetyawaken:zombie_mountaineer_servant", 0.55F);
            Attunement.registerCompat("goetyawaken:zombie_neollager_servant", 0.55F);
            Attunement.registerCompat("goetyawaken:zombie_darkguard", 0.55F);
            Attunement.registerCompat("goetyawaken:skeleton_vanguard", 0.45F);
            Attunement.registerCompat("goetyawaken:skeleton_tower_guard_servant", 0.45F);
            Attunement.registerCompat("goetyawaken:giant_servant", 0.45F);
            Attunement.registerCompat("goetyawaken:pale_golem_servant", 0.40F);
            Attunement.registerCompat("goetyawaken:wither_servant", 0.35F);
            Attunement.registerCompat("goetyawaken:obsidian_monolith_servant", 0.30F);
            Attunement.registerCompat("goetyawaken:seismic_monolith", 0.30F);
            LOGGER.info("[Goety: Revenant] 联动「诡厄巫法：觉醒」已启用");
        }
        if (!revelation() && !awaken()) {
            LOGGER.info("[Goety: Revenant] 未检测到启示录 / 觉醒，按原版 + 本体运行");
        }
    }
}
