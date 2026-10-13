package com.habaiwa.goetyrevenant.common.block;

import com.habaiwa.goetyrevenant.common.flesh.FleshEnergy;
import com.habaiwa.goetyrevenant.common.item.FleshHeartItem;
import com.habaiwa.goetyrevenant.common.item.FleshTotemItem;
import com.habaiwa.goetyrevenant.init.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * **血肉之笼**的方块实体 —— 血肉祭坛的"胃"。
 *
 * <h3>两个格子</h3>
 * <table border="1">
 *   <tr><th>槽</th><th>放什么</th><th>作用</th></tr>
 *   <tr><td>0</td><td>{@code 血肉之心}</td><td>燃料。笼子每 20 tick 嚼一颗，榨出 {@link FleshEnergy#PER_HEART} 点血肉能量</td></tr>
 *   <tr><td>1</td><td>{@code 血肉图腾}</td><td>容器。<b>必须放</b> —— 没有图腾，能量无处可存，仪式根本发动不了</td></tr>
 * </table>
 *
 * <p>血肉祭坛每 20 tick 从这里的图腾上扣一次 {@code soulCost}，
 * 扣不动或者图腾被拿走，仪式当场断掉。</p>
 */
public class FleshCageBlockEntity extends BlockEntity {
    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            FleshCageBlockEntity.this.setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == FleshEnergy.SLOT_HEART
                    ? stack.getItem() instanceof FleshHeartItem
                    : stack.getItem() instanceof FleshTotemItem;
        }
    };
    private LazyOptional<IItemHandler> handler = LazyOptional.of(() -> this.items);

    public FleshCageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FLESH_CAGE_BE.get(), pos, state);
    }

    /* ------------------------------------------------------------------ */
    /*  对外                                                                */
    /* ------------------------------------------------------------------ */

    /** 笼子里的血肉图腾（没有就是空栈）。 */
    public ItemStack totem() {
        return this.items.getStackInSlot(FleshEnergy.SLOT_TOTEM);
    }

    /** 里面有没有图腾 —— 祭坛判定要用。 */
    public boolean hasTotem() {
        return this.totem().getItem() instanceof FleshTotemItem;
    }

    /** 此刻能拿出来用的血肉能量。 */
    public int energy() {
        return FleshEnergy.get(this.totem());
    }

    /** 够不够扣。 */
    public boolean canPay(int amount) {
        return this.hasTotem() && this.energy() >= amount;
    }

    /** 扣能量（够才扣）。 */
    public void pay(int amount) {
        FleshEnergy.drain(this.totem(), amount);
        this.setChanged();
        this.sync();
    }

    public IItemHandler items() {
        return this.items;
    }

    /* ------------------------------------------------------------------ */
    /*  嚼心脏                                                              */
    /* ------------------------------------------------------------------ */

    public void tick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        ItemStack totem = this.totem();
        if (!(totem.getItem() instanceof FleshTotemItem)) {
            return;
        }
        // 图腾满了就先不嚼，免得浪费心脏
        if (FleshEnergy.get(totem) >= FleshEnergy.capacity(totem)) {
            return;
        }
        ItemStack heart = this.items.getStackInSlot(FleshEnergy.SLOT_HEART);
        if (!(heart.getItem() instanceof FleshHeartItem)) {
            return;
        }
        if (this.level.getGameTime() % 20 != 0) {
            return;
        }

        heart.shrink(1);
        FleshEnergy.add(totem, com.habaiwa.goetyrevenant.common.config.RevenantConfig.fleshPerHeart);
        this.setChanged();
        this.sync();

        if (this.level instanceof ServerLevel server) {
            BlockPos p = this.getBlockPos();
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    p.getX() + 0.5D, p.getY() + 0.8D, p.getZ() + 0.5D, 3, 0.2D, 0.2D, 0.2D, 0.0D);
            server.playSound(null, p, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.6F, 0.6F);
        }
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  存档 / 能力                                                         */
    /* ------------------------------------------------------------------ */

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items.deserializeNBT(tag.getCompound("items"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", this.items.serializeNBT());
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return this.handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.handler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.handler = LazyOptional.of(() -> this.items);
    }
}
