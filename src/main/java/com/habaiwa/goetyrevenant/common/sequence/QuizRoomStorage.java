package com.habaiwa.goetyrevenant.common.sequence;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * **答题空间的进出记录** —— 存**世界**上，不存玩家身上。
 *
 * <h3>为什么要换地方存</h3>
 * <p>原来这些（从哪儿来的、人是不是在里面）都塞在 {@code player.getPersistentData()} 里。
 * 而那个 tag <b>在跨维度时不可靠</b>：玩家进那个维度时实体会被重建，
 * 数据没跟过去 —— 于是"回来的位置"读出来是 0,0,0，人就被扔在世界出生点。</p>
 *
 * <p>（我加过一个 {@code PlayerEvent.Clone} 的搬运工，但那条路显然没能救回来 ——
 * 与其继续和实体生命周期的细节较劲，不如把这份记录挪到一个<b>根本不会重建</b>的地方：
 * 主世界的存档数据。它跟着存档走，重启游戏都还在。）</p>
 *
 * <h3>存了什么</h3>
 * <pre>
 *   每个玩家一份：从哪来（维度 + 坐标 + 朝向）、是不是还在里面、有没有在转场
 * </pre>
 */
public final class QuizRoomStorage extends SavedData {
    public static final String NAME = "goety_revenant_quiz_room";

    /** 一个玩家的进出记录。 */
    public static final class Entry {
        public String dim = "minecraft:overworld";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        /** 人现在是不是在那片白里。 */
        public boolean inside;
        /** 转场待办：1 = 要进去，2 = 要出来，0 = 没有。 */
        public int pending;
        /** 转场倒计时。 */
        public int cd;
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static QuizRoomStorage get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                QuizRoomStorage::load, QuizRoomStorage::new, NAME);
    }

    /** 取（没有就建一份）。 */
    public Entry get(UUID id) {
        return this.entries.computeIfAbsent(id, k -> new Entry());
    }

    /** 看一眼，不存在就返回 null（不想凭空建）。 */
    public Entry peek(UUID id) {
        return this.entries.get(id);
    }

    public void markDirty() {
        setDirty();
    }

    /* ------------------------------------------------------------------ */

    public static QuizRoomStorage load(CompoundTag tag) {
        QuizRoomStorage data = new QuizRoomStorage();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            try {
                UUID id = one.getUUID("id");
                Entry e = new Entry();
                e.dim = one.getString("dim");
                e.x = one.getDouble("x");
                e.y = one.getDouble("y");
                e.z = one.getDouble("z");
                e.yaw = one.getFloat("yaw");
                e.pitch = one.getFloat("pitch");
                e.inside = one.getBoolean("inside");
                e.pending = one.getInt("pending");
                e.cd = one.getInt("cd");
                data.entries.put(id, e);
            } catch (Exception ignored) {
                // 单条坏了就跳过，别让整个存档读不出来
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> e : this.entries.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("id", e.getKey());
            Entry v = e.getValue();
            one.putString("dim", v.dim == null ? "minecraft:overworld" : v.dim);
            one.putDouble("x", v.x);
            one.putDouble("y", v.y);
            one.putDouble("z", v.z);
            one.putFloat("yaw", v.yaw);
            one.putFloat("pitch", v.pitch);
            one.putBoolean("inside", v.inside);
            one.putInt("pending", v.pending);
            one.putInt("cd", v.cd);
            list.add(one);
        }
        tag.put("players", list);
        return tag;
    }
}
