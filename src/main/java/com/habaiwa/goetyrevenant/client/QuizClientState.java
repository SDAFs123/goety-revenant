package com.habaiwa.goetyrevenant.client;

import com.habaiwa.goetyrevenant.common.sequence.Sequence;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * 客户端记着七问答问到哪了，外加**本地算的倾向**。
 *
 * <p>题目和打分表都是静态的，所以"现在最像谁"客户端自己算得出来 ——
 * 弹窗的颜色、消散特效的颜色都用它，不用等往返。</p>
 */
public final class QuizClientState {
    private static int index = -1;
    private static boolean running = false;
    private static String sequence = "";
    /** 本地倾向分：每个选项按题目里的表加分。 */
    private static final Map<Sequence, Integer> SCORES = new EnumMap<>(Sequence.class);
    /**
     * **进去之后至少等这么久**（毫秒）才开问答。
     *
     * <p>少了它，玩家跳进去的同一帧题目就糊在脸上 ——
     * 从高空落下来那几秒、和那一片白，等于白给。</p>
     */
    public static final long MIN_WAIT_MS = 3000L;

    /** **进入那片白的时刻**（毫秒）—— 开问答要等够 MIN_WAIT_MS，让人先落地、先看一眼。 */
    private static long enteredAt = 0L;

    /**
     * **玩家自己开口了没有**。
     *
     * <p>进那片白之后**不自动弹界面**：先让他站着看、听耳边那句话，
     * 想开始的时候自己按一下（默认回车）。少了这道，人刚落地题目就糊脸上。</p>
     */
    private static boolean began = false;

    /** **落地之后多久界面自己浮出来**（毫秒）—— 留足看清这片白、听完那句话的时间。 */
    public static final long AUTO_OPEN_MS = 12000L;

    /** 连续站在地上的 tick 数（落地检测要稳，不能看一帧）。 */
    private static int groundTicks = 0;
    /** 缺装备这件事只提示一次。 */
    private static boolean gearNoticed = false;

    /** 玩家按下了"开口"（不想等就提前开始）。 */
    public static void begin() {
        began = true;
    }

    /** 刚进到那片白里（由 QuizRoomClient 在转场到位时调）。 */
    public static void onEnterRoom() {
        enteredAt = System.currentTimeMillis();
        began = false;
        groundTicks = 0;
        gearNoticed = false;
    }

    /** 离开那片白：把低语收起来（下次进来重新说）。 */
    public static void onLeaveRoom() {
        com.habaiwa.goetyrevenant.client.fx.WhisperIntro.reset();
    }

    private QuizClientState() {
    }

