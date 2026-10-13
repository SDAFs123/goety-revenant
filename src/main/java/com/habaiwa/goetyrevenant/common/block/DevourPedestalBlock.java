package com.habaiwa.goetyrevenant.common.block;

import com.Polarice3.Goety.common.blocks.PedestalBlock;
import com.Polarice3.Goety.common.blocks.entities.PedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * **吞噬基座** —— 血肉仪式专用的基座，材料摆在它上面。
 *
 * <h3>为什么继承本体的 {@code PedestalBlock}</h3>
 * <p>本体的 {@code Ritual#getPedestals} 是这么找基座的：</p>
 * <pre>
 * if (tileEntity instanceof PedestalBlockEntity &amp;&amp; !(tileEntity instanceof DarkAltarBlockEntity) ...)
 * </pre>
 * <p>它<b>只认方块实体类型</b>。所以只要我们的基座挂本体的 {@code PedestalBlockEntity}，
 * 右键放材料、材料飞向祭坛、按顺序消耗，全部白送。</p>
 *
 * <p>血肉仪式**只认吞噬基座** —— 附近摆本体基座会被
 * {@code FleshRitual#getRequirement} 拦下来。</p>
 */
public class DevourPedestalBlock extends PedestalBlock {

    public DevourPedestalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PedestalBlockEntity(pos, state);
    }
}
