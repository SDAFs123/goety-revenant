package com.habaiwa.goetyrevenant.common.block;

import com.habaiwa.goetyrevenant.common.flesh.FleshEnergy;
import com.habaiwa.goetyrevenant.common.item.FleshHeartItem;
import com.habaiwa.goetyrevenant.common.item.FleshTotemItem;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * **血肉之笼** —— 血肉祭坛的"胃"，放在祭坛正下方。
 *
 * <ul>
 *   <li>手拿 <b>血肉之心</b> 右键 → 丢进燃料槽</li>
 *   <li>手拿 <b>血肉图腾</b> 右键 → 放进容器槽（仪式必须有它）</li>
 *   <li>空手潜行右键 → 把里面东西都掏出来</li>
 * </ul>
 */
public class FleshCageBlock extends BaseEntityBlock {

    public FleshCageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FleshCageBlockEntity cage)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        // 空手潜行 = 全掏出来
        if (held.isEmpty() && player.isCrouching()) {
            IItemHandler handler = cage.items();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.extractItem(i, 64, false);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY() + 1.0D, pos.getZ(), stack);
                }
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        int slot = held.getItem() instanceof FleshHeartItem ? FleshEnergy.SLOT_HEART
                : held.getItem() instanceof FleshTotemItem ? FleshEnergy.SLOT_TOTEM : -1;
        if (slot < 0) {
            return InteractionResult.PASS;
        }
        IItemHandler handler = cage.items();
        ItemStack rest = handler.insertItem(slot, held, false);
        if (rest.getCount() != held.getCount()) {
            player.setItemInHand(hand, rest);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FleshCageBlockEntity cage) {
                IItemHandler handler = cage.items();
                for (int i = 0; i < handler.getSlots(); i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(i));
                }
            }
            super.onRemove(state, level, pos, newState, moving);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FleshCageBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof FleshCageBlockEntity cage) {
                cage.tick();
            }
        };
    }
}
