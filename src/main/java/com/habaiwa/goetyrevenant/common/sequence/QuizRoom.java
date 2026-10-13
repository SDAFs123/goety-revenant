package com.habaiwa.goetyrevenant.common.sequence;

import com.habaiwa.goetyrevenant.init.ModBlocks;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

/**
 * **那片白** —— 一个单独的、明亮的、纯白的维度。
 *
 * <h3>它是什么，为什么会存在</h3>
 * <p>哭泣之井的井底不是石头，是这里。井里的水一直没有流走 —— 它们都淌到了这一边，
 * 把这里洗成了白色。<b>井不是传送门，井是这片白的出口</b>：跳进去的人不会被弹回来，
 * 而是落进这里面，被问七件事。答完之后你可以自己走 —— 按离开键，白就把你吐回去。</p>
 *
 * <h3>怎么做到"没有传送门也能无缝进去"</h3>
 * <ol>
 *   <li><b>转场先糊住</b>：客户端把白涨满（约 0.7 秒），此时玩家什么都看不见；</li>
 *   <li><b>区块先备好</b>：在传送之前就把目标维度的区块强制加载出来 ——
 *       跨维度那一下之所以会卡、会黑屏，就是因为要现找现加载；先备好了就没得等；</li>
 *   <li><b>白底下换场景</b>：真正的维度切换发生在全白的那些 tick 里，玩家看不到；</li>
 *   <li><b>白退去</b>：人已经在里面了。</li>
 * </ol>
 *
 * <p>所以外面不需要摆任何传送门方块 —— 井口就是入口，而且是"穿过去"而不是"加载过去"。</p>
 */
public final class QuizRoom {
    /** 那片白的维度。 */
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(
            Registries.DIMENSION, new ResourceLocation("goety_revenant", "white_void"));
    /** 人落在这里 —— 从高空落下来。 */
    private static final double ROOM_X = 0.5D;
    private static final double ROOM_Y = 200.0D;
    private static final double ROOM_Z = 0.5D;
    /**
     * **地面高度** —— 必须和生成器对得上。
     *
     * <p>超平坦生成器是<b>从世界最低点开始铺的</b>，而这个维度 min_y = 0、铺 3 层，
     * 所以地面顶面在 y=2、人站在 y=3。之前这里写 64，于是"掉出范围"的检查
     * 在 y&lt;62 时就触发、把人一次次拎回半空 —— 那就是"被钉在空中"和"时不时黑一下"。</p>
     */
    private static final int FLOOR_Y = 3;

    /** 变暗只花这么久（够遮住换场景那一下就行，别让人等）。 */
    private static final int TRANSITION_TICKS = 6;
    /** 落进去之后的**无敌时长**（tick）—— 从天而降那一下不受伤。 */
    private static final int GRACE_TICKS = 120;
    // 这些键已经不用了 —— 状态全部挪到 QuizRoomStorage（世界的存档数据）里，
    // 因为玩家身上的东西跨维度会丢，而"从哪儿来的"这件事丢不起。

    private QuizRoom() {
    }

    /** **状态都存在世界的存档里**，不存玩家身上 —— 跨维度时实体会重建，
     *  数据跟着实体走就会丢（那正是"回程落到出生点"的原因）。 */
    private static QuizRoomStorage.Entry entry(ServerPlayer player) {
        return QuizRoomStorage.get(player.serverLevel()).get(player.getUUID());
    }

    private static void sync(ServerPlayer player) {
        QuizRoomStorage.get(player.serverLevel()).markDirty();
    }

    public static boolean inside(ServerPlayer player) {
        QuizRoomStorage.Entry e = QuizRoomStorage.get(player.serverLevel()).peek(player.getUUID());
        return e != null && e.inside;
    }

    /** 开始进入：白先涨上来，人还站在原地。 */
    public static void enter(ServerPlayer player) {
        if (inside(player)) {
            return;
        }
        prepare(player);
        if (entry(player).pending != 0) {
            return;   // 已经在转场里了
        }
        // **就在这里记下原处** —— 这是玩家刚踩进井里的那一刻。
        // 记晚了（等几 tick 再记）他人已经往下掉，回程就会落在半空。
        rememberBack(player);
        QuizRoomStorage.Entry e = entry(player);
        e.pending = 1;
        e.cd = TRANSITION_TICKS;
        sync(player);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendQuizRoom(player, 0, false);
    }

