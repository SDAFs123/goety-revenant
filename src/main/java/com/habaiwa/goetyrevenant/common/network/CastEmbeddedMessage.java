package com.habaiwa.goetyrevenant.common.network;

import com.habaiwa.goetyrevenant.common.body.EmbeddedCasting;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C2S：轮盘选中某个器官 → 施放它里面嵌的聚晶。规则全在 {@link EmbeddedCasting} 里。 */
public class CastEmbeddedMessage {
    private final String organ;

    public CastEmbeddedMessage(String organ) {
        this.organ = organ;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.organ);
    }

    public static CastEmbeddedMessage decode(FriendlyByteBuf buf) {
        return new CastEmbeddedMessage(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            for (OrganType organ : OrganType.values()) {
                if (organ.getId().equals(this.organ)) {
                    EmbeddedCasting.cast(player, organ);
                    return;
                }
            }
        });
        context.setPacketHandled(true);
    }
}
