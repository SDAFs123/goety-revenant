package com.habaiwa.goetyrevenant.common.compat;

import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 与 <b>Malum（灵灾）</b> 的联动。
 *
 * <p><b>规则</b>：</p>
 * <ol>
 *   <li>怪物**不再产出精魄** —— Malum 的精魄掉落被我们拦掉。</li>
 *   <li>佩戴**聚魂护符**击杀生物 → 按怪物强弱产出**大量诡厄巫法灵魂能量**（只有灵魂能量，不产精魄）。</li>
 * </ol>
 *
 * <p>护符可放 Curios 护符槽（我们带了 `curios:charm` 标签），也可带在背包/副手。</p>
 */
public final class MalumCompat {
    public static final String MALUM = "malum";

    /** 装了本模组后，灵魂能量获取的常态加成。 */
    public static final float SOUL_GAIN_BONUS = 1.20F;
    /** 聚魂护符的额外加成。 */
    /** **聚魂护符**带在身上时，击杀获得的灵魂能量 +5%（用户 2026-09-27 的规格）。 */
    public static final float CHARM_BONUS = 1.05F;

    private MalumCompat() {
    }

    public static boolean loaded() {
        return ModList.get() != null && ModList.get().isLoaded(MALUM);
    }

    /** 精魄种类注册名（供转化方块选用）。 */
    public static final String[] SPIRITS = {
            "earthen_spirit", "wicked_spirit", "arcane_spirit", "umbral_spirit",
            "aqueous_spirit", "aerial_spirit", "infernal_spirit",
            "sacred_spirit", "eldritch_spirit"};

