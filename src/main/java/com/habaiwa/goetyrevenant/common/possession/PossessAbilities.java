package com.habaiwa.goetyrevenant.common.possession;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;

/**
 * **附身能力继承** —— 玩家要有这具身体的**全部特性**。
 *
 * <h3>为什么不逐个生物写表</h3>
 * <p>整合包里几十上百种生物（还带模组），逐个硬编码是不可能的。
 * 所以全部用**通用判据**：</p>
 * <ul>
 *   <li><b>飞行</b>：{@code FlyingMob}（恶魂、幻翼）/ {@code FlyingAnimal}（蝙蝠、鹦鹉、蜜蜂）
 *       —— 模组飞行生物只要继承这两个基类就自动算数</li>
 *   <li><b>水生</b>：{@code WaterAnimal}（鱼、鱿鱼、海豚）→ 水下呼吸 + 游泳速度</li>
 *   <li><b>火焰免疫</b>：实体自己的 {@code fireImmune()}（烈焰人、岩浆怪、凋灵）</li>
 *   <li><b>亡灵</b>：{@code MobType.UNDEAD}（僵尸、骷髅、凋灵骷髅，模组亡灵也一样）→ 白天燃烧</li>
 *   <li><b>节肢</b>：{@code MobType.ARTHROPOD}（蜘蛛、洞穴蜘蛛、蠹虫）→ <b>会爬墙</b></li>
 *   <li><b>摔落免疫</b>：飞行生物 + 猫科（猫、豹猫、劫掠兽？不 —— 蜘蛛与猫）</li>
 *   <li><b>跳跃</b>：兔子、山羊 → 跳跃提升</li>
 * </ul>
 *
 * <p>能力标记写进玩家 persistent data（位掩码），这样伤害事件也能查到。</p>
 */
public final class PossessAbilities {
    /** 位掩码存在这里。 */
    private static final String KEY_MASK = "GRPossessAbilityMask";
    /** 能力标记。 */
    public static final int BIT_FLY = 1;
    public static final int BIT_FALL_IMMUNE = 1 << 1;
    public static final int BIT_WATER_BREATH = 1 << 2;
    public static final int BIT_FIRE_IMMUNE = 1 << 3;
    public static final int BIT_CLIMB = 1 << 4;
    public static final int BIT_JUMP = 1 << 5;
    /** 弹跳移动（史莱姆 / 岩浆怪：一蹦一蹦） */
    public static final int BIT_BOUNCE = 1 << 6;
    /** 离水扑腾（鱼上岸以后会蹦跶，能蹦回水里） */
    public static final int BIT_FLOP = 1 << 7;
    /** 横向姿态（鱼/海豚那样横着游，第一人称也跟着躺平） */
    public static final int BIT_HORIZONTAL = 1 << 8;

    private PossessAbilities() {
    }

    /* ------------------------------------------------------------------ */
    /*  一次性给上                                                          */
    /* ------------------------------------------------------------------ */

    public static void apply(ServerPlayer player, LivingEntity body) {
        int mask = 0;

        // 飞行
        boolean flies = body instanceof net.minecraft.world.entity.FlyingMob
                || body instanceof net.minecraft.world.entity.animal.FlyingAnimal;
        if (flies) {
            mask |= BIT_FLY | BIT_FALL_IMMUNE;
        }
        // 水生：水下呼吸 + 离水扑腾 + 横着游的姿态
        if (body instanceof net.minecraft.world.entity.animal.WaterAnimal) {
            mask |= BIT_WATER_BREATH | BIT_FLOP | BIT_HORIZONTAL;
        }
        // 弹跳类：史莱姆 / 岩浆怪（MagmaCube 继承 Slime，一个判据全包）
        if (body instanceof net.minecraft.world.entity.monster.Slime) {
            mask |= BIT_BOUNCE | BIT_FALL_IMMUNE;
        }
        // 火焰免疫（实体自己说的）
        if (body.fireImmune()) {
            mask |= BIT_FIRE_IMMUNE;
        }
        // 节肢 → 爬墙 + 摔落免疫（蜘蛛从高处掉下来不死）
        if (body.getMobType() == MobType.ARTHROPOD) {
            mask |= BIT_CLIMB | BIT_FALL_IMMUNE;
        }
        // 猫科 → 轻功（猫永远摔不死）
        if (body instanceof net.minecraft.world.entity.animal.Cat) {
            mask |= BIT_FALL_IMMUNE;
        }
        // 兔子 / 山羊 → 跳跃
        if (body instanceof net.minecraft.world.entity.animal.Rabbit
                || body instanceof net.minecraft.world.entity.animal.goat.Goat) {
            mask |= BIT_JUMP;
        }
        // 亡灵 → 白天燃烧（既有逻辑，这里只记标记）
        if (body.getMobType() == MobType.UNDEAD) {
            mask |= BIT_FALL_IMMUNE;   // 亡灵不摔伤也是设定的一部分（骷髅散架另有处理）
        }

        player.getPersistentData().putInt(KEY_MASK, mask);
        applyEffects(player, mask);
        applyFlight(player, mask);
    }

