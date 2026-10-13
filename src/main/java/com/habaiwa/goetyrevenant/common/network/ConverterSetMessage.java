package com.habaiwa.goetyrevenant.common.network;

import com.habaiwa.goetyrevenant.common.block.SpiritConverterBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/** C2S：在界面里勾完精魄后回传（服务端会重新校验方块与范围）。 */
public class ConverterSetMessage {
    private final BlockPos pos;
    /** 9 位掩码：第 i 位 = 第 i 种精魄要不要产。 */
    private final int mask;

    public ConverterSetMessage(BlockPos pos, int mask) {
        this.pos = pos;
        this.mask = mask;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.mask);
    }

    public static ConverterSetMessage decode(FriendlyByteBuf buf) {
        return new ConverterSetMessage(buf.readBlockPos(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // 服务端复检：方块要在玩家附近，而且真的是转化台
            if (player.distanceToSqr(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D) > 64.0D) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(this.pos);
            if (blockEntity instanceof SpiritConverterBlockEntity converter) {
                converter.setSelectedMask(this.mask);
                RevenantNetwork.sendConverterSync(player, this.pos, converter);
            }
        });
        context.setPacketHandled(true);
    }
}
