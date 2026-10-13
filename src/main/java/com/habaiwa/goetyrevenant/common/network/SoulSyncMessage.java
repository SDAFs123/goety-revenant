package com.habaiwa.goetyrevenant.common.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C：把「这名玩家现在是不是**回魂的灵魂状态**」以及灵魂自己的数值同步给客户端。
 *
 * <p>为什么必须同步：灵魂状态是我们自己实现的（不是原版旁观者），
 * 管理员开旁观者时不该触发灵魂的寻尸箭头、灵魂 HUD 等一切表现。
 * 客户端只认服务端下发的这个标记。</p>
 *
 * <h3>身体的位置也走这个包</h3>
 * <p>以前「身体在北边 35 格」是服务端拼好**文字**塞到 actionbar 里的。
 * 现在改成发**距离 + 方位角**两个数，客户端自己画**箭头** ——
 * 不用文字，而且朝向能跟着视角实时转。</p>
 */
public class SoulSyncMessage {
    private final boolean soul;
    private final float attunement;
    private final int energy;
    private final int soulTicks;
    private final int possessTicks;
    /** 到身体的距离（米）；-1 = 找不到身体。 */
    private final int bodyDistance;
    /** 指向身体的方位角（度，MC yaw 约定）；NaN = 没有方向（没身体 / 不同维度）。 */
    private final float bodyBearing;
    /** 灵魂能量上限（基础量 + 图腾 / 灵魂方舟，由服务端按本体配置算出来）。 */
    private final int energyMax;
    /** 是不是**出窍游离**（主动出窍）—— 这种不追踪尸体，也不高亮。 */
    private final boolean drifting;

    public SoulSyncMessage(boolean soul, float attunement, int energy, int soulTicks, int possessTicks,
                           int bodyDistance, float bodyBearing, int energyMax, boolean drifting) {
        this.soul = soul;
        this.attunement = attunement;
        this.energy = energy;
        this.soulTicks = soulTicks;
        this.possessTicks = possessTicks;
        this.bodyDistance = bodyDistance;
        this.bodyBearing = bodyBearing;
        this.energyMax = energyMax;
        this.drifting = drifting;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.soul);
        buf.writeFloat(this.attunement);
        buf.writeVarInt(this.energy);
        buf.writeVarInt(this.soulTicks);
        buf.writeVarInt(this.possessTicks);
        buf.writeVarInt(this.bodyDistance + 1);
        buf.writeFloat(this.bodyBearing);
        buf.writeVarInt(this.energyMax);
        buf.writeBoolean(this.drifting);
    }

    public static SoulSyncMessage decode(FriendlyByteBuf buf) {
        return new SoulSyncMessage(buf.readBoolean(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readFloat(),
                buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.habaiwa.goetyrevenant.client.ClientSoulState.accept(
                        this.soul, this.attunement, this.energy, this.soulTicks, this.possessTicks,
                        this.bodyDistance, this.bodyBearing, this.energyMax, this.drifting)));
        context.setPacketHandled(true);
    }
}
