package com.habaiwa.goetyrevenant.common.network;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.habaiwa.goetyrevenant.common.body.BodyModData;
import com.habaiwa.goetyrevenant.common.body.Embedding;
import com.habaiwa.goetyrevenant.common.body.FocusSlots;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** C2S：打开身体界面 / 从背包（或聚晶包）取一枚聚晶嵌入 / 取回某个器官里的聚晶。 */
public class BodyActionMessage {
    public static final byte OPEN = 0;
    public static final byte EMBED = 1;
    public static final byte EXTRACT = 2;

    private final byte action;
    private final String organ;
    private final int slot;

    public BodyActionMessage(byte action, String organ) {
        this(action, organ, -1);
    }

    public BodyActionMessage(byte action, String organ, int slot) {
        this.action = action;
        this.organ = organ;
        this.slot = slot;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(this.action);
        buf.writeUtf(this.organ);
        buf.writeVarInt(this.slot);
    }

    public static BodyActionMessage decode(FriendlyByteBuf buf) {
        return new BodyActionMessage(buf.readByte(), buf.readUtf(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                run(player);
            }
        });
        context.setPacketHandled(true);
    }

    private OrganType organType() {
        for (OrganType organ : OrganType.values()) {
            if (organ.getId().equals(this.organ)) {
                return organ;
            }
        }
        return null;
    }

    private void run(ServerPlayer player) {
        OrganType target = organType();
        switch (this.action) {
            case OPEN -> RevenantNetwork.sendBodyState(player);
            case EMBED -> {
                if (target != null) {
                    embed(player, target);
                }
            }
            case EXTRACT -> {
                if (target != null) {
                    extract(player, target);
                }
            }
            default -> {
            }
        }
    }

    private void embed(ServerPlayer player, OrganType organ) {
        if (!BodyModData.isUnlocked(player)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.body_locked"), true);
            return;
        }
        if (BodyModData.isSlotLocked(player, organ)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_slot_locked",
                    organ.displayName()), true);
            return;
        }
        ItemStack source = FocusSlots.peek(player, this.slot);
        if (source.isEmpty() || !(source.getItem() instanceof IFocus focusItem)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_need_focus"), true);
            return;
        }
        ISpell spell = focusItem.getSpell();
        // 用 forSpellType 而不是 accepts：附属模组自造的新派系会被它猜进某个窍，
        // 两边（客户端界面 / 服务端校验）走的是同一个函数，不会打架。
        if (spell == null || OrganType.forSpellType(spell.getSpellType()) != organ) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.embed_wrong_element",
                    organ.displayName()), true);
            return;
        }
        // 注意：这里**不扣聚晶** —— 扣的动作在 Embedding.finish() 里，
        // 只有真正嵌入成功才会拿走；中途失败聚晶留在背包（见 Embedding 的类注释）。
        if (Embedding.start(player, organ, source.copy(), this.slot)) {
            RevenantNetwork.sendBodyState(player);
        }
    }

    private void extract(ServerPlayer player, OrganType organ) {
        ItemStack stored = BodyModData.getFocus(player, organ);
        if (stored == null || stored.isEmpty()) {
            return;
        }
        BodyModData.setFocus(player, organ, ItemStack.EMPTY);
        Embedding.applyAll(player);
        player.getInventory().placeItemBackInInventory(stored);
        RevenantNetwork.sendBodyState(player);
    }
}