    /** 把"原处"记在玩家身上（位置 / 朝向 / 维度）。 */
    private static void rememberBack(ServerPlayer player) {
        QuizRoomStorage.Entry e = entry(player);
        e.dim = player.level().dimension().location().toString();
        e.x = player.getX();
        e.y = player.getY();
        e.z = player.getZ();
        e.yaw = player.getYRot();
        e.pitch = player.getXRot();
        sync(player);
    }

    /** 开始离开（同样是白先糊上来）。 */
    public static void beginLeave(ServerPlayer player) {
        if (!inside(player)) {
            return;
        }
        QuizRoomStorage.Entry e = entry(player);
        if (e.pending != 0) {
            return;
        }
        e.pending = 2;
        e.cd = TRANSITION_TICKS;
        sync(player);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendQuizRoom(player, 0, true);
    }

    /**
     * **提前把那边备好** —— 这一步就是"无缝"的全部秘密。
     *
     * <p>强制加载维度的区块、把房间铺好、让光照算完。等真正传送的时候，
     * 那边已经是一个现成的房间了，没有任何东西要现做。</p>
     */
    private static void prepare(ServerPlayer player) {
        ServerLevel target = player.server.getLevel(DIMENSION);
        if (target == null) {
            return;   // 维度没起来（数据包问题）—— 那就什么也不做，玩家照常被井处理
        }
        target.getChunk(0, 0);
        // 地形由维度自己的生成器铺（一整片超平坦的纯白），这里不用管
    }

    /**
     * **铺一层平地** —— 超平坦，没有墙、没有顶。
     *
     * <p>只有一层白色的地，一直铺到雾里。所以它看起来是<b>没有边界的平原</b>：
     * 眼前是平的，远处融进白里 —— 这就是"超平坦，但看得出有多远"。</p>
     */
    // 地形由维度自己的生成器铺（超平坦三层纯白），这里不再手动铺。
    // 保留这个方法名会误导后人，所以整个删掉。

    /* ------------------------------------------------------------------ */

    private static void doEnter(ServerPlayer player) {
        QuizRoomStorage.Entry e = entry(player);
        ServerLevel target = player.server.getLevel(DIMENSION);
        if (target == null) {
            // 没有那个维度 —— 别把人弄丢了
            e.pending = 0;
            sync(player);
            com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendQuizRoom(player, 1, false);
            return;
        }

        // **原处已经在 enter() 里记过了**（跳井那一刻的位置）。
        // 这里再记一次会把井口位置覆盖成"传送瞬间"的位置 —— 那样回程就落在半空了。

        // 从天而降（落到那片白平原上）
        com.habaiwa.goetyrevenant.common.event.FallGuard.beginFall(player);
        // **用返回的新实例**：跨维度之后旧的那个已经作废了
        ServerPlayer now = moveTo(player, target, ROOM_X, ROOM_Y, ROOM_Z);

        e.inside = true;
        e.pending = 0;
        sync(now);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendQuizRoom(now, 1, true);
        // **问答状态要在这里发**：跨维度会强制关掉客户端的界面，
        // 传送之前发的那些等于白发（界面刚打开就被关）。
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendQuizState(now);
    }

    private static void doLeave(ServerPlayer player) {
        // **从存档里读回来** —— 这份记录跨维度不会丢（玩家身上的会）
        QuizRoomStorage.Entry e = entry(player);
        ResourceKey<Level> back = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(e.dim == null || e.dim.isEmpty()
                        ? "minecraft:overworld" : e.dim));
        ServerLevel target = player.server.getLevel(back);
        if (target == null) {
            target = player.server.overworld();
        }
        // 注意：这里的返回值是新实例（back 这个名字被上面的维度键占了，改叫 home）
        ServerPlayer home = moveTo(player, target, e.x, e.y, e.z);

