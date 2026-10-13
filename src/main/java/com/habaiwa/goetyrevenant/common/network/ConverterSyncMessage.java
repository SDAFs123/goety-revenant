package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** S2C：打开（或刷新）精魄转化台界面，并带上它当前的勾选、笼内灵魂能量与这一轮的进度。 */
public class ConverterSyncMessage {
    private final BlockPos pos;
    /** 9 位掩码：第 i 位 = 第 i 种精魄要不要产。 */
    private final int mask;
    /** 下方笼子的灵魂能量；-1 = 下方没有诅咒之笼。 */
    private final int cageSouls;
    /** 这一轮已经走了多少 tick（界面画进度条用）。 */
    private final int progress;

    public ConverterSyncMessage(BlockPos pos, int mask, int cageSouls, int progress) {
        this.pos = pos;
        this.mask = mask;
        this.cageSouls = cageSouls;
        this.progress = progress;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.mask);
        buf.writeVarInt(this.cageSouls + 1);
        buf.writeVarInt(this.progress);
    }

    public static ConverterSyncMessage decode(FriendlyByteBuf buf) {
        return new ConverterSyncMessage(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt() - 1,
                buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.screen.block.SpiritConverterScreen.open(
                        this.pos, this.mask, this.cageSouls, this.progress)));
        context.setPacketHandled(true);
    }
}
