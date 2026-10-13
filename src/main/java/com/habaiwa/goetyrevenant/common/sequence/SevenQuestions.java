package com.habaiwa.goetyrevenant.common.sequence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * **七问答** —— 跳进哭泣之井之后，水里的东西问你七件事。
 *
 * <h3>它问的不是"你想要什么"</h3>
 * <p>序列的内核是**失去**：他不是天生不被感知，是"被忽视"这件事把他变成了那样的人。
 * 所以七问答问的全是**处境 + 你会怎么做** —— 七个处境，正好对上七条序列各自的那次失去。</p>
 *
 * <h3>怎么匹配</h3>
 * <p>心理测试式的加权：每个选项给一两条序列加分（1~2 分），七题答完，
 * **总分最高的那条**看中你。没有正确答案，也没有更好的人生。</p>
 *
 * <h3>觉醒即锁定</h3>
 * <p>匹配到哪条就是哪条，不能换；七问答只出现一次。</p>
 */
public final class SevenQuestions {
    /** 一个选项：文本 + 它偏袒谁。 */
    public record Choice(String text, Map<Sequence, Integer> toward) {
    }

    /** 一道题。 */
    public record Question(String title, String text, List<Choice> choices) {
    }

    private static final String KEY_INDEX = "GRQuizIndex";

    private SevenQuestions() {
    }

    /* ------------------------------------------------------------------ */
    /*  七道题（七个处境）                                                   */
    /* ------------------------------------------------------------------ */

    private static final List<Question> QUESTIONS = build();

    private static List<Question> build() {
        List<Question> out = new ArrayList<>();

        // 题型混排（像真的心理测试）：投射 / 取舍 / 两难 / 偏好 / 自陈 / 完形
        // 四个选项，每题各偏袒四条不同的序列 —— 七题下来七条序列各被问到 4 次，完全平衡。

        // 【一·取舍】只能带一样出去
        out.add(q("只能带一样出去", "屋子要塌了。你只来得及带一样东西。",
                c("一截还在烧的蜡烛 —— 它还能亮", Sequence.CANDLE_KEEPER, 2),
                c("一个塞满零碎的袋子 —— 总有用得上的", Sequence.SCAVENGER, 2),
                c("一封没有拆开的信 —— 读了就要有反应", Sequence.SILENT, 2),
                c("一双磨破了的鞋 —— 还能走", Sequence.SHADOW, 2)));

        // 【二·投射】看自己的影子
        out.add(q("你的影子", "你看见自己的影子。它——",
                c("挡住了身后的光", Sequence.WALL, 2),
                c("朝来时的地方退", Sequence.RETURN, 2),
                c("比你本人还响", Sequence.ECHO, 2),
                c("比周围所有的影子都亮", Sequence.CANDLE_KEEPER, 2)));

        // 【三·两难】只能拉上来一个
        out.add(q("只能拉上来一个", "水里有人。你只拉得动一个。你先看谁？",
                c("那个手里还攥着东西的", Sequence.SCAVENGER, 2),
                c("那个一直没出声的", Sequence.SILENT, 2),
                c("那个已经游出去很远的", Sequence.SHADOW, 2),
                c("那个正在喊你名字的", Sequence.ECHO, 2)));

        // 【四·偏好】更想听见哪个
        out.add(q("你在听什么", "静下来的时候，你更想听见哪个声音？",
                c("火烧起来的声音", Sequence.CANDLE_KEEPER, 2),
                c("门锁扣上的声音", Sequence.WALL, 2),
                c("有人从很远的地方走回来的声音", Sequence.RETURN, 2),
                c("袋子里零碎碰在一起的声音", Sequence.SCAVENGER, 2)));

        // 【五·取舍】得先放下一样
        out.add(q("先放下哪个", "要往前走，得先放下一样。你放下——",
                c("说话的权利", Sequence.SILENT, 2),
                c("别人对你的看法", Sequence.SHADOW, 2),
                c("你喊过的每一句话", Sequence.ECHO, 2),
                c("你已经砌好的那段墙", Sequence.WALL, 2)));

        // 【六·自陈】别人怎么形容你
        out.add(q("别人会怎么说你", "认识你的人，会怎么形容你？",
                c("他一直在往回走", Sequence.RETURN, 2),
                c("他烧得太旺了", Sequence.CANDLE_KEEPER, 2),
                c("他什么都舍不得扔", Sequence.SCAVENGER, 2),
                c("他从来不开口", Sequence.SILENT, 2)));

        // 【七·完形】走了以后还剩什么
        out.add(q("还剩什么", "你走了以后，你希望还剩什么？",
                c("一串还在响的回声", Sequence.ECHO, 2),
                c("一段别人越不过去的墙", Sequence.WALL, 2),
                c("一串脚印", Sequence.SHADOW, 2),
                c("一条回来的路", Sequence.RETURN, 2)));

        return out;
    }

