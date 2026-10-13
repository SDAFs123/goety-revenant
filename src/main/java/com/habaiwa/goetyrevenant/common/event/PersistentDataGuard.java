package com.habaiwa.goetyrevenant.common.event;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * **别把玩家身上的东西弄丢了。**
 *
 * <h3>问题的来龙去脉</h3>
 * <p>这个模组把几乎所有玩家数据都塞在 {@code player.getPersistentData()} 里：
 * 觉醒的序列、开窍状态、强化槽、灵魂温度、答题进度、跳井前的位置……</p>
 *
 * <p>而 Forge 的这个 tag <b>在玩家实体被重建时不会自动跟过去</b>。
 * 玩家跨维度（跳进那片白、再回来）时，服务端会<b>新建一个玩家实体</b>、
 * 把旧实体的数据"搬"过去 —— 但那个搬运清单里<b>没有</b> persistentData。</p>
 *
 * <h3>于是会看到什么</h3>
 * <ul>
 *   <li>传送回原来的地方，人却出现在世界出生点（位置读出来是 0,0,0）</li>
 *   <li>觉醒过的序列变回"未觉醒"</li>
 *   <li>开好的窍又锁上、选的被动全空</li>
 * </ul>
 *
 * <h3>怎么办</h3>
 * <p>Forge 为这种情况留了 {@link PlayerEvent.Clone} —— 在实体重建的那一刻触发，
 * 把旧实体的数据交给新实体。这里把那整个 tag 搬一次就完事。</p>
 *
 * <p>（死亡重生也走同一个事件，同样要搬 —— 身体的改造不该因为死一次就没了。）</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID)
public final class PersistentDataGuard {
    private PersistentDataGuard() {
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag from = event.getOriginal().getPersistentData();
        CompoundTag to = event.getEntity().getPersistentData();
        if (from == null || to == null || from.isEmpty()) {
            return;
        }
        // 新数据优先（万一新实体上已经有东西了），旧数据补齐缺的那些
        CompoundTag merged = from.copy();
        merged.merge(to);
        to.getAllKeys().forEach(to::remove);
        to.merge(merged);
    }
}