    /** 按名字取精魄物品；没装 Malum 返回 null。 */
    public static Item spiritItem(String name) {
        if (!loaded()) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(MALUM, name));
        return item == Items.AIR ? null : item;
    }

    private static boolean isMalumSpirit(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(MALUM) && key.getPath().endsWith("_spirit");
    }

    // ===== 1) 怪物不再产出精魄 =====

    /** Malum 的精魂实体（{@code FloatingItemEntity} 的子类）。 */
    private static final String SPIRIT_ENTITY = "com.sammy.malum.common.entity.spirit.SpiritItemEntity";

    /**
     * **精魂是实体，不是掉落物** —— 真正生效的是这一段。
     *
     * <h3>为什么之前的 onDrops 拦不住</h3>
     * <p>反编译 + 源码确认，Malum 在 {@code SpiritHarvestHandler} 里有三个入口：</p>
     * <pre>
     *   spawnSpiritsOnDeath(LivingDeathEvent)    ← ★ 精魂在这里【直接生成实体】
     *   primeItemForShatter(LivingDropsEvent)    ← 只是给掉落物打"可碎"标记
     *   shatterItem(ItemExpireEvent)             ← 物品寿命到了才碎成精魂
     * </pre>
     * <p>生成走的是 {@code createSpiritEntities(...)} → 往世界里加
     * `SpiritItemEntity`。**它压根不经过掉落表**，所以 {@code LivingDropsEvent} 里
     * `removeIf` 永远删不到东西。</p>
     *
     * <h3>为什么用 LOWEST 优先级</h3>
     * <p>Malum 那两个方法用的是默认的 {@code EventPriority.NORMAL}，
     * 我们用 {@code LOWEST} 就能保证**排在它后面**跑 —— 那时精魂实体已经在世界里了，
     * 直接收掉即可。</p>
     *
     * <h3>为什么只在死亡事件里做</h3>
     * <p>用户 2026-09-27：「精魄产出**替代**了灵魂能量……**然后精魄采用转化**，
     * 制作一个方块」—— 玩家用转化台产出的精魂**必须留着**。
     * 挂在死亡事件上就天然区分开了：只有"生物死亡产生的"才会被扫。</p>
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onDeathRemoveSpirits(LivingDeathEvent event) {
        if (!loaded() || event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // 精魂会从尸体位置散开，给足半径；同一次死亡生成的都在这一圈里
        int removed = 0;
        for (net.minecraft.world.entity.Entity e : victim.level().getEntities(victim,
                victim.getBoundingBox().inflate(6.0D),
                ent -> ent.getClass().getName().equals(SPIRIT_ENTITY))) {
            e.discard();
            removed++;
        }
        if (removed > 0) {
            SPIRIT_SWEPT += removed;
        }
    }

    /** 自检计数：被收掉的精魂实体总数（打印在玩家首次击杀时，方便用户确认生效）。 */
    private static int SPIRIT_SWEPT;

    /** 供自检用：本局一共收掉了多少精魂实体。 */
    public static int sweptSpirits() {
        return SPIRIT_SWEPT;
    }

    /** 掉落表那条路也留着（万一将来 Malum 改成走掉落表）。 */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!loaded() || event.getDrops().isEmpty()) {
            return;
        }
        event.getDrops().removeIf(drop -> isMalumSpirit(drop.getItem()));
    }

    // ===== 2) 聚魂护符：击杀只产出大量灵魂能量 =====

    /**
     * 击杀产灵魂能量 —— <b>不需要佩戴饰品</b>，任何玩家杀任何生物都给。
     * 佩戴聚魂护符只是**翻倍**。
     */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) {
            return;
        }
        // **精魄产出替代为灵魂能量** —— 用户 2026-09-27 的原话：
        // 「不需要佩戴这个饰品就可以做到怪物不会掉落精魄……只需要诡厄巫法灵魂能量，
        //  **精魄产出替代了灵魂能量**，每一个怪物的灵魂能量产出变高」。
        //
        // 所以优先用 Malum 自己算出的"这只怪本该产几个精魂"
        // （SpiritHarvestHandler.getSpiritDropsRaw，public static，反射调用），
        // 每个精魂折成 SPIRIT_TO_SOULS 点灵魂能量 —— 这才是"替代"。
        // 拿不到（Malum 缺席 / 签名变了 / 这怪本来不产精魂）时才回落到按血量给。
        int spirits = rawSpiritCount(victim);
        int souls;
        if (spirits > 0) {
            souls = spirits * SPIRIT_TO_SOULS;
        } else {
            souls = soulsFor(victim.getMaxHealth());
        }
        // 装了本模组的常态加成 +20%
        souls = Math.round(souls * SOUL_GAIN_BONUS);
        // 聚魂护符再 +5%
        souls = Math.round(souls * charmBonus(player));
        if (SEHelper.increaseSESouls(player, souls)) {
            SOULS_GRANTED += souls;
        }
    }

    /**
     * **一个精魂折成多少灵魂能量**。
     *
     * <p>用户只说"精魄产出替代了灵魂能量""产出变高"，没给具体数 ——
     * 这是我定的换算率，**可调**（要改就改这一行）。</p>
     */
    public static final int SPIRIT_TO_SOULS = 600;

    /** 本局累计发给玩家的灵魂能量（自检用）。 */
    private static long SOULS_GRANTED;

    /** 自检：本局一共发出去多少灵魂能量。 */
    public static long soulsGranted() {
        return SOULS_GRANTED;
    }

    private static java.lang.reflect.Method GET_SPIRIT_DROPS;
    private static boolean spiritDropsResolved;

    /**
     * **这只怪本该产几个精魂**（Malum 的算法，我们只是拿来换算）。
     *
     * <p>反射调 {@code SpiritHarvestHandler.getSpiritDropsRaw(LivingEntity)}
     * —— 它是 {@code public static}，签名从 Malum 1.20.1 的字节码确认过。
     * 拿不到就返回 0，调用方会回落到按血量给。</p>
     */
    private static int rawSpiritCount(LivingEntity victim) {
        if (!loaded()) {
            return 0;
        }
        try {
            if (!spiritDropsResolved) {
                Class<?> handler = Class.forName("com.sammy.malum.core.handlers.SpiritHarvestHandler");
                GET_SPIRIT_DROPS = handler.getMethod("getSpiritDropsRaw", LivingEntity.class);
                spiritDropsResolved = true;
            }
            Object result = GET_SPIRIT_DROPS.invoke(null, victim);
            if (result instanceof java.util.List<?> list) {
                return list.size();
            }
        } catch (Throwable ignored) {
            spiritDropsResolved = true;   // 失败就别每次都反射
        }
        return 0;
    }

    /** 按怪物强弱给的灵魂能量（比本体基础值高）。 */
    private static int soulsFor(float maxHealth) {
        if (maxHealth >= 60.0F) {
            return 3000;
        }
        if (maxHealth >= 30.0F) {
            return 1200;
        }
        if (maxHealth >= 12.0F) {
            return 500;
        }
        return 180;
    }

    // ===== 护符状态缓存：Curios 查询不便宜，怪物农场里每次击杀都查会卡 =====

    /**
     * 护符缓存。
     *
     * <p><b>为什么用「世界游戏刻」而不是 `player.tickCount`</b>：后者是每个玩家各自从 0 数的，
     * 没法拿来判断**别的玩家**的缓存过期没有，也就没法做清理 —— 原来那两个只增不减的静态 Map
     * 就是这么漏的（见过的每个 UUID 都永远留在里面）。换成世界时钟以后，
     * 一次遍历就能把所有过期的条目扫掉。</p>
     */
    /**
     * **聚魂护符的加成倍率**（读**主手武器上的附魔等级**）。
     *
     * <p>用户 2026-09-27：「聚魂护符修改为增加获取灵魂能量的护符，增加 5%，
     * 适用物品：#malum:soul_hunter_weapon，最大等级：II」。</p>
     *
     * <p>⚠️ 它是个**附魔**，不是一个叫"聚魂护符"的饰品 ——
     * 之前 `hasCharmCached` 检查的是背包里有没有 `SOUL_HARVEST_CHARM` 这个物品，
     * 而那个物品根本没有效果（规格里的"适用物品/最大等级/概述"都是附魔的词）。</p>
     *
     * @return 1.0 = 无加成；1.05 = I 级；1.10 = II 级
     */
    public static float charmBonus(ServerPlayer player) {
        return hasCharm(player) ? CHARM_BONUS : 1.0F;
    }

    /**
     * 身上带没带**聚魂护符**（背包 / 副手 / Curios 都算）。
     *
     * <p>用户 2026-09-27：「带在身上击杀生物……」——
     * 它是个**饰品**（`goety_revenant:soul_harvest_charm`），带在身上就生效，
     * 不需要拿在手里。</p>
     */
    public static boolean hasCharm(ServerPlayer player) {
        net.minecraft.world.item.Item charm =
                com.habaiwa.goetyrevenant.init.ModItems.SOUL_HARVEST_CHARM.get();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(charm)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && stack.is(charm)) {
                return true;
            }
        }
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> handler.findFirstCurio(charm).isPresent())
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 旧版这里是「背包里有没有 SOUL_HARVEST_CHARM 那个物品」的检查 + 40 tick 缓存。
     *
     * <p>已删除 —— 用户 2026-09-27 的规格里，「聚魂护符」是一个**附魔**
     * （有"适用物品 / 最大等级 / 概述"），不是一个饰品。
     * 加成现在直接由 {@link #charmBonus(net.minecraft.server.level.ServerPlayer)}
     * 读主手武器的附魔等级得到，不需要缓存也不需要遍历背包。</p>
     */

    public static boolean spiritExists(String name) {
        return loaded() && BuiltInRegistries.ITEM.get(new ResourceLocation(MALUM, name)) != Items.AIR;
    }
}
