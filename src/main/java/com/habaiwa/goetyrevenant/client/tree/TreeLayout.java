package com.habaiwa.goetyrevenant.client.tree;

/**
 * **魂树内容图的布局常量** —— 分区、比例、视口。
 *
 * <p>抽出来的原因：这几个数字原本散在 `TreeTexture`（分区）、`TreeScreen`（视口换算）、
 * `SoulBackdrop`（星空分布）三处，**同一个比例要在三个文件里手抄**，
 * 改一处忘一处就会出现"地面线对不上"这类问题。</p>
 *
 * <h3>内容图是怎么分的</h3>
 * <p>内容图比屏幕大（倍数见下），从顶到底分成六段：</p>
 * <pre>
 *   0.00 ~ 0.14   宇宙        星系 / 深空 / 稀疏的星        （星空之上）
 *   0.14 ~ 0.34   星空        银河 / 星团 / 当空之宫
 *   0.34 ~ 0.46   夜空 + 树    树干与树冠长在这一段
 *   0.46 ~ 0.60   土壤        颗粒 + 层理 + 石子
 *   0.60 ~ 0.66   过渡层      纹理同土壤密度，只有颜色在变
 *   0.66 ~ 1.00   血肉土壤    血浸透的土：血管、孔隙、深部血光
 * </pre>
 *
 * <p>数字都是<b>内容图高度的比例</b>（0~1），不是像素。</p>
 */
public final class TreeLayout {
    private TreeLayout() {
    }

    // ----------------------------------------------------------------------
    //  画布倍数 —— **两个方向必须相等**
    //
    //  屏幕是 16:9，内容图如果宽高倍数不等，视口放进去就会
    //  一个方向铺满、另一个方向只剩一条（"只有长没有宽"）。
    //  相等之后横竖能拖的范围一样大。
    // ----------------------------------------------------------------------
    public static final float CONTENT_W = 3.80F;
    public static final float CONTENT_H = 4.30F;

    /** 宇宙区的底（往下就是星空）。 */
    public static final float UNIVERSE_END = 0.14F;
    /** 星空底（往下就开始变稀）。 */
    public static final float SKY_END = 0.34F;
    /** 土壤顶 —— 树从这里长出来。 */
    public static final float SOIL_LINE = 0.46F;
    /** 土壤底 = 过渡层顶。 */
    public static final float SOIL_BOTTOM = 0.60F;
    /** 过渡层底 = 血肉土壤顶。 */
    public static final float FLESH_LINE = 0.66F;
}
