package com.habaiwa.goetyrevenant.common.network;

import com.habaiwa.goetyrevenant.common.embed.UpgradeSlots;
import com.habaiwa.goetyrevenant.common.embed.UpgradeType;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S：把某个强化槽切成某个方向（type = 0 表示清空）。 */
public class UpgradeMessage {
    private final int index;
    private final int type;

    public UpgradeMessage(int index, int type) {
        this.index = index;
        this.type = type;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.index);
        buf.writeVarInt(this.type);
    }

    public static UpgradeMessage decode(FriendlyByteBuf buf) {
        return new UpgradeMessage(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            UpgradeType.Type[] options = UpgradeSlots.candidates(this.index);
            UpgradeType.Type target = this.type <= 0 || this.type > options.length
                    ? null : options[this.type - 1];
            if (UpgradeSlots.set(player, this.index, target)) {
                player.displayClientMessage(Component.translatable("message.goety_revenant.upgrade_set",
                        target == null ? "空" : target.cn()), true);
                // 把新状态发回去，界面好刷新
                RevenantNetwork.sendBodyState(player);
            } else {
                int left = (UpgradeSlots.cooldown(player) + 19) / 20;
                player.displayClientMessage(Component.translatable(
                        "message.goety_revenant.upgrade_cooldown", left), true);
            }
        });
        context.setPacketHandled(true);
    }
}
