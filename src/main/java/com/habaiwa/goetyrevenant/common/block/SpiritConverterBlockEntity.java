package com.habaiwa.goetyrevenant.common.block;

import com.habaiwa.goetyrevenant.common.compat.MalumCompat;
import com.habaiwa.goetyrevenant.init.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * 精魄转化方块的核心逻辑。
 *
 * <p><b>规则</b>：下方必须是**诅咒之笼**，从笼子的灵魂能量里扣 ——
 * **5 点 → 1 枚精魄**；选中的精魄**持续自动产出**，产物**自动塞进周围容器**（塞不下就掉在原地）。</p>
 *
 * <h3>选中方式是「多选」</h3>
 * <p>以前是「选一个起始种类 + 调同时产出几种」，现在是**九种精魄各自勾选，最多九种一起产**：</p>
 * <ul>
 *   <li>用一个 9 位的掩码（{@link #selectedMask}）记「哪几种要产」</li>
 *   <li><b>默认一个都没选 → 什么都不产</b>，必须先在界面上点出你要的精魄</li>
 *   <li>一轮的花费 = 5 × 选中的数量</li>
 * </ul>
 */
public class SpiritConverterBlockEntity extends BlockEntity {
    /** 一枚精魄消耗的灵魂能量。 */
    public static final int SOUL_COST = 5;
    /** 产出间隔（tick）。 */
    public static final int INTERVAL = 40;
    /** 最多能同时选几种（= 精魄总数）。 */
    public static final int MAX_TYPES = 9;
    /** 全选时的掩码。 */
    public static final int FULL_MASK = (1 << MAX_TYPES) - 1;

    /** 第 i 位 = 第 i 种精魄要不要产。 */
    private int selectedMask;
    private int progress;

    public SpiritConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SPIRIT_CONVERTER_BE.get(), pos, state);
    }

    // ===== 选择 =====

    public int getSelectedMask() {
        return this.selectedMask;
    }

    /** 界面 / 指令回传：服务端已复检过方块身份。 */
    public void setSelectedMask(int mask) {
        this.selectedMask = mask & FULL_MASK;
        this.setChanged();
    }

    public boolean isSelected(int index) {
        return index >= 0 && index < MAX_TYPES && (this.selectedMask & (1 << index)) != 0;
    }

    /** 点一下切换某一种。 */
    public void toggle(int index) {
        if (index < 0 || index >= MAX_TYPES) {
            return;
        }
        this.selectedMask ^= (1 << index);
        this.setChanged();
    }

    /** 选中了几种。 */
    public int selectedCount() {
        return Integer.bitCount(this.selectedMask);
    }

    /** 潜行右键：全选 / 全不选。 */
    public void toggleAll() {
        this.selectedMask = this.selectedMask == 0 ? FULL_MASK : 0;
        this.setChanged();
    }

    public int getProgress() {
        return this.progress;
    }

    public static int interval() {
        return INTERVAL;
    }

    /** 这一轮要花多少灵魂能量。 */
    public int roundCost() {
        return SOUL_COST * Math.max(1, selectedCount());
    }

    /** 下方是不是诅咒之笼（界面用来提示玩家放对位置）。 */
    public boolean hasCageBelow() {
        return this.level != null
                && this.level.getBlockEntity(this.worldPosition.below())
                instanceof com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
    }

    /** 笼子里的灵魂能量；没笼子返回 -1。 */
    public int cageSouls() {
        if (this.level != null
                && this.level.getBlockEntity(this.worldPosition.below())
                instanceof com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cage) {
            return cage.getSouls();
        }
        return -1;
    }

    public Component describe() {
        return Component.translatable("message.goety_revenant.converter_state", selectedCount());
    }

    /** 给调试指令用：把掩码转成一行可读的注册名。 */
    public List<String> selectedNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < MAX_TYPES; i++) {
            if (isSelected(i)) {
                names.add(MalumCompat.SPIRITS[i]);
            }
        }
        return names;
    }

    /** 按注册名切换某一种（指令用）。返回是否认识这个名字。 */
    public boolean toggleByName(String name) {
        for (int i = 0; i < MAX_TYPES; i++) {
            if (MalumCompat.SPIRITS[i].equals(name)) {
                toggle(i);
                return true;
            }
        }
        return false;
    }

    // ===== 产出 =====

    public static void tick(SpiritConverterBlockEntity converter) {
        if (converter.level == null || converter.level.isClientSide) {
            return;
        }
        if (++converter.progress < INTERVAL) {
            return;
        }
        converter.progress = 0;
        converter.convert();
    }

    private void convert() {
        if (!MalumCompat.loaded() || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 一个都没选 → 什么都不产（这就是「默认要先去点要哪几种」）
        if (this.selectedMask == 0) {
            return;
        }
        if (!(this.level.getBlockEntity(this.worldPosition.below())
                instanceof com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cage)) {
            return;
        }
        int count = selectedCount();
        int cost = SOUL_COST * count;
        if (cage.getSouls() < cost) {
            return;
        }

        List<ItemStack> produced = new ArrayList<>();
        for (int i = 0; i < MAX_TYPES; i++) {
            if (!isSelected(i)) {
                continue;
            }
            Item spirit = MalumCompat.spiritItem(MalumCompat.SPIRITS[i]);
            if (spirit != null && spirit != Items.AIR) {
                produced.add(new ItemStack(spirit));
            }
        }
        if (produced.isEmpty()) {
            return;
        }

        cage.decreaseSouls(cost);
        cage.setChanged();
        cage.markUpdated();
        for (ItemStack stack : produced) {
            output(serverLevel, stack);
        }
    }

    /** 先往周围的容器里塞，塞不下就掉在方块上方。 */
    private void output(ServerLevel level, ItemStack stack) {
        ItemStack remaining = stack;
        for (Direction direction : Direction.values()) {
            BlockPos target = this.worldPosition.relative(direction);
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (blockEntity == null) {
                continue;
            }
            IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite())
                    .resolve().orElse(null);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) {
                return;
            }
        }
        if (!remaining.isEmpty()) {
            Block.popResource(level, this.worldPosition.above(), remaining);
        }
    }

    // ===== 存档 =====

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("SelectedMask", this.selectedMask);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.selectedMask = tag.getInt("SelectedMask") & FULL_MASK;
    }
}
