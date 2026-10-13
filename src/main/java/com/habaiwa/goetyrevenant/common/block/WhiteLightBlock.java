package com.habaiwa.goetyrevenant.common.block;

import net.minecraft.world.level.block.Block;

/**
 * **纯白** —— 一个纯白的、自己会发光的方块。
 *
 * <p>那片白空间的地、墙、顶，铺的都是它；同时它也是能拿走的建材 ——
 * 想要一间永远亮着的白屋子，就在自己家里铺它。</p>
 *
 * <p>亮度直接拉满（15），所以白空间里不需要任何光源：
 * 那个维度本身的环境光也是满的（{@code ambient_light = 1.0}），
 * 两者叠起来就是"明亮得没有影子"。</p>
 */
public class WhiteLightBlock extends Block {
    public WhiteLightBlock(Properties properties) {
        super(properties);
    }
}
