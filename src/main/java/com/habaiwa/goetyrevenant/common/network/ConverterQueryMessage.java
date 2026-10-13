package com.habaiwa.goetyrevenant.common.network;

import com.habaiwa.goetyrevenant.common.block.SpiritConverterBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/**
 * C2S：界面开着的时候定期问一次「现在笼子里还剩多少灵魂能量」。
 *
 * <p>服务端只回一条 {@link ConverterSyncMessage}，客户端拿到后**刷新已有界面**而不是重开。
 * 界面关掉后客户端自然就不再发，不需要额外的关闭回调。</p>
 */
public class ConverterQueryMessage {
    private final BlockPos pos;

    public ConverterQueryMessage(BlockPos pos) {
        this.pos = pos;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    public static ConverterQueryMessage decode(FriendlyByteBuf buf) {
        return new ConverterQueryMessage(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // 和 ConverterSetMessage 同一套复检：方块要在玩家附近，而且真的是转化台
            if (player.distanceToSqr(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D) > 64.0D) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(this.pos);
            if (blockEntity instanceof SpiritConverterBlockEntity converter) {
                RevenantNetwork.sendConverterSync(player, this.pos, converter);
            }
        });
        context.setPacketHandled(true);
    }
}
