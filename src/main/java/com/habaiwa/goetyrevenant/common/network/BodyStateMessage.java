package com.habaiwa.goetyrevenant.common.network;

import com.habaiwa.goetyrevenant.common.embed.BodyModData;
import com.habaiwa.goetyrevenant.common.embed.OrganType;
import com.Polarice3.Goety.api.items.magic.IFocus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C：把玩家当前的改造状态整份发给客户端 ——
 * 哪些器官开了、嵌了什么，以及**背包 / 聚晶包里所有可用的聚晶**（界面直接列出来点选）。
 */
public class BodyStateMessage {
    /**
     * 器官槽位。
     *
     * @param opened  这个窍开没开（= 有没有喝过魔药并压制掉元素超载）
     * @param locked  没开窍就是锁的（界面灰环 + 直接写要喝哪一瓶）
     * @param cost    这次嵌入要花多少灵魂能量（界面直接显示这个数）
     */
    public record Entry(String organ, boolean opened, boolean locked, boolean hasFocus, ItemStack focus, int cost) {
    }

    /**
     * 可用聚晶：`slot` 的编码 ——
     * 0..35 主背包、36 副手、`100 + 背包槽*100 + 包内序号` 聚晶包。
     */
    public record FocusEntry(int slot, ItemStack stack) {
    }

    private final boolean unlocked;
    private final Entry[] entries;
    private final FocusEntry[] focuses;
    /** 玩家当前的灵魂能量（界面显示「够不够」）。 */
    private final int soulEnergy;
    /** 灵魂能量上限（灵魂自己的基础值 + 方舟 / 图腾加上去的）。 */
    private final int energyMax;

    /** 灵魂温度（0~100）—— 底层资源，界面要显示。 */
    private final int temperature;
    /** 这次打开要不要播生长动画（序列**升了**才有）。 */
    private final boolean newStage;
    /** 当前序列（-1 = 还没跳井，0 = 零阶幼苗，1~7 = 烛守…回声）。 */
    private final int stage;
    /**
     * **觉醒的是哪一条序列**（{@code Sequence.ordinal()}；-1 = 还没觉醒）。
     *
     * <p>之前只同步了"阶"，没同步"是哪条" —— 于是魂树界面永远显示"序列未定"，
     * 树也不会按序列变样。</p>
     */
    private final int sequence;
    /** **血肉线**走的是哪条路（-1 = 未定）。 */
    private final int fleshLine;
    /** 血肉线走到第几阶。 */
    private final int fleshStage;
    /** 24 个强化槽各自选了什么：0 = 空，1/2/3 = 攻/守/速。 */
    private final int[] upgrades;
    /** 距离下次能切换还有多少 tick。 */
    private final int upgradeCooldown;

    public BodyStateMessage(boolean unlocked, Entry[] entries, FocusEntry[] focuses, int soulEnergy,
                            int energyMax, int temperature, boolean newStage,
                            int[] upgrades, int upgradeCooldown, int stage, int sequence,
                            int fleshLine, int fleshStage) {
        this.unlocked = unlocked;
        this.entries = entries;
        this.focuses = focuses;
        this.soulEnergy = soulEnergy;
        this.energyMax = energyMax;
        this.temperature = temperature;
        this.newStage = newStage;
        this.upgrades = upgrades;
        this.upgradeCooldown = upgradeCooldown;
        this.stage = stage;
        this.sequence = sequence;
        this.fleshLine = fleshLine;
        this.fleshStage = fleshStage;
    }

    private static boolean isFocus(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IFocus;
    }

