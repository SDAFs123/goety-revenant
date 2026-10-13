package com.habaiwa.goetyrevenant.common.compat;

import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.init.ModTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 镰刀统一适配。
 *
 * <p>1.20.1 的任何镰刀只要写进 `goety_revenant:scythes` 标签，就获得这些特性 ——
 * 不依赖任何具体模组的物品类。</p>
 *
 * <p><b>L1 灵魂能量共享</b>：镰刀命中给灵魂能量。
 * Goety 自己的镰刀已经在 `DarkScytheItem` 里给过了，**这里跳过避免双倍**。</p>
 *
 * <p><b>L2 连锁破坏</b>：镰刀挖锄头类方块时，连带破坏同高度周围的同类方块。</p>
 */
public final class ScytheCompat {
    /** 镰刀每次命中给的灵魂能量（应与 goety-item.toml 的 darkScytheSouls 一致）。 */
    public static final int SOULS_PER_HIT = 5;
    /** 连锁破坏的水平半径。 */
    /**
     * **连锁破坏的尺寸** —— 用户 2026-09-27 的原话（引自 Goety 镰刀说明）：
     * 「当打破其中的一个方块时，它会打破周围 **2x1x2** 区域内的所有其他可开采方块」。
     *
     * <p>不是 3x3 的九宫格，而是**沿玩家朝向 2 格 × 垂直朝向 2 格 × 高 1 层**的 4 格。</p>
     */
    public static final int CHAIN_LEN = 2;
    public static final int CHAIN_WIDE = 2;

    /** 防递归：连锁破坏时自己触发的 BreakEvent 不再连锁。 */
    private static boolean cascading;

    private ScytheCompat() {
    }

