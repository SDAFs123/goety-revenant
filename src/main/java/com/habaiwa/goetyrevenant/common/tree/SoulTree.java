package com.habaiwa.goetyrevenant.common.tree;

import com.habaiwa.goetyrevenant.common.embed.BodyModData;
import com.habaiwa.goetyrevenant.common.embed.OrganType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * **魂树** —— 玩家本身就是一棵树。
 *
 * <h3>设定</h3>
 * <pre>
 *   根  回魂        灵化/回魂的底子
 *   干  内核        主干
 *   枝  嵌入位置     一级枝 = 六窍，往下越分越细
 *   叶  嵌入的聚晶
 *   花  绽放
 *   果  源（第七阶）
 * </pre>
 *
 * <h3>枝干层级</h3>
 * <table>
 *   <tr><td>主干</td><td>1</td></tr>
 *   <tr><td>一级枝</td><td>6</td></tr>
 *   <tr><td>二级枝</td><td>18</td></tr>
 *   <tr><td>三级枝</td><td>36</td></tr>
 *   <tr><td>四级枝</td><td>72</td></tr>
 *   <tr><td>末梢</td><td>72</td></tr>
 * </table>
 * <p>合计 205 节。</p>
 *
 * <h3>序列（七阶）</h3>
 * <p>序列就是树的**长势**。一阶只是一株幼苗，每升一阶树能多长一节、
 * 多开一个嵌入位；第七阶的名字是「源」。</p>
 */
public final class SoulTree {
    /** 序列阶数上限。 */
    public static final int MAX_STAGE = 7;

    /** 各层级的枝数：主干 / 一级 / 二级 / 三级 / 四级 / 末梢。 */
    public static final int[] LEVEL_COUNT = {1, 6, 18, 36, 72, 72};

    /** 层级名（供界面显示）。 */
    private static final String[] LEVEL_NAME = {"主干", "一级枝", "二级枝", "三级枝", "四级枝", "末梢"};

    /** 七阶的名字 —— 烛守 → 回声，之后晋升「源」。 */
    private static final String[] STAGE_NAME = {
            "烛守", "拾荒", "缄口", "追影", "筑墙", "归墟", "回声"
    };

    // 每阶的默认灵魂温度在 SoulTemperature.defaultOf —— 只留一份，免得两处对不上

    private SoulTree() {
    }

    /* ------------------------------------------------------------------ */

    /** 这一层级的枝共几节。 */
    public static int countOf(int level) {
        return level >= 0 && level < LEVEL_COUNT.length ? LEVEL_COUNT[level] : 0;
    }

    /** 层级名。 */
    public static String levelName(int level) {
        return level >= 0 && level < LEVEL_NAME.length ? LEVEL_NAME[level] : "枝";
    }

    /** 整棵树一共多少节。 */
    public static int totalNodes() {
        int sum = 0;
        for (int n : LEVEL_COUNT) {
            sum += n;
        }
        return sum;
    }

    /* ------------------------------------------------------------------ */

    /**
     * **当前序列（1~7）**。
     *
     * <p>序列 = 已开窍数 + 1：一窍未开时是一株幼苗（一阶），
     * 六窍全开是六阶（灵环），第七阶「源」留给献祭。 </p>
     */
    public static int stage(Player player) {
        if (player == null || !BodyModData.isUnlocked(player)) {
            return -1;   // 还没跳井 —— 树还没醒
        }
        // **跳进哭泣之井 = 零阶**。往上怎么升阶由序列设定决定（还没发过来），
        // 所以正常路径下这里如实返回零阶，不做任何推断。
        //
        // 唯一的例外是【测试用的阶数】：指令 grdebug tree 会写这个键，
        // 写了就用它。正常玩家里这个键根本不存在 → 一律走上面的 0。
        // （这样"序列晋升"那套机制一个字没改 —— 它没设计好之前，
        //   树就该停在幼苗，而不是被我随便推断出一个阶数。）
        int test = player.getPersistentData().getInt("GRTreeStageTest");
        return Math.max(0, Math.min(MAX_STAGE, test));
    }

    /**
     * 名字表（烛守 / 拾荒 / 缄口 / 追影 / 筑墙 / 归墟 / 回声）。
     *
     * <p>⚠️ 这七个名字你给过，但它们**怎么排列、怎么获得**还没定 ——
     * 所以这里只保留表，界面暂时不显示，等序列设定。</p>
     */
    public static String stageName(int stage) {
        if (stage < 0) {
            return "未醒";
        }
        if (stage <= 0) {
            return "幼苗";
        }
        return STAGE_NAME[Math.min(MAX_STAGE, stage) - 1];
    }

    /** 序列的显示组件。 */
    public static Component stageLabel(int stage) {
        return Component.literal("第 " + Math.max(1, stage) + " 阶 · " + stageName(stage));
    }

    /**
     * **这一刻树长出了多少节**。
     *
     * <p>一阶只有主干和一株幼苗；每升一阶，下一层级的枝开始生长。
     * 用一条平滑的曲线，免得升阶时"啪"地一下全冒出来。</p>
     */
    public static int grownNodes(int stage) {
        if (stage <= 0) {
            return 0;
        }
        int total = totalNodes();
        // 第 1 阶 = 主干 + 一级枝；之后逐层推进
        float t = Math.min(1.0F, stage / (float) MAX_STAGE);
        return Math.max(1, Math.round(total * t * t));   // 二次曲线：前期慢、后期快
    }

    /**
     * **一级枝（六窍）在当前序列下开了几个**。
     *
     * <p>一阶只有 1 个嵌入位，每升一阶多一个 —— 这就是"序列限制嵌入位数"。</p>
     */
    public static int unlockedSlots(int stage) {
        // 嵌入位都长在**主干**上，跳井睁开眼就能用；
        // 序列怎么限制它，等序列设定发过来再定。
        return stage < 0 ? 0 : OrganType.values().length;
    }

    /** 第 index 个嵌入位（按 OrganType 顺序）是否已被序列解锁。 */
    public static boolean slotUnlocked(int stage, int index) {
        return index >= 0 && index < unlockedSlots(stage);
    }

    /** 按 OrganType 顺序取第 index 个窍。 */
    public static OrganType organAt(int index) {
        OrganType[] all = OrganType.values();
        return index >= 0 && index < all.length ? all[index] : null;
    }
}
