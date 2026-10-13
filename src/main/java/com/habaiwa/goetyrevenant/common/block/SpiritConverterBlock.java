package com.habaiwa.goetyrevenant.common.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 精魄转化方块。
 *
 * <p>要**骑在诅咒之笼上方**（跟本体黑暗祭坛一个放法），从笼子里抽灵魂能量换成 Malum 精魄。</p>
 *
 * <p><b>操作</b>：</p>
 * <ul>
 *   <li>空手右键 —— 打开**精魄转化台界面**（选起始精魄 + 调同时产出几种，并显示笼内灵魂能量）</li>
 *   <li>潜行 + 右键 —— 不开界面，直接把起始精魄种类往后轮换一位</li>
 * </ul>
 */
public class SpiritConverterBlock extends Block implements EntityBlock {
    public SpiritConverterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiritConverterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof SpiritConverterBlockEntity converter) {
                SpiritConverterBlockEntity.tick(converter);
            }
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // 手上拿着东西 → 交还给原版（否则贴着转化台放不了方块）
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        // 客户端只负责挥手，实际逻辑在服务端
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SpiritConverterBlockEntity converter) {
            if (player.isShiftKeyDown()) {
                // 潜行右键 = 全选 / 全不选（不开界面）
                converter.toggleAll();
                player.displayClientMessage(Component.translatable("message.goety_revenant.converter_mode",
                        converter.describe()), true);
            } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.habaiwa.goetyrevenant.common.network.RevenantNetwork
                        .sendConverterSync(serverPlayer, pos, converter);
            }
        }
        return InteractionResult.CONSUME;
    }
}
