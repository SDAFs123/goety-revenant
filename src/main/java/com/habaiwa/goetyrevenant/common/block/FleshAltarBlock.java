package com.habaiwa.goetyrevenant.common.block;

import com.Polarice3.Goety.common.blocks.DarkAltarBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * **血肉祭坛** —— 一张<b>大嘴</b>。
 *
 * <h3>两个新状态</h3>
 * <table border="1">
 *   <tr><th>属性</th><th>平时</th><th>仪式中</th><th>模型要做什么</th></tr>
 *   <tr><td>{@code mouth_open}</td><td>false（闭着）</td><td>true（张开）</td><td>下颌向下转开</td></tr>
 *   <tr><td>{@code eyes}</td><td>false（闭眼）</td><td>true（睁眼）</td><td>眼睛睁开、发光</td></tr>
 * </table>
 *
 * <p>这两个状态由 {@link FleshAltarBlockEntity} 每 tick 根据"有没有在跑仪式"写进去，
 * 模型那边只要画两套形态（闭合 / 张开）就够了。</p>
 *
 * <p>"东西从嘴里出来"那一步是仪式<b>结束</b>时的表现，走的是
 * {@code FleshRitual#sendFinishRay} 那条射线钩子。</p>
 */
public class FleshAltarBlock extends DarkAltarBlock {
    /** 嘴张开 / 闭合。 */
    public static final BooleanProperty MOUTH_OPEN = BooleanProperty.create("mouth_open");
    /** 眼睛睁开 / 闭上。 */
    public static final BooleanProperty EYES = BooleanProperty.create("eyes");

    public FleshAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUTH_OPEN, EYES);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FleshAltarBlockEntity(pos, state);
    }
}