    public static void accept(int index, boolean running, String sequence) {
        // **没在答题就把状态清干净** —— 否则上一轮的题号会一直挂着，
        // 新存档、重进世界的时候那些残留会被当成"还在答题"。
        if (!running && index <= 0) {
            QuizClientState.index = -1;
            QuizClientState.running = false;
            QuizClientState.sequence = sequence == null ? "" : sequence;
            SCORES.clear();
            return;
        }
        // 从头开始说明是新的一轮，把上次的分清掉
        if (index == 0 && !QuizClientState.running) {
            SCORES.clear();
            // 觉醒提示也要重置 —— 否则它只在**第一次**觉醒时弹得出来
            awakenedName = "";
            awakenedAt = 0L;
        }
        QuizClientState.index = index;
        QuizClientState.running = running;
        QuizClientState.sequence = sequence == null ? "" : sequence;

        // **刚刚觉醒** —— 记下时刻：屏幕中央要显示几秒（见 AwakenOverlay）
        if (!QuizClientState.sequence.isEmpty() && awakenedName.isEmpty()) {
            awakenedName = QuizClientState.sequence;
            awakenedAt = System.currentTimeMillis();
            // 问答界面收起来，让中央那段提示单独说话
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof com.habaiwa.goetyrevenant.client.screen.SevenQuestionScreen) {
                mc.setScreen(null);
            }
        }
        // **这里不直接开屏。**
        // 开屏统一交给 ensureScreen() —— 它带着"进去满 3 秒 + 真正落地"两道检查。
        // 之前在这里顺手 openScreen()，等于把那两道检查整个绕过，
        // 于是刚进维度、人还在天上，题目就弹出来了。
    }

    /**
     * 每 tick 兜底：该答题却没在答题屏上，就补开一次。
     *
     * <p>跨维度那一瞬间客户端会自己弹加载屏把我们的界面顶掉，
     * 光靠收包时机开屏不稳，所以这里再补一道。</p>
     */
    public static void ensureScreen() {
        if (!running || QuizClientState.index < 0) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 条件一：人得**在这片白里**（还在外面就别开）
        if (!QuizRoomClient.inside()) {
            return;
        }
        // 条件二：进来**至少 3 秒** —— 让人有时间从高空落下来、看一眼这片白。
        // 少这一个，跳进去的同一帧题目就糊脸上了，白空间等于白给。
        if (System.currentTimeMillis() - enteredAt < MIN_WAIT_MS) {
            return;
        }
        // 条件三：**稳稳落到地上** —— 只要求"连续 6 tick 都踩在地上"。
        //
        // 这里曾经多写了一个"竖直速度接近 0"，那是个错误的物理直觉：
        // 玩家站在地上时 y 速度**不是** 0，它一直是重力给的那个值（约 -0.0784），
        // 每 tick 被重力往下拉、再被地面顶回来。于是那个条件永远不成立，
        // groundTicks 永远攒不到 6 —— 那句话一次都没响过，界面也一直不弹。
        if (mc.player.onGround()) {
            groundTicks++;
        } else {
            groundTicks = 0;
            return;
        }
        if (groundTicks < 6) {
            return;
        }
        // 落地之后先让他看一会儿，那句话才从耳边浮起来
        long since = System.currentTimeMillis() - enteredAt;
        if (since > 1600L) {
            // **戴着莫测之眼才听得见那句话** —— 照 Enigmatic Legacy 那边的规矩：
            // 莫测之眼的画外音（assets/enigmaticlegacy/sounds/quote/*.ogg）
            // 只说给戴着它的人听。我们这段低语同理。
            // 需要 enigmaticlegacy:cursed_ring + enigmaticlegacy:enigmatic_eye_active
            // 两件都在身上（背包/副手/Curios 都算）；没装那个模组时自动放行。
            if (com.habaiwa.goetyrevenant.common.compat.LegacyCompat.hasGear(mc.player)) {
                com.habaiwa.goetyrevenant.client.fx.WhisperIntro.trigger();
            } else if (!gearNoticed) {
                // 临时诊断：戴的东西不全时明说缺哪一件（免得只看到"什么都没发生"）
                gearNoticed = true;
                boolean ring = com.habaiwa.goetyrevenant.common.compat.LegacyCompat.hasRing(mc.player);
                boolean eye = com.habaiwa.goetyrevenant.common.compat.LegacyCompat.hasEye(mc.player);
                if (mc.player != null) {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "[回魂] 听得见那句话需要两件都戴着 —— 七咒之戒: " + ring
                                    + "  ·  莫测之眼: " + eye), false);
                }
            }
        }
        // **界面自己浮现，但等足够久** —— 给玩家看完这片白、听完那句话的时间：
        //   1.6s  那句话浮起来（音频 6.8 秒）
        //   8.5s  音频放完
        //  12.0s  界面自己浮现（不想等就按回车提前开始）
        if (!began && since < AUTO_OPEN_MS) {
            return;
        }
        if (!(mc.screen instanceof com.habaiwa.goetyrevenant.client.screen.SevenQuestionScreen)) {
            mc.setScreen(new com.habaiwa.goetyrevenant.client.screen.SevenQuestionScreen());
        }
    }

    /* ---- 觉醒提示（中央显示几秒） ---- */

    private static long awakenedAt = 0L;
    private static String awakenedName = "";
    /** 中央提示总共显示多久。 */
    public static final long AWAKEN_TOTAL_MS = 4200L;

    /** 现在还在显示觉醒提示吗。 */
    public static boolean awakening() {
        return awakenedAt > 0L && System.currentTimeMillis() - awakenedAt < AWAKEN_TOTAL_MS;
    }

    /** 觉醒提示已经过去多久（毫秒）。 */
    public static long awakenElapsed() {
        return awakenedAt == 0L ? -1L : System.currentTimeMillis() - awakenedAt;
    }

    /** 被看中的那条序列的名字（中文）。 */
    public static String awakenedName() {
        return awakenedName;
    }

    public static void openScreen() {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new com.habaiwa.goetyrevenant.client.screen.SevenQuestionScreen());
    }

    /** 玩家选了一个选项 —— 按它的倾向加分。 */
    public static void addScore(Map<Sequence, Integer> toward) {
        for (Map.Entry<Sequence, Integer> e : toward.entrySet()) {
            SCORES.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    /** **现在的倾向**：分最高的那条；一题都没答返回 null。 */
    @Nullable
    public static Sequence leaning() {
        if (SCORES.isEmpty()) {
            return null;
        }
        Sequence best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Sequence s : Sequence.values()) {
            int score = SCORES.getOrDefault(s, 0);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    public static int index() {
        return index;
    }

    public static boolean running() {
        return running;
    }

    public static String sequence() {
        return sequence;
    }
}
