using System;
using System.Drawing;
using System.Drawing.Imaging;

// 生成两个创造标签页的专属背景（195x136，可平铺）
//
// 设计原则（上一版栽在这）：
//   1. **背景不该抢戏** —— 元素与底色的对比要低，物品图标才跳得出来。
//   2. **少** —— 十几个精致的元素胜过几百个噪点；满屏颗粒只会显得脏。
//   3. **可平铺** —— 不用光晕/暗角这类"有中心"的图案（平铺会出网格），
//      只用均匀渐变 + 不跨边界的小元素。
public static class TabArt {
    static void Blend(Bitmap b, int x, int y, Color c, double k) {
        if (x < 0 || y < 0 || x >= b.Width || y >= b.Height) return;
        if (k <= 0) return;
        if (k > 1) k = 1;
        Color o = b.GetPixel(x, y);
        b.SetPixel(x, y, Color.FromArgb(255,
            (int)(o.R + (c.R - o.R) * k),
            (int)(o.G + (c.G - o.G) * k),
            (int)(o.B + (c.B - o.B) * k)));
    }

    static void Glow(Bitmap b, double cx, double cy, double rad, Color c, double strength) {
        int x0 = Math.Max(0, (int)(cx - rad)), x1 = Math.Min(b.Width - 1, (int)(cx + rad));
        int y0 = Math.Max(0, (int)(cy - rad)), y1 = Math.Min(b.Height - 1, (int)(cy + rad));
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double d = Math.Sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / rad;
                if (d >= 1) continue;
                Blend(b, x, y, c, Math.Pow(1 - d, 3.0) * strength);
            }
    }

    // ============ 回魂：一片安静的灵魂空间 ============
    public static void Soul(string path, int W, int H) {
        var b = new Bitmap(W, H, PixelFormat.Format32bppArgb);
        var r = new Random(20261011);

        // 底：干净的垂直渐变（上略亮），只加 1~2 的微差，避免"塑料感"
        for (int y = 0; y < H; y++) {
            double t = y / (double)(H - 1);
            int rr = (int)(7 - 3 * t), gg = (int)(21 - 10 * t), bb = (int)(25 - 12 * t);
            for (int x = 0; x < W; x++) {
                int n = r.Next(0, 3);
                b.SetPixel(x, y, Color.FromArgb(255, rr + n, gg + n, bb + n));
            }
        }

        // 三道很宽、很淡的横向光带：像光从一侧淌过来（不平铺出格）
        for (int i = 0; i < 3; i++) {
            double baseY = 22 + i * 46;
            double wid = 20 + r.Next(14);
            for (int y = 0; y < H; y++) {
                double d = Math.Abs(y - baseY) / wid;
                if (d >= 1) continue;
                double k = (1 - d) * 0.15;
                for (int x = 0; x < W; x++) Blend(b, x, y, Color.FromArgb(255, 32, 100, 96), k);
            }
        }

        // **十二粒**魂火（不是九十五粒）：每粒一小片柔光 + 一个亮点
        for (int i = 0; i < 12; i++) {
            int x = r.Next(12, W - 12), y = r.Next(12, H - 12);
            int tier = r.Next(3);
            Color core = tier == 2 ? Color.FromArgb(255, 172, 246, 238)
                       : tier == 1 ? Color.FromArgb(255, 86, 212, 198)
                                   : Color.FromArgb(255, 46, 152, 144);
            Glow(b, x, y, 6.5 + tier * 2.6, Color.FromArgb(255, 20, 86, 82), 0.42);
            Blend(b, x, y, core, 0.88);
        }

        // 一点微尘：少、淡（不是 560 个的噪点雨）
        for (int i = 0; i < 130; i++)
            Blend(b, r.Next(W), r.Next(H), Color.FromArgb(255, 72, 162, 154), r.NextDouble() * 0.14);

        b.Save(path, ImageFormat.Png);
        b.Dispose();
    }

    // ============ 血肉：一块安静的腹腔 ============
    public static void Flesh(string path, int W, int H) {
        var b = new Bitmap(W, H, PixelFormat.Format32bppArgb);
        var r = new Random(7777);

        // 底：暗红垂直渐变
        for (int y = 0; y < H; y++) {
            double t = y / (double)(H - 1);
            int rr = (int)(56 - 24 * t), gg = (int)(15 - 7 * t), bb = (int)(19 - 8 * t);
            for (int x = 0; x < W; x++) {
                int n = r.Next(0, 3);
                b.SetPixel(x, y, Color.FromArgb(255, rr + n, gg + n / 2, bb + n / 2));
            }
        }

        // 四块大而淡的肉色区（不做小碎斑）
        for (int i = 0; i < 4; i++)
            Glow(b, r.Next(W), r.Next(H), 36 + r.Next(28), Color.FromArgb(255, 68, 20, 24), 0.2);

        // 稀疏肌纤维：44 条，对比很轻
        for (int i = 0; i < 44; i++) {
            int y = r.Next(H);
            int len = 24 + r.Next(72);
            int x0 = r.Next(Math.Max(1, W - len));
            Color c = r.Next(2) == 0 ? Color.FromArgb(255, 92, 28, 32) : Color.FromArgb(255, 26, 8, 10);
            double k = 0.14 + r.NextDouble() * 0.16;
            for (int x = x0; x < x0 + len; x++) Blend(b, x, y, c, k);
        }

        // 两条主血管：平滑、粗细起伏
        for (int v = 0; v < 2; v++) {
            double x = r.Next(W), y = r.Next(H), ang = r.NextDouble() * Math.PI * 2;
            for (int s = 0; s < 260; s++) {
                ang += (r.NextDouble() - 0.5) * 0.14;
                x += Math.Cos(ang) * 1.1; y += Math.Sin(ang) * 1.1;
                if (x < 0 || y < 0 || x >= W || y >= H) break;
                double wid = 1.3 + 1.1 * Math.Sin(s * 0.045);
                Glow(b, x, y, wid + 1.1, Color.FromArgb(255, 54, 12, 16), 0.3);
                Blend(b, (int)x, (int)y, Color.FromArgb(255, 136, 36, 40), 0.46);
            }
        }

        // 极少颗粒
        for (int i = 0; i < 400; i++)
            Blend(b, r.Next(W), r.Next(H), Color.FromArgb(255, 24, 7, 9), r.NextDouble() * 0.15);

        b.Save(path, ImageFormat.Png);
        b.Dispose();
    }
}
