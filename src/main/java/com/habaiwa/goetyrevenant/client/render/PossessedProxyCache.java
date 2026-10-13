package com.habaiwa.goetyrevenant.client.render;

import com.habaiwa.goetyrevenant.client.PossessedAppearance;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * **附身代理实体的缓存**。
 *
 * <h3>为什么要有这个</h3>
 * <p>附身了生物时，玩家要画成那个生物的样子。做法是造一个"类型正确的代理实体"
 * 交给那个生物的渲染器 —— 因为大量模组生物的渲染器会把自己的实体强转成自己的类，
 * 把玩家直接塞进去会 ClassCastException，而代理永远类型正确。</p>
 *
 * <p>但原来这个代理是<b>每一帧现造的</b>：新建实体、读变体 NBT、复制全套装备，
 * 然后才交给渲染器。这个成本随"屏幕上可见的附身玩家数 × 帧率"增长，
 * 而且每帧重建也破坏了实体内部状态的连续性（动画、tick 计数）。</p>
 *
 * <p>所以：<b>代理按玩家缓存</b>，只在"附身的生物类型变了"或"换了世界"时重建。
 * 每帧只做状态同步（位置、朝向、装备），那部分很便宜。</p>
 */
public final class PossessedProxyCache {
    private PossessedProxyCache() {
    }

    /** 一个代理。 */
    private record Entry(LivingEntity mob, ResourceLocation type, ResourceLocation dimension) {
    }

    private static final Map<UUID, Entry> CACHE = new HashMap<>();

    /**
     * **拿到这个玩家当前的代理** —— 能复用就复用，必要时重建。
     *
     * @return 代理实体；类型解析不出来时返回 null（调用方退回玩家模型）
     */
    public static LivingEntity get(AbstractClientPlayer player, ResourceLocation typeId) {
        if (player == null || player.level() == null || typeId == null) {
            return null;
        }
        ResourceLocation dim = player.level().dimension().location();
        Entry old = CACHE.get(player.getUUID());
        // **命中条件：类型没变、世界没变** —— 这两样变了才需要重建
        if (old != null && old.type().equals(typeId) && old.dimension().equals(dim)) {
            return old.mob();
        }
        // 重建
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        if (type == null) {
            return null;
        }
        net.minecraft.world.entity.Entity made = type.create(player.level());
        if (!(made instanceof LivingEntity mob)) {
            return null;
        }
        // 变体数据（羊的颜色、猫的品种、史莱姆大小、村民职业……）只在重建时喂一次
        CompoundTag data = PossessedAppearance.data(player.getUUID());
        if (data != null) {
            try {
                mob.readAdditionalSaveData(data);
            } catch (Throwable ignored) {
                // 数据不吃就当默认形态
            }
        }
        CACHE.put(player.getUUID(), new Entry(mob, typeId, dim));
        return mob;
    }

    /** 这个玩家的代理如果存在就丢掉（离开附身、换世界时用）。 */
    public static void drop(UUID id) {
        CACHE.remove(id);
    }

    /** **全部丢掉** —— 断线/换世界时调（实体绑着旧世界，带着走会出问题）。 */
    public static void reset() {
        CACHE.clear();
    }

    /** 顺手清掉那些已经不在世界里的玩家（防止缓存无限增长）。 */
    public static void prune(java.util.Set<UUID> alive) {
        Iterator<Map.Entry<UUID, Entry>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            if (!alive.contains(it.next().getKey())) {
                it.remove();
            }
        }
    }
}
