package com.habaiwa.goetyrevenant.common.network;

import com.habaiwa.goetyrevenant.common.soul.Possession;
import com.habaiwa.goetyrevenant.common.soul.SoulData;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/** C2S：灵魂状态下**左键** → 附身最近的一具可附身身体。 */
public class PossessRequestMessage {
    public PossessRequestMessage() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static PossessRequestMessage decode(FriendlyByteBuf buf) {
        return new PossessRequestMessage();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !SoulData.isSoul(player) || SoulData.isPossessing(player)) {
                return;
            }
            // 服务端复检：距离/类型都由 findTarget 把关
            Entity target = Possession.findTarget(player);
            if (target != null) {
                Possession.possess(player, target);
            }
        });
        context.setPacketHandled(true);
    }
}
