package com.habaiwa.goetyrevenant.common.flesh;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * **底色** —— 七问答顺带算出来的"你朝哪边走"，决定血肉线走三条路里的哪一条。
 *
 * <p>用户 2026-10-11 的原话：</p>
 * <blockquote>
 *   三条路的选择：每个序列者觉醒后，血肉线都会走向其中一条。**不是选的，是你的底色决定的**：
 *   · **向外要的人 → 异化**；· **向内收的人 → 灵化**；· **向下沉的人 → 硅化**。
 *   但三条路不分好坏，只是不同的"不再是人了"。
 * </blockquote>
 *
 * <h3>为什么表在代码里，而不是加进 Question</h3>
 * <p>七问答原本只给"偏袒哪条序列"打分（{@code Choice.toward}），没有方向这一维。
 * 为了不动那份已经调好的问答数据，这里用一张**独立的映射表**
 * （题号 + 选项号 → 方向），答完七题累加，多的那个就是底色。</p>
 *
 * <p>这样将来要调某个选项的倾向，只改这张表，不影响序列匹配。</p>
 *
 * <h3>映射是怎么定的</h3>
 * <p>按**选项文本的语义**对用户给的三句话：</p>
 * <ul>
 *   <li><b>向外</b>（给出去、往前、让别人看见）：蜡烛 / 比影子亮 / 喊你名字的 / 脚印 / 回声……</li>
 *   <li><b>向内</b>（收起来、往回、咽下去）：袋子 / 信没拆 / 没出声的 / 锁扣上 / 回来的路……</li>
 *   <li><b>向下</b>（停住、封上、沉下去）：墙 / 砌好的墙 / 从来不开口……</li>
 * </ul>
 *
 * <p>⚠️ <b>这套映射是我按语义定的，你随时可以改</b>（只改下面那张表即可，
 * 其它代码一行都不用动）。你 10-11 那条最后问的是
 * 「这个方向对吗？对的话，我把三条路的每个阶段推出来」——
 * 所以三条路**每个阶段**的内容还没定，这里只实现"走哪条路"这一步。</p>
 */
public final class BloodlineBias {
    private static final String KEY_OUT = "GRBiasOut";
    private static final String KEY_IN = "GRBiasIn";
    private static final String KEY_DOWN = "GRBiasDown";

    /**
     * **题号 → 4 个选项各自的方向**（0 = 向外 / 1 = 向内 / 2 = 向下）。
     *
     * <p>题序与 {@code SevenQuestions.build()} 里的顺序一致。</p>
     */
    private static final int[][] TABLE = {
            // 一·取舍（屋子要塌了，只能带一样）
            //    蜡烛(烛守·给光) / 袋子(拾荒·囤起来) / 没拆的信(缄口·不读) / 磨破的鞋(追影·继续走)
            {0, 1, 1, 0},
            // 二·投射（你看见自己的影子）
            //    挡住身后的光(筑墙·停在原地) / 朝来时的地方退(归墟·往回) / 比你本人还响(回声·外放) / 比别处都亮(烛守·外放)
            {2, 1, 0, 0},
            // 三·两难（水里有人，只拉得动一个）
            //    攥着东西的(拾荒·先保住物品) / 一直没出声的(缄口·安静) / 游得很远的(追影·往前) / 喊你名字的(回声·被听见)
            {1, 1, 0, 0},
            // 四·偏好（更想听见哪个声音）
            //    火烧起来(烛守·外放) / 门锁扣上(筑墙·封上) / 有人走回来(归墟·往回) / 零碎碰撞(拾荒·收着)
            {0, 2, 1, 1},
            // 五·取舍（先放下哪个）
            //    说话的权利(缄口→反而放开) / 别人的看法(追影) / 喊过的话(回声) / 砌好的墙(筑墙→不再守)
            {1, 0, 0, 0},
            // 六·自陈（别人怎么形容你）
            //    一直往回走(归墟) / 烧得太旺(烛守) / 什么都舍不得扔(拾荒) / 从来不开口(缄口→沉下去)
            {1, 0, 1, 2},
            // 七·完形（走了以后还剩什么）
            //    还在响的回声(回声) / 越不过去的墙(筑墙·停住) / 一串脚印(追影) / 一条回来的路(归墟)
            {0, 2, 0, 1},
    };

    private BloodlineBias() {
    }

    /** 答完一题的一票。 */
    public static void record(Player player, int questionIndex, int choiceIndex) {
        if (player == null || questionIndex < 0 || questionIndex >= TABLE.length) {
            return;
        }
        int[] row = TABLE[questionIndex];
        if (choiceIndex < 0 || choiceIndex >= row.length) {
            return;
        }
        String key = switch (row[choiceIndex]) {
            case 0 -> KEY_OUT;
            case 2 -> KEY_DOWN;
            default -> KEY_IN;
        };
        CompoundTag tag = player.getPersistentData();
        tag.putInt(key, tag.getInt(key) + 1);
    }

    public static int out(Player player) {
        return player == null ? 0 : player.getPersistentData().getInt(KEY_OUT);
    }

    public static int in(Player player) {
        return player == null ? 0 : player.getPersistentData().getInt(KEY_IN);
    }

    public static int down(Player player) {
        return player == null ? 0 : player.getPersistentData().getInt(KEY_DOWN);
    }

    /**
     * **底色 → 哪条血肉线**。
     *
     * <p>多数决；**三者全平（或一票没有）时返回 null** ——
     * 那种情况留给玩家自己，不猜。</p>
     */
    public static FleshLine resolve(Player player) {
        int o = out(player);
        int i = in(player);
        int d = down(player);
        if (o == 0 && i == 0 && d == 0) {
            return null;
        }
        int max = Math.max(o, Math.max(i, d));
        // 平局则不裁决（含"最高的有两个"）
        int ties = (o == max ? 1 : 0) + (i == max ? 1 : 0) + (d == max ? 1 : 0);
        if (ties != 1) {
            return null;
        }
        if (o == max) {
            return FleshLine.MUTATION;   // 向外要 → 异化
        }
        if (i == max) {
            return FleshLine.SPIRIT;     // 向内收 → 灵化
        }
        return FleshLine.SILICON;        // 向下沉 → 硅化
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        CompoundTag tag = player.getPersistentData();
        tag.remove(KEY_OUT);
        tag.remove(KEY_IN);
        tag.remove(KEY_DOWN);
    }
}