    public static boolean isScythe(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModTags.SCYTHES);
    }

    /** Goety 自己的镰刀（它自带结算，别重复给）。 */
    public static boolean isGoetyScythe(ItemStack stack) {
        return stack.getItem() instanceof com.Polarice3.Goety.common.items.equipment.DarkScytheItem;
    }

    /** 噬魂附魔等级（运行时按注册名查，不依赖字段名）。 */
    private static int soulEaterLevel(ItemStack stack) {
        Enchantment soulEater = BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation("goety", "soul_eater"));
        return soulEater == null ? 0 : stack.getEnchantmentLevel(soulEater);
    }

    /** 是不是**原生镰刀**（Goety / Malum 自己的）—— 它们内部各自实现过横扫与结算，我们必须跳过。 */
    public static boolean isNativeScythe(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return namespace.equals(MalumCompat.MALUM) || namespace.equals("goety");
    }

    /** 是不是 Malum 自己的镰刀（它内部已经乘过熟练度，别再乘一次）。 */
    /**
     * **不祥镰刀**（{@code goetyominous} 附属的镰刀）。
     *
     * <p>用户 2026-09-27 引的说明：「**不祥镰刀可以在没有精准采集附魔的情况下采集幽匿块**。
     * 抢夺和时运附魔在不祥镰刀上时会和精准采集冲突。」</p>
     *
     * <p>反编译该附属得到的注册名是 {@code goetyominous:wither_scythe}；这里放宽成
     * "命名空间是 goetyominous 且路径含 scythe"，免得他们改 id 就失效。</p>
     */
    public static boolean isOminousScythe(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals("goetyominous") && key.getPath().contains("scythe");
    }

    /** 幽匿系列方块（它的掉落受精准采集影响）。 */
    private static boolean isSculk(net.minecraft.world.level.block.state.BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key.getNamespace().equals("minecraft") && key.getPath().startsWith("sculk");
    }

    public static boolean isMalumNative(ItemStack stack) {
        return !stack.isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(MalumCompat.MALUM);
    }

    /**
     * Malum 的**镰刀熟练度属性**（运行时按注册名查）。
     *
     * <p>官方定义：`malum:scythe_proficiency`，**镰刀的伤害倍率，基值 1，值域 0-2048**；
     * 镰刀造成的攻击伤害乘以该值（**不含魔法伤害**）。</p>
     */
    @Nullable
    private static net.minecraft.world.entity.ai.attributes.Attribute proficiency() {
        if (!MalumCompat.loaded()) {
            return null;
        }
        // 注册表查不到会返回 null，直接用即可
        return BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(MalumCompat.MALUM, "scythe_proficiency"));
    }

    // ===== L8：镰刀伤害 × 镰刀熟练度 =====

    /**
     * 让**非 Malum 的镰刀**也吃镰刀熟练度加成。
     *
     * <p>只算**近战直击**（`getDirectEntity()` 是玩家本人）—— 这样投射物、法术等
     * **魔法伤害自然被排除**，符合「不含魔法伤害」的规则。</p>
     */
    @SubscribeEvent
    public static void onHurtDamage(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        // 只算近战直击：法术/投射物的直接来源不是玩家本人，天然被排除
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!isScythe(weapon) || isMalumNative(weapon)) {
            return;
        }
        net.minecraft.world.entity.ai.attributes.Attribute proficiency = proficiency();
        if (proficiency == null) {
            return;
        }
        double multiplier = player.getAttributeValue(proficiency);
        if (multiplier == 1.0D) {
            return;
        }
        event.setAmount((float) (event.getAmount() * multiplier));
    }

    // ===== L4：横扫 =====

    /** 横扫的攻速修饰符 UUID（固定，便于移除）。 */
    private static final java.util.UUID SWEEP_SPEED_UUID =
            java.util.UUID.nameUUIDFromBytes("goety_revenant:scythe_offhand".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /**
     * 第三方镰刀的横扫。
     *
     * <p>规则按 **Malum**（更好理解也更现代）：</p>
     * <ul>
     *   <li>范围：以被击生物碰撞箱为基础，每方向扩大 `1 + 横扫之刃等级 × 0.25` 格</li>
     *   <li>伤害：攻击力 × `(0.5 + 横扫之刃伤害比率)`</li>
     * </ul>
     *
     * <p>触发条件对齐原版：不在疾跑、站在地面、攻击冷却 ≥ 90%。
     * **原版镰刀（Goety/Malum）跳过** —— 它们内部各自already横扫过了。</p>
     */
    @SubscribeEvent
    public static void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity primary)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!isScythe(weapon) || isNativeScythe(weapon)) {
            return;
        }
        // 对齐原版横扫的触发条件
        if (player.isSprinting() || !player.onGround()) {
            return;
        }
        // **戴了精准镰刃 / 隐藏锋刃项链就禁止横扫**（用户 2026-09-27 的规格）
        if (ScytheTrinkets.hasBladeNecklace(player)) {
            return;
        }
        if (player.getAttackStrengthScale(0.5F) < 0.9F) {
            return;
        }
        doSweep(player, primary, weapon);
    }

    private static void doSweep(ServerPlayer player, net.minecraft.world.entity.LivingEntity primary, ItemStack weapon) {
        int edge = sweepingEdgeLevel(weapon);
        double radius = 1.0D + edge * 0.25D;
        net.minecraft.world.phys.AABB box = primary.getBoundingBox().inflate(radius, 0.25D, radius);

        float ratio = net.minecraft.world.item.enchantment.EnchantmentHelper.getSweepingDamageRatio(player);
        float damage = (float) (player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * (0.5D + ratio));

        boolean hitAny = false;
        for (net.minecraft.world.entity.LivingEntity victim
                : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (victim == player || victim == primary || victim instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                continue;
            }
            if (victim.hurt(player.damageSources().playerAttack(player), damage)) {
                hitAny = true;
                // 轻微击退，手感贴近原版横扫
                double dx = victim.getX() - player.getX();
                double dz = victim.getZ() - player.getZ();
                double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
                victim.push(dx / length * 0.4D, 0.1D, dz / length * 0.4D);
                // 横扫也给灵魂能量（与 L1 同规则）
                grantSouls(player, weapon);
            }
        }
        if (hitAny) {
            weapon.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        }
    }

    private static int sweepingEdgeLevel(ItemStack stack) {
        Enchantment sweeping = BuiltInRegistries.ENCHANTMENT.get(
                new ResourceLocation("minecraft", "sweeping_edge"));
        return sweeping == null ? 0 : stack.getEnchantmentLevel(sweeping);
    }

    /** 统一的灵魂能量结算（L1 与横扫共用）。 */
    private static void grantSouls(ServerPlayer player, ItemStack weapon) {
        int souls = Math.round(SOULS_PER_HIT * (1 + soulEaterLevel(weapon)) * MalumCompat.SOUL_GAIN_BONUS);
        if (MalumCompat.charmBonus(player) > 1.0F) {
            souls = Math.round(souls * MalumCompat.charmBonus(player));
        }
        SEHelper.increaseSESouls(player, souls);
    }

    // ===== L5：攻击冷却（副手空 / 戴坟墓手套，可叠加） =====

    /** 每份加成的攻速值。 */
    public static final double COOLDOWN_BONUS = 0.15D;

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // 每 20 tick 重算一次就够，避免每 tick 动属性
        if (player.tickCount % 20 != 0) {
            return;
        }
        applyCooldownBonus(player);
    }

    private static void applyCooldownBonus(ServerPlayer player) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(SWEEP_SPEED_UUID);

        // 只有手持镰刀时才给这份加成
        if (!isScythe(player.getMainHandItem())) {
            return;
        }
        double bonus = 0.0D;
        if (player.getOffhandItem().isEmpty()) {
            bonus += COOLDOWN_BONUS;
        }
        if (hasGraveGlove(player)) {
            bonus += COOLDOWN_BONUS;
        }
        if (bonus > 0.0D) {
            speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    SWEEP_SPEED_UUID, "goety_revenant.scythe_offhand", bonus,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * 是否戴着**坟墓手套**。
     * 按注册名模糊匹配（goety 命名空间 + 名字含 glove），
     * 这样不依赖具体物品 id，Goety 改名也不会崩。
     */
    private static boolean hasGraveGlove(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (isGlove(stack)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isGlove(stack)) {
                return true;
            }
        }
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> handler.getCurios().values().stream()
                            .anyMatch(slot -> {
                                for (int i = 0; i < slot.getStacks().getSlots(); i++) {
                                    if (isGlove(slot.getStacks().getStackInSlot(i))) {
                                        return true;
                                    }
                                }
                                return false;
                            }))
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isGlove(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals("goety") && key.getPath().contains("glove");
    }

    // ===== L6：附魔准入（铁砧） =====

    /**
     * 让**第三方镰刀**在铁砧上吃「剑系附魔 + 时运」。
     *
     * <p>为什么走铁砧：Forge 的准入钩子 `IForgeItem#canApplyAtEnchantingTable` 在**别人的物品类**上，
     * 我们改不了；`AnvilUpdateEvent` 是唯一能从外部改结果的入口（不需要 Mixin）。</p>
     *
     * <p>判定「剑系」的办法很干净：**这个附魔能不能附到钻石剑上** ——
     * 能，就说明它是剑系；再额外放行**时运**。</p>
     */
    @SubscribeEvent
    public static void onAnvil(net.minecraftforge.event.AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        // **两边的原生镰刀也要能互附** ——
        // 用户 2026-09-27：「让灵灾镰刀也能共享，双方是互通的，所以需要两遍都看进行兼容」。
        // 所以这里【不能】排除 isNativeScythe(left)：Malum 镰刀要能上 Goety 附魔，
        // Goety 镰刀也要能上 Malum 附魔。
        if (!isScythe(left) || right.isEmpty() || event.getPlayer() == null) {
            return;
        }

        ItemStack result = left.copy();
        java.util.Map<Enchantment, Integer> incoming =
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(right);
        boolean changed = false;
        int applied = 0;

        for (java.util.Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            if (!allowedOnScythe(enchantment) || conflicts(result, enchantment)) {
                continue;
            }
            int current = result.getEnchantmentLevel(enchantment);
            int target = current == level ? level + 1 : Math.max(current, level);
            target = Math.min(target, enchantment.getMaxLevel());
            if (target > current) {
                result.enchant(enchantment, target);
                changed = true;
                applied++;
            }
        }

        if (!changed) {
            return;
        }
        event.setOutput(result);
        event.setCost(Math.max(1, applied));
        event.setMaterialCost(1);
    }

    /** 剑系附魔（能不能附到钻石剑上）+ 时运。 */
    private static boolean allowedOnScythe(Enchantment enchantment) {
        if (isCrossModEnchantment(enchantment)) {
            return true;
        }
        if (enchantment == net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE) {
            return true;
        }
        return enchantment.canEnchant(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
    }

    /**
     * **两方的"独特附魔"** —— 允许互相附到对方的镰刀上。
     *
     * <p>命名空间是 {@code malum} 或 {@code goety} 的都放行，**除了用户点名排除的**：</p>
     *
     * <ul>
     *   <li>{@code malum:spirit_plunder}（精魂劫掠）—— 用户 2026-09-27 明确要求
     *       「灵灾的附魔这个附魔**不允许附魔出来**」。它的效果由我们的
     *       "收割 = +20% 灵魂能量获取"接管（见 {@link MalumCompat}）。</li>
     * </ul>
     */
    private static boolean isCrossModEnchantment(Enchantment enchantment) {
        ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
        if (id == null) {
            return false;
        }
        String ns = id.getNamespace();
        if (!ns.equals(MalumCompat.MALUM) && !ns.equals("goety")) {
            return false;
        }
        return !id.getPath().equals("spirit_plunder");
    }

    /**
     * 和镰刀上已有的附魔是否冲突。
     *
     * <p>{@code Enchantment#checkCompatibility} 是 **protected**，外部调不了 ——
     * 改用 {@code EnchantmentHelper#isEnchantmentCompatible(Collection, Enchantment)}，
     * 它是公开的、内部走同一套冲突规则。</p>
     */
    /**
     * **不祥镰刀：抢夺 / 时运 与 精准采集 冲突。**
     *
     * <p>用户 2026-09-27 引的说明：「抢夺和时运附魔在**不祥镰刀**上时会和精准采集冲突。」
     * —— 这是那把镰刀特有的规则，不是全局的。</p>
     */
    private static boolean ominousConflict(ItemStack stack, Enchantment candidate) {
        if (!isOminousScythe(stack)) {
            return false;
        }
        java.util.Set<Enchantment> existing =
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack).keySet();
        boolean hasSilk = existing.contains(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH);
        boolean hasLoot = existing.contains(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE)
                || existing.contains(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING);
        if (!hasSilk && !hasLoot) {
            return false;
        }
        // 已经有精准采集 → 拒绝抢夺/时运；已经有抢夺/时运 → 拒绝精准采集
        boolean candidateIsSilk = candidate == net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH;
        boolean candidateIsLoot = candidate == net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE
                || candidate == net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING;
        return (hasSilk && candidateIsLoot) || (hasLoot && candidateIsSilk);
    }

    private static boolean conflicts(ItemStack stack, Enchantment candidate) {
        if (ominousConflict(stack, candidate)) {
            return true;
        }
        java.util.Set<Enchantment> existing =
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack).keySet();
        if (existing.isEmpty()) {
            return false;
        }
        return !net.minecraft.world.item.enchantment.EnchantmentHelper.isEnchantmentCompatible(existing, candidate);
    }

    // ===== L1：镰刀命中给灵魂能量 =====

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!isScythe(weapon) || isGoetyScythe(weapon)) {
            return;
        }
        // 基础值 ×（1 + 噬魂等级），再乘本模组的常态加成
        int souls = Math.round(SOULS_PER_HIT * (1 + soulEaterLevel(weapon)) * MalumCompat.SOUL_GAIN_BONUS);
        if (MalumCompat.charmBonus(player) > 1.0F) {
            souls = Math.round(souls * MalumCompat.charmBonus(player));
        }
        SEHelper.increaseSESouls(player, souls);
    }

    // ===== L2：镰刀挖锄头类方块时连锁 =====

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (cascading || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack tool = serverPlayer.getMainHandItem();
        if (!isScythe(tool)) {
            return;
        }
        BlockState origin = event.getState();

        // **不祥镰刀：没有精准采集也能采幽匿块**（掉方块本身，而不是幽匿/经验）
        // 用户 2026-09-27 引的说明。
        if (isOminousScythe(tool)
                && isSculk(origin)
                && tool.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH) <= 0) {
            if (!event.isCanceled()) {
                event.setCanceled(true);
                level.destroyBlock(event.getPos(), false, serverPlayer);
                net.minecraft.world.level.block.Block.popResource(
                        level, event.getPos(), new ItemStack(origin.getBlock()));
                tool.hurtAndBreak(1, serverPlayer,
                        p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
            }
            return;
        }

        // 用户 2026-09-27：「镰刀可以用来打碎可以用锄头挖掘的石块，
        // **也可以用来打碎作物、灌木和茎块**」
        // 作物（CROPS）和茎块（STEMS）不属于 MINEABLE_WITH_HOE，要单独认。
        if (!origin.is(BlockTags.MINEABLE_WITH_HOE)
                && !origin.is(BlockTags.CROPS)
                && !origin.is(BlockTags.REPLACEABLE_BY_TREES)
                && !origin.is(net.minecraft.tags.BlockTags.LEAVES)) {
            return;
        }

        List<BlockPos> targets = new ArrayList<>();
        BlockPos center = event.getPos();
        // **2x1x2**（用户规格）：沿玩家朝向 CHAIN_LEN 格 × 垂直朝向 CHAIN_WIDE 格 × 高 1 层。
        // 不是 3x3 九宫格 —— 中心本身算在 2x2 里，所以连带的是"其余 3 格"。
        net.minecraft.core.Direction facing = serverPlayer.getDirection();
        int fx = facing.getStepX();
        int fz = facing.getStepZ();
        int sx = -fz;   // 垂直于朝向的"右方"
        int sz = fx;
        for (int a = 0; a < CHAIN_LEN; a++) {
            for (int b = 0; b < CHAIN_WIDE; b++) {
                int dx = fx * a + sx * b;
                int dz = fz * a + sz * b;
                if (dx == 0 && dz == 0) {
                    continue;   // 中心那块就是玩家正在挖的
                }
                BlockPos pos = center.offset(dx, 0, dz);
                if (level.getBlockState(pos).is(origin.getBlock())) {
                    targets.add(pos);
                }
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        cascading = true;
        try {
            for (BlockPos pos : targets) {
                // 交给原版销毁（会正常掉落、正常消耗耐久/工具判定）
                level.destroyBlock(pos, true, serverPlayer);
            }
        } finally {
            cascading = false;
        }
    }
}
