package com.habaiwa.goetyrevenant.common.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** S2C：某玩家附身了生物（changed=true 带实体 id）或取消了附身外观（id 空串）。 */
public class PossessAppearanceMessage {
    private final UUID player;
    private final String entityTypeId;
    /** 生物自己的存档数据 —— 变体（颜色/花纹/大小/职业）都在里面。 */
    private final net.minecraft.nbt.CompoundTag data;

    public PossessAppearanceMessage(UUID player, String entityTypeId) {
        this(player, entityTypeId, null);
    }

    public PossessAppearanceMessage(UUID player, String entityTypeId, net.minecraft.nbt.CompoundTag data) {
        this.player = player;
        this.entityTypeId = entityTypeId == null ? "" : entityTypeId;
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
        buf.writeUtf(this.entityTypeId);
        buf.writeNbt(this.data);
    }

    public static PossessAppearanceMessage decode(FriendlyByteBuf buf) {
        return new PossessAppearanceMessage(buf.readUUID(), buf.readUtf(), buf.readNbt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.PossessedAppearance.set(
                        this.player, this.entityTypeId, this.data)));
        context.setPacketHandled(true);
    }
}