    /** 效果类能力（要一直挂着，所以无限时长 + 不可见）。 */
    private static void applyEffects(ServerPlayer player, int mask) {
        if ((mask & BIT_WATER_BREATH) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, Integer.MAX_VALUE, 0, true, false, false));
            // 水生生物在水里更快
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, Integer.MAX_VALUE, 0, true, false, false));
        }
        if ((mask & BIT_FIRE_IMMUNE) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false, false));
        }
        if ((mask & BIT_JUMP) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, Integer.MAX_VALUE, 1, true, false, false));
        }
    }

    private static void applyFlight(ServerPlayer player, int mask) {
        boolean fly = (mask & BIT_FLY) != 0;
        if (fly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  每 tick 维持                                                        */
    /* ------------------------------------------------------------------ */

    public static void tick(ServerPlayer player) {
        int mask = player.getPersistentData().getInt(KEY_MASK);
        if (mask == 0) {
            return;
        }
        // 效果可能被别的模组洗掉 —— 掉了就补
        if ((mask & BIT_WATER_BREATH) != 0 && !player.hasEffect(MobEffects.WATER_BREATHING)) {
            applyEffects(player, mask);
        }
        if ((mask & BIT_FIRE_IMMUNE) != 0 && !player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false, false));
        }
        if ((mask & BIT_JUMP) != 0 && !player.hasEffect(MobEffects.JUMP)) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, Integer.MAX_VALUE, 1, true, false, false));
        }
        if ((mask & BIT_FLY) != 0 && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
        // **弹跳移动**（史莱姆）：原版史莱姆根本不"走"，它是靠一下一下弹的。
        // 站在地上就按玩家输入的方向弹出去，间隔照着原版的跳跃延迟来。
        if ((mask & BIT_BOUNCE) != 0 && player.onGround() && player.tickCount % 10 == 0) {
            float fwd = player.zza;
            float side = player.xxa;
            if (fwd != 0.0F || side != 0.0F) {
                double len = Math.sqrt(fwd * fwd + side * side);
                double f = fwd / len;
                double s = side / len;
                double yaw = Math.toRadians(player.getYRot());
                double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
                double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
                double power = 0.42D;
                player.setDeltaMovement(dx * power * 1.2D, power, dz * power * 1.2D);
                player.hasImpulse = true;
            }
        }

        // **离水扑腾**（鱼上岸）：原版鱼在岸上会一下一下弹 —— 靠着这个能蹦回水里。
        if ((mask & BIT_FLOP) != 0 && !player.isInWater() && player.onGround()
                && player.tickCount % 8 == 0) {
            player.setDeltaMovement(player.getDeltaMovement().x + player.xxa * 0.06D, 0.32D,
                    player.getDeltaMovement().z + player.zza * 0.06D);
            player.hasImpulse = true;
        }

        // **横向姿态**（鱼/海豚）：身体是横的，第一人称也跟着躺平 —— 靠 SWIMMING 姿态实现。
        // 每 tick 设一次，因为原版 updatePlayerPose 会按它自己的规则覆盖回去。
        if ((mask & BIT_HORIZONTAL) != 0 && !player.isPassenger()) {
            player.setSwimming(true);
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        }

        // **爬墙**：原版蜘蛛的做法 —— 贴着墙（水平碰撞）且往前推就给一个向上的速度。
        if ((mask & BIT_CLIMB) != 0 && player.horizontalCollision && !player.onGround()
                && player.zza > 0.0F && !player.isInWater()) {
            player.setDeltaMovement(player.getDeltaMovement().x,
                    Math.min(player.getDeltaMovement().y + 0.2D, 0.8D),
                    player.getDeltaMovement().z);
            player.fallDistance = 0.0F;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  查询 / 撤销                                                         */
    /* ------------------------------------------------------------------ */

    public static boolean has(ServerPlayer player, int bit) {
        return (player.getPersistentData().getInt(KEY_MASK) & bit) != 0;
    }

    public static void clear(ServerPlayer player) {
        player.getPersistentData().putInt(KEY_MASK, 0);
        // 横向姿态的收尾：退出游泳状态，姿态交回原版逻辑
        player.setSwimming(false);
        player.setPose(net.minecraft.world.entity.Pose.STANDING);
        player.removeEffect(MobEffects.WATER_BREATHING);
        player.removeEffect(MobEffects.DOLPHINS_GRACE);
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.removeEffect(MobEffects.JUMP);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
    }
}