        home.setNoGravity(false);
        if (!home.isCreative() && !home.isSpectator()) {
            home.getAbilities().mayfly = false;
            home.getAbilities().flying = false;
        }
        home.onUpdateAbilities();
        // **像被井吐出来一样** —— 回程给一点向上的初速度，
        // 落到井口那一下是被"顶"出来的，而不是无声无息站回去。
        home.setDeltaMovement(0.0D, 0.62D, 0.0D);
        home.hurtMarked = true;   // 让客户端同步这个速度
        home.fallDistance = 0.0F;
        home.getPersistentData().putInt("GRGrace", GRACE_TICKS);
        home.setInvulnerable(true);
        e.inside = false;
        e.pending = 0;
        sync(home);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendQuizRoom(home, 1, false);
    }

    /** 跨维度搬人（关掉原版那套"先加载界面"的等待感：目标区块已经备好了）。 */
    private static ServerPlayer moveTo(ServerPlayer player, ServerLevel target, double x, double y, double z) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        // 【关键】changeDimension 返回的是"新玩家"实例 —— 之后所有操作都必须作用在它身上。
        // 之前这里接着用那个 player 变量（旧实体，已经作废），
        // 等于后面那些 teleportTo / 速度 / 无敌全部落在空气上。
        // changeDimension 声明的返回类型是 Entity —— 这里显式收回 ServerPlayer
        Entity movedEntity = player.changeDimension(target, new ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld,
                                      float rotationYaw, Function<Boolean, Entity> repositionEntity) {
                Entity placed = repositionEntity.apply(false);
                placed.moveTo(x, y, z, rotationYaw, 0.0F);
                return placed;
            }
        });
        ServerPlayer p = movedEntity instanceof ServerPlayer sp ? sp : player;
        // 双保险：跨维度落地那一步，原版有时会按它自己的"安全落点"再挪一次
        // （于是人就出现在目标维度的出生点附近 —— 这就是"还是世界出生点"的来路）。
        p.teleportTo(target, x, y, z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
        // **不钉在空中**：关掉无重力与飞行，让人实实在在往下落。
        p.setNoGravity(false);
        p.getAbilities().mayfly = false;
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
        // **下落无伤害**：直接用原版那套无敌（比抹坠落高度干净）。
        // 落进那片白之后的几秒里，任何伤害都进不来。
        p.setInvulnerable(true);
        p.getPersistentData().putInt("GRGrace", GRACE_TICKS);
        return p;
    }

    /** 每 tick：转场倒计时到了就在白底下换场景；在里面时别让人飘出去。 */
    public static void tick(ServerPlayer player) {
        QuizRoomStorage.Entry e = entry(player);
        int pending = e.pending;
        if (pending != 0) {
            int cd = e.cd - 1;
            e.cd = Math.max(0, cd);
            // 每次减都标脏：转场只有十几 tick，这点开销可以忽略
            sync(player);
            if (cd <= 0) {
                if (pending == 1) {
                    doEnter(player);
                } else {
                    doLeave(player);
                }
            }
            return;
        }
        if (!inside(player)) {
            return;
        }
        // 觉醒表现倒计时：走完才把人送回井边
        int show = player.getPersistentData().getInt("GRAwakenShow");
        if (show > 0) {
            player.getPersistentData().putInt("GRAwakenShow", show - 1);
            if (show - 1 <= 0) {
                beginLeave(player);
                return;
            }
        }
        // 落地了就撤掉"下落保护"
        if (com.habaiwa.goetyrevenant.common.event.FallGuard.falling(player)
                && player.onGround()) {
            com.habaiwa.goetyrevenant.common.event.FallGuard.endFall(player);
        }
        // 无敌倒计时：落到地上站稳之后就解除
        int grace = player.getPersistentData().getInt("GRGrace");
        if (grace > 0) {
            player.getPersistentData().putInt("GRGrace", grace - 1);
            player.fallDistance = 0.0F;
            if (grace - 1 <= 0) {
                player.setInvulnerable(false);
            }
        }
        if (!player.level().dimension().equals(DIMENSION)) {
            return;
        }
        // **不限制他跑多远**（平坦世界没有边），只有真的掉出世界才拉回来。
        // （之前那段"范围检查"就是把人反复拎回半空、还闪屏的元凶。）
        if (player.getY() < -4.0D) {
            player.teleportTo(player.serverLevel(), ROOM_X, ROOM_Y, ROOM_Z,
                    player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
        }
    }
}