    private static Question q(String title, String text, Choice... choices) {
        return new Question(title, text, List.of(choices));
    }

    private static Choice c(String text, Sequence sequence, int score) {
        Map<Sequence, Integer> map = new EnumMap<>(Sequence.class);
        map.put(sequence, score);
        return new Choice(text, map);
    }

    private static Choice c(String text, Sequence a, int scoreA, Sequence b, int scoreB) {
        Map<Sequence, Integer> map = new EnumMap<>(Sequence.class);
        map.put(a, scoreA);
        map.put(b, scoreB);
        return new Choice(text, map);
    }

    /* ------------------------------------------------------------------ */
    /*  流程                                                                */
    /* ------------------------------------------------------------------ */

    public static int count() {
        return QUESTIONS.size();
    }

    public static Question question(int index) {
        return index >= 0 && index < QUESTIONS.size() ? QUESTIONS.get(index) : null;
    }

    /** 现在问到第几题（= 已经答了几题）。 */
    public static int progress(ServerPlayer player) {
        return Math.max(0, player.getPersistentData().getInt(KEY_INDEX));
    }

    /** 开始问答（跳进井里那一刻）。 */
    public static void start(ServerPlayer player) {
        player.getPersistentData().putInt(KEY_INDEX, 0);
        for (Sequence s : Sequence.values()) {
            player.getPersistentData().remove("GRQuizScore_" + s.name());
        }
    }

    /** 还在问吗（没觉醒 + 没答完）。 */
    public static boolean running(ServerPlayer player) {
        return Sequence.of(player) == null && progress(player) < QUESTIONS.size();
    }

    /**
     * **回答当前这一题**。
     *
     * @return 答完七题返回被看中的序列；还没答完返回 null
     */
    public static Sequence answer(ServerPlayer player, int choiceIndex) {
        // 底色：这一票记下来（决定觉醒后走哪条血肉线，见 BloodlineBias）
        com.habaiwa.goetyrevenant.common.flesh.BloodlineBias
                .record(player, progress(player), choiceIndex);
        int index = progress(player);
        Question question = question(index);
        if (question == null || choiceIndex < 0 || choiceIndex >= question.choices().size()) {
            return null;
        }
        for (Map.Entry<Sequence, Integer> e : question.choices().get(choiceIndex).toward().entrySet()) {
            addScore(player, e.getKey(), e.getValue());
        }
        player.getPersistentData().putInt(KEY_INDEX, index + 1);
        if (index + 1 < QUESTIONS.size()) {
            return null;
        }
        return match(player);
    }

    /** **匹配**：分最高的那条看中你。平手时按声明顺序。 */
    public static Sequence match(ServerPlayer player) {
        Sequence best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Sequence s : Sequence.values()) {
            int score = score(player, s);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        Sequence picked = best == null ? Sequence.CANDLE_KEEPER : best;

        // **觉醒的同时定下血肉线** ——
        // 用户 2026-10-11：「每个序列者觉醒后，血肉线都会走向其中一条。
        // 不是选的，是你的**底色**决定的：向外要→异化 / 向内收→灵化 / 向下沉→硅化」。
        // 底色由七道题顺带算出来（见 BloodlineBias）。只定一次，重进不会改。
        if (!com.habaiwa.goetyrevenant.common.flesh.FleshProgress.defined(player)) {
            com.habaiwa.goetyrevenant.common.flesh.FleshLine flesh =
                    com.habaiwa.goetyrevenant.common.flesh.BloodlineBias.resolve(player);
            if (flesh != null) {
                com.habaiwa.goetyrevenant.common.flesh.FleshProgress.set(player, flesh, 0);
            }
            // flesh == null（三方向平票）→ 保持"未定"，不猜
        }
        return picked;
    }

    public static int score(ServerPlayer player, Sequence sequence) {
        return player.getPersistentData().getInt("GRQuizScore_" + sequence.name());
    }

    private static void addScore(ServerPlayer player, Sequence sequence, int delta) {
        String key = "GRQuizScore_" + sequence.name();
        player.getPersistentData().putInt(key, player.getPersistentData().getInt(key) + delta);
    }

    /** 谁是看中你的那条（没觉醒返回 null）。 */
    public static Sequence looked(ServerPlayer player) {
        return Sequence.of(player);
    }

    /** 结果提示（只说"是谁"，不提失去）。 */
    public static Component resultHint(ServerPlayer player) {
        Sequence s = Sequence.of(player);
        return s == null ? Component.literal("还没有人看中你")
                : Component.literal("看中你的，是「" + s.cn() + "」");
    }
}
