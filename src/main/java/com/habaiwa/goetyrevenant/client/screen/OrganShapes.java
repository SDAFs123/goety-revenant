package com.habaiwa.goetyrevenant.client.screen;

import com.habaiwa.goetyrevenant.common.body.OrganType;
import java.util.ArrayList;
import java.util.List;

/**
 * **六个窍各自的形状**。
 *
 * <h3>为什么不要人体了</h3>
 * <p>原来画一具人体、把窍"挖"在身上。但界面里人体只是一大片暗红，窍反而看不清；
 * 而且人体轮廓要跟窍的位置对齐，稍微一改就得重画。</p>
 * <p>那就<b>只画要看的东西</b> —— 心、肝、脾、肺、肾、灵窍，各画各的，
 * 摆在 {@code BodyLayout} 给的位置上。少了躯壳，器官反而立得住。</p>
 *
 * <h3>返回值</h3>
 * <p>每个器官给出一串"行段"：{@code {dy, x0, x1}}，坐标是<b>相对于器官中心</b>的像素，
 * {@code r} 是半径基准。一行可以有多段 —— 比如心上面那两道是左右心房、中间是缺口，
 * 肺是左右两叶中间留气管，灵窍是中间那个洞。</p>
 */
final class OrganShapes {
    private OrganShapes() {
    }

    static int[][] spans(OrganType organ, int r) {
        List<int[]> out = new ArrayList<>();
        for (int dy = -r; dy <= r; dy++) {
            float t = dy / (float) r;           // -1 顶 .. +1 底
            switch (organ) {
                case HEART -> heart(out, dy, t, r);
                case LUNG -> lung(out, dy, t, r);
                case LIVER -> liver(out, dy, t, r);
                case SPLEEN -> spleen(out, dy, t, r);
                case KIDNEY -> kidney(out, dy, t, r);
                case SPIRIT_APERTURE -> aperture(out, dy, t, r);
            }
        }
        return out.toArray(new int[0][]);
    }

    /* ------------------------------------------------------------------ */

    /** 心：标准心形 —— 两个饱满的心房瓣、中间一道深凹槽、下面收成一个尖（微偏左）。 */
    private static void heart(List<int[]> o, int dy, float t, int r) {
        float hw = profile(t, new float[]{
                0.06F, 0.62F, 0.88F, 0.98F, 1.00F, 0.96F, 0.86F, 0.72F, 0.55F, 0.38F, 0.20F, 0.05F});
        if (hw <= 0.03F) {
            return;
        }
        // 心尖略偏左（解剖学心尖朝左下）
        int shift = (int) (-0.10F * r * Math.max(0.0F, t));
        if (t < -0.55F) {
            // 两心房之间的凹槽 —— 一直延伸到 2/3 深处
            float notch = 0.42F * (1.0F - (t + 1.0F) / 0.45F) + 0.05F;
            add(o, dy, shift - (int) (hw * r), shift - (int) (notch * r));
            add(o, dy, shift + (int) (notch * r), shift + (int) (hw * r));
        } else {
            add(o, dy, shift - (int) (hw * r), shift + (int) (hw * r));
        }
    }

    /** 肺：上圆下尖的两片肺叶（每片都是"馒头"），中间一条气管缝。 */
    private static void lung(List<int[]> o, int dy, float t, int r) {
        float hw = profile(t, new float[]{
                0.16F, 0.46F, 0.68F, 0.84F, 0.94F, 0.98F, 0.96F, 0.88F, 0.74F, 0.54F, 0.30F, 0.08F});
        if (hw <= 0.05F) {
            return;
        }
        // 顶部两叶几乎并拢（气管在顶端），往下分开
        float gap = (t < -0.6F) ? 0.08F : 0.18F;
        add(o, dy, -(int) (hw * r), -(int) (gap * r));
        add(o, dy, (int) (gap * r), (int) (hw * r));
    }

    /** 肝：一片横躺的肝 —— 右缘圆厚、向左下楔尖，上面平。 */
    private static void liver(List<int[]> o, int dy, float t, int r) {
        float hw = profile(t, new float[]{
                0.55F, 0.85F, 0.98F, 1.00F, 0.97F, 0.90F, 0.80F, 0.66F, 0.50F, 0.32F, 0.15F, 0.03F});
        if (hw <= 0.03F) {
            return;
        }
        // 上缘平、下缘斜 → 左半更短（楔形）
        float leftScale = 0.90F - 0.18F * Math.max(0.0F, t);
        float shift = 0.10F * r * (1.0F - Math.abs(t));
        add(o, dy, (int) (shift - hw * r * leftScale), (int) (shift + hw * r));
    }

    /** 脾：长卵形，内侧一条浅凹（器官都有自己的"腰"）。 */
    private static void spleen(List<int[]> o, int dy, float t, int r) {
        float hw = profile(t, new float[]{
                0.10F, 0.34F, 0.55F, 0.72F, 0.86F, 0.94F, 0.96F, 0.92F, 0.82F, 0.64F, 0.42F, 0.16F});
        if (hw <= 0.02F) {
            return;
        }
        float bite = Math.max(0.0F, 1.0F - Math.abs(t) * 2.2F) * 0.22F;
        add(o, dy, (int) (-hw * r * 0.80F), (int) ((hw - bite) * r));
    }

    /** 肾：豆形 —— 内侧一道明显的凹口（肾门），一看就知道是肾不是蛋。 */
    private static void kidney(List<int[]> o, int dy, float t, int r) {
        float hw = profile(t, new float[]{
                0.14F, 0.40F, 0.62F, 0.78F, 0.90F, 0.95F, 0.95F, 0.91F, 0.82F, 0.66F, 0.46F, 0.18F});
        if (hw <= 0.02F) {
            return;
        }
        float bite = Math.max(0.0F, 1.0F - Math.abs(t) * 2.2F) * 0.42F;
        int outer = (int) (hw * r);
        int inner = (int) ((hw - bite) * r);
        add(o, dy, -outer, inner);
    }

    /** 灵窍：一个环 —— 中间是那个"洞"。 */
    private static void aperture(List<int[]> o, int dy, float t, int r) {
        float outer = (float) Math.sqrt(Math.max(0.0F, 1.0F - t * t));
        if (outer <= 0.05F) {
            return;
        }
        int o1 = (int) (outer * r);
        int hole = (int) (outer * r * 0.44F);
        add(o, dy, -o1, -hole);
        add(o, dy, hole, o1);
    }

    /* ------------------------------------------------------------------ */

    private static void add(List<int[]> o, int dy, int x0, int x1) {
        if (x1 > x0) {
            o.add(new int[]{dy, x0, x1});
        }
    }

    /** 11 个采样点上的半宽，线性插值 —— 比公式好调。 */
    private static float profile(float t, float[] table) {
        float u = (t + 1.0F) * 0.5F * (table.length - 1);   // 0 .. len-1
        int i = (int) u;
        if (i < 0) {
            i = 0;
        }
        if (i >= table.length - 1) {
            return table[table.length - 1];
        }
        float f = u - i;
        return table[i] + (table[i + 1] - table[i]) * f;
    }
}
