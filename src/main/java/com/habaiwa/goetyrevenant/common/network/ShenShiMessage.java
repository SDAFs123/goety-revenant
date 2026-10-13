package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S：**神识扫视** —— 主动把当前半径内的东西完整报一遍（对应神识强度）。 */
public class ShenShiMessage {
    public ShenShiMessage() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static ShenShiMessage decode(FriendlyByteBuf buf) {
        return new ShenShiMessage();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.habaiwa.goetyrevenant.common.body.ShenShi.toggle(player);
            }
        });
        context.setPacketHandled(true);
    }
}
