package com.habaiwa.goetyrevenant.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * **血管** —— 把每一个吞噬基座接到血肉祭坛上的管道。
 *
 * <h3>它是真机制，不是装饰</h3>
 * <p>血肉仪式发动时，祭坛会从自己身上沿血管做一次<b>连通性搜索</b>：
 * 每一个吞噬基座都必须有一条连续的血管通往祭坛。断一节，仪式直接不成立
 * （仪式途中被挖断也一样，3 秒内自动中断）。判定在 {@code BloodVessels#connected}。</p>
 */
public class BloodVesselBlock extends Block {

    public BloodVesselBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
