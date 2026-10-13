package com.habaiwa.goetyrevenant.common.flesh;

import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.VoidShrineBlockEntity;
import com.habaiwa.goetyrevenant.init.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * **血管网络** —— 从血肉祭坛沿血管走到每一个吞噬基座。
 *
 * <p>用的是最朴素的 BFS：起点是贴着祭坛那几块血管，只沿着血管扩散，
 * 扩散到"挨着某个基座"就算通了。节点数有硬上限，摆再大也不会卡。</p>
 */
public final class BloodVessels {
    /** 找基座的半径（和本体 RitualRange 一个量级）。 */
    public static final int RANGE = 8;
    /** BFS 最多走多少块血管 —— 防止有人铺一整片。 */
    public static final int MAX_NODES = 1024;

    private BloodVessels() {
    }

    public static boolean isVessel(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.BLOOD_VESSEL.get());
    }

    /** 这块方块实体算不算"基座"（本体的 + 我们的，都算）。 */
    private static boolean isPedestal(BlockEntity be) {
        return be instanceof com.Polarice3.Goety.common.blocks.entities.PedestalBlockEntity
                && !(be instanceof DarkAltarBlockEntity)
                && !(be instanceof VoidShrineBlockEntity);
    }

    /** 祭坛周围所有的基座坐标。 */
    public static List<BlockPos> pedestals(Level level, BlockPos altar) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(
                altar.offset(-RANGE, -RANGE, -RANGE), altar.offset(RANGE, RANGE, RANGE))) {
            if (isPedestal(level.getBlockEntity(p))) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    /** 这块基座是不是我们的吞噬基座。 */
    public static boolean isDevourPedestal(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.DEVOUR_PEDESTAL.get());
    }

    /**
     * 从祭坛出发，沿血管能不能摸到这块基座。
     *
     * <p>判定"摸到"的方式：走到某块血管时，看它六面有没有直接贴着目标基座。</p>
     */
    public static boolean connected(Level level, BlockPos altar, BlockPos pedestal) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();

        // 起点：紧贴祭坛的血管
        for (Direction d : Direction.values()) {
            BlockPos p = altar.relative(d);
            if (isVessel(level, p) && seen.add(p)) {
                queue.add(p);
            }
        }

        int visited = 0;
        while (!queue.isEmpty() && visited++ < MAX_NODES) {
            BlockPos cur = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos next = cur.relative(d);
                if (next.equals(pedestal)) {
                    return true;
                }
                if (isVessel(level, next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }
}