    public static BodyStateMessage of(ServerPlayer player) {
        OrganType[] organs = OrganType.values();
        Entry[] entries = new Entry[organs.length];
        for (int i = 0; i < organs.length; i++) {
            ItemStack focus = BodyModData.getFocus(player, organs[i]);
            boolean has = focus != null && !focus.isEmpty();
            boolean opened = BodyModData.isOpened(player, organs[i]);
            entries[i] = new Entry(organs[i].getId(), opened,
                    BodyModData.isSlotLocked(player, organs[i]), has, has ? focus : ItemStack.EMPTY,
                    com.habaiwa.goetyrevenant.common.embed.Embedding.costFor(opened));
        }

        List<FocusEntry> available = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (isFocus(stack)) {
                available.add(new FocusEntry(i, stack.copy()));
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        if (isFocus(offhand)) {
            available.add(new FocusEntry(36, offhand.copy()));
        }
        // 聚晶包 / 多晶大袋：走物品处理器能力把里面装的聚晶也列出来
        for (int i = 0; i < 36; i++) {
            ItemStack bag = player.getInventory().items.get(i);
            if (bag.isEmpty() || isFocus(bag)) {
                continue;
            }
            IItemHandler handler = bag.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
            if (handler == null) {
                continue;
            }
            for (int j = 0; j < handler.getSlots(); j++) {
                ItemStack inner = handler.getStackInSlot(j);
                if (isFocus(inner)) {
                    available.add(new FocusEntry(100 + i * 100 + j, inner.copy()));
                }
            }
        }

        return new BodyStateMessage(BodyModData.isUnlocked(player), entries, available.toArray(new FocusEntry[0]),
                (int) com.Polarice3.Goety.utils.SEHelper.getSESouls(player),
                com.habaiwa.goetyrevenant.common.soul.SoulEnergy.max(player),
                com.habaiwa.goetyrevenant.common.soul.SoulTemperature.get(player),
                takeNewStage(player),
                com.habaiwa.goetyrevenant.common.embed.UpgradeSlots.snapshot(player),
                com.habaiwa.goetyrevenant.common.embed.UpgradeSlots.cooldown(player),
                com.habaiwa.goetyrevenant.common.tree.SoulTree.stage(player),
                seqOrdinal(player),
                fleshLineOrdinal(player),
                com.habaiwa.goetyrevenant.common.flesh.FleshProgress.stage(player));
    }

    /** 血肉线走的是哪条（-1 = 未定）。 */
    private static int fleshLineOrdinal(ServerPlayer player) {
        com.habaiwa.goetyrevenant.common.flesh.FleshLine line =
                com.habaiwa.goetyrevenant.common.flesh.FleshProgress.line(player);
        return line == null ? -1 : line.ordinal();
    }

    /** 觉醒的是哪一条（-1 = 还没）。 */
    private static int seqOrdinal(ServerPlayer player) {
        com.habaiwa.goetyrevenant.common.sequence.Sequence seq =
                com.habaiwa.goetyrevenant.common.sequence.Sequence.of(player);
        return seq == null ? -1 : seq.ordinal();
    }

    /**
     * **这次打开要不要播生长动画** —— 只有序列**升了**才算。
     *
     * <p>做法：玩家身上记一个「上次看到的序列」，比它高就返回 true 并把它推上去。
     * 所以同一阶反复打开界面**不会**反复播成长；真的升阶时才播一次。</p>
     */
    private static boolean takeNewStage(ServerPlayer player) {
        int now = com.habaiwa.goetyrevenant.common.tree.SoulTree.stage(player);
        net.minecraft.nbt.CompoundTag tag = player.getPersistentData();
        int seen = tag.contains("GRTreeSeenStage") ? tag.getInt("GRTreeSeenStage") : -2;
        if (now > seen) {
            tag.putInt("GRTreeSeenStage", now);
            return true;
        }
        return false;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.unlocked);
        buf.writeVarInt(this.entries.length);
        for (Entry entry : this.entries) {
            buf.writeUtf(entry.organ());
            buf.writeBoolean(entry.opened());
            buf.writeBoolean(entry.locked());
            buf.writeBoolean(entry.hasFocus());
            if (entry.hasFocus()) {
                buf.writeItem(entry.focus());
            }
            buf.writeVarInt(entry.cost());
        }
        buf.writeVarInt(this.focuses.length);
        for (FocusEntry focus : this.focuses) {
            buf.writeVarInt(focus.slot());
            buf.writeItem(focus.stack());
        }
        buf.writeVarInt(this.soulEnergy);
        buf.writeVarInt(this.energyMax);
        buf.writeVarInt(this.temperature);
        buf.writeBoolean(this.newStage);
        buf.writeVarInt(this.upgrades.length);
        for (int v : this.upgrades) {
            buf.writeByte(v);
        }
        buf.writeVarInt(this.upgradeCooldown);
        buf.writeVarInt(this.stage);
        buf.writeVarInt(this.sequence);
        buf.writeVarInt(this.fleshLine + 1);          // -1 → 0（VarInt 不收负数）
        buf.writeVarInt(this.fleshStage);
    }

    public static BodyStateMessage decode(FriendlyByteBuf buf) {
        boolean unlocked = buf.readBoolean();
        int size = buf.readVarInt();
        Entry[] entries = new Entry[size];
        for (int i = 0; i < size; i++) {
            String organ = buf.readUtf();
            boolean opened = buf.readBoolean();
            boolean locked = buf.readBoolean();
            boolean has = buf.readBoolean();
            entries[i] = new Entry(organ, opened, locked, has, has ? buf.readItem() : ItemStack.EMPTY,
                    buf.readVarInt());
        }
        int focusCount = buf.readVarInt();
        FocusEntry[] focuses = new FocusEntry[focusCount];
        for (int i = 0; i < focusCount; i++) {
            focuses[i] = new FocusEntry(buf.readVarInt(), buf.readItem());
        }
        return new BodyStateMessage(unlocked, entries, focuses,
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                readUpgrades(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt() - 1, buf.readVarInt());
    }

    private static int[] readUpgrades(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] out = new int[Math.max(0, n)];
        for (int i = 0; i < out.length; i++) {
            out[i] = buf.readByte();
        }
        return out;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.state.BodyClientState.accept(this)));
        context.setPacketHandled(true);
    }

    public boolean unlocked() {
        return this.unlocked;
    }

    public Entry[] entries() {
        return this.entries;
    }

    public FocusEntry[] focuses() {
        return this.focuses;
    }

    public int soulEnergy() {
        return this.soulEnergy;
    }

    public int energyMax() {
        return this.energyMax;
    }

    /** 灵魂温度（0~100）。 */
    public int temperature() {
        return this.temperature;
    }

    /** 这次是不是刚升了阶（要播生长动画）。 */
    public boolean newStage() {
        return this.newStage;
    }

    /** 当前序列（-1 = 没跳井，0 = 零阶幼苗）。 */
    public int stage() {
        return this.stage;
    }

    /** 觉醒的序列序号（-1 = 还没觉醒）。 */
    public int sequence() {
        return this.sequence;
    }

    /** 血肉线的路（-1 = 未定）。 */
    public int fleshLine() {
        return this.fleshLine;
    }

    /** 血肉线走到第几阶。 */
    public int fleshStage() {
        return this.fleshStage;
    }

    /** 24 个强化槽的状态：0 = 空，1/2/3 = 攻/守/速。 */
    public int[] upgrades() {
        return this.upgrades;
    }

    /** 距离下次能切换强化方向还有多少 tick。 */
    public int upgradeCooldown() {
        return this.upgradeCooldown;
    }
}
