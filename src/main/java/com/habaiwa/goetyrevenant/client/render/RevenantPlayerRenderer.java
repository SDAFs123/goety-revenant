package com.habaiwa.goetyrevenant.client.render;

import com.habaiwa.goetyrevenant.client.ClientSkinOverride;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 覆写过的玩家渲染器：附身了别人的身体，就用别人的皮肤。
 *
 * <p>其余行为完全继承原版 {@link PlayerRenderer}（含粗细模型、护甲层、手持物）。</p>
 */
public class RevenantPlayerRenderer extends PlayerRenderer {
    // ======================================================================
    //  接入方式（审查后重做的）
    //
    //  原来是在 RegisterRenderers 里用 registerEntityRenderer(EntityType.PLAYER, ...)
    //  注册的。那条路走不通：玩家渲染走的是皮肤渲染器映射（default / slim 两套），
    //  普通实体类型注册分发不到那里 —— 也就是说，这个类写好了但从来没被调用过。
    //
    //  Forge 也没有提供"替换玩家渲染器"的接口（AddLayers 只给 getSkin()，内部 map 是 private final）。
    //
    //  所以现在这样接：从 AddLayers 借一个 Context，自己造渲染器实例，
    //  然后在 RenderPlayerEvent.Pre 里【只在需要的时候】接管 ——
    //  不需要接管的玩家（绝大多数）完全走原版，一点不受影响。
    // ======================================================================

    /** 从 AddLayers 借来的渲染上下文。 */
    private static EntityRendererProvider.Context context;

    /** 两个模型各缓存一个实例 —— 不是每帧新建。 */
    private static RevenantPlayerRenderer wide;
    private static RevenantPlayerRenderer slim;

    /** 由 ClientEvents 在 AddLayers 里调用。 */
    public static void captureContext(EntityRendererProvider.Context ctx) {
        context = ctx;
    }

    /** 这个玩家需不需要我们接管渲染。 */
    public static boolean needsOverride(AbstractClientPlayer player) {
        if (context == null || player == null) {
            return false;
        }
        // ① 附身了生物 → 要画成那个生物
        if (com.habaiwa.goetyrevenant.client.PossessedAppearance.get(player.getUUID()) != null) {
            return true;
        }
        // ② 附身了别的玩家 → 要用对方的皮肤
        return ClientSkinOverride.get(player.getUUID()) != null;
    }

    /** 取（或造）对应模型的渲染器实例。 */
    public static RevenantPlayerRenderer forPlayer(AbstractClientPlayer player) {
        boolean isSlim = "slim".equals(player.getModelName());
        if (isSlim) {
            if (slim == null) {
                slim = new RevenantPlayerRenderer(context, true);
            }
            return slim;
        }
        if (wide == null) {
            wide = new RevenantPlayerRenderer(context, false);
        }
        return wide;
    }

    public RevenantPlayerRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, slim);
    }

    /**
     * 附身生物的外貌 —— **通用做法：正确类型的代理实体 + 公开 API 同步**。
     *
     * <h3>为什么不用"把玩家直接交给生物 renderer"（变形模组的原始做法）</h3>
     * <p>那样动画天然同步，但<b>大量模组生物的 renderer 会强转成自己的类</b>
     * （{@code (MyMob) entity}）—— 传玩家进去就 ClassCastException。
     * 一个整合包几十上百种生物，逐个打补丁是不可能的。</p>
     *
     * <h3>为什么代理实体是通用的</h3>
     * <p>代理是用 {@code type.create(level)} 造出来的，<b>类型永远正确</b> ——
     * 原版生物、模组生物、GeckoLib 生物，它们的 renderer 全都拿到对的东西，永远不强转失败。
     * 代价只是"要把玩家的状态喂给它"，而这件事用<b>公开 API</b> 就能做完
     * （上一版失败是因为去碰受保护的字段，这次全走 setter）。</p>
     *
     * <p>灵魂**不渲染** —— 服务端 {@code setInvisible(true)} 已让对方完全看不见。</p>
     */
    @Override
    public void render(AbstractClientPlayer entity, float entityYaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        net.minecraft.resources.ResourceLocation id =
                com.habaiwa.goetyrevenant.client.PossessedAppearance.get(entity.getUUID());
        if (id != null && !entity.isInvisible() && entity.level() != null) {
            // **代理走缓存** —— 不再每帧新建实体、重读变体 NBT、重搬整套装备。
            // 那些成本随"屏幕上可见的附身玩家数 × 帧率"增长，而且破坏实体内部状态的连续性。
            // 现在只在"附身的生物类型变了"或"换了世界"时重建（见 PossessedProxyCache）。
            net.minecraft.world.entity.LivingEntity mob =
                    PossessedProxyCache.get(entity, id);
            if (mob != null) {
                {
                    syncFromPlayer(mob, entity);
                    net.minecraft.client.renderer.entity.EntityRenderer<?> er =
                            Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
                    if (er instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> raw) {
                        try {
                            ((net.minecraft.client.renderer.entity.LivingEntityRenderer) raw)
                                    .render(mob, entityYaw, partialTick, poseStack, buffer, packedLight);
                            return;   // 画完生物，玩家本体不画
                        } catch (Throwable t) {
                            // 真遇到怪 renderer 就退回玩家模型，绝不崩（这是兜底，不是常规路径）
                            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        }
                    }
                }
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        // 注：上面少了一层 —— 代理现在是缓存来的，不再是"现造 + 判类型"两层。
    }

    /**
     * 把玩家的实时状态喂给代理 —— **只用公开 API**，所以对任何生物都一样。
     *
     * <p>位置朝向、头部朝向、走行摆动、潜行、游泳、全套装备、受伤闪红、死亡姿态。
     * 这几样齐了，别人看到的就是"一个在正常走路的生物"，而不是滑行的人偶。</p>
     */
    private static void syncFromPlayer(net.minecraft.world.entity.LivingEntity mob,
                                       AbstractClientPlayer p) {
        // 位置 + 身体朝向（copyPosition 带 yRot / xRot）
        mob.copyPosition(p);
        // 头部独立朝向：走 setter，不碰受保护字段
        mob.setYHeadRot(p.getYHeadRot());
        mob.setYBodyRot(p.yBodyRot);
        // **tick 计数要跟着玩家走** —— 很多生物的动画/AI 用 mob.tickCount 算相位，
        // 一直停在 0 的话它们会卡在动画的第一帧。
        mob.tickCount = p.tickCount;
        // 走行摆动：光设速度不够，还得【推进摆动位置】，否则腿不动或者抖。
        mob.walkAnimation.setSpeed(p.walkAnimation.speed());
        mob.walkAnimation.update(p.walkAnimation.speed(), 1.0F);
        // 上一帧的旋转状态：生物渲染器会用它做插值，不喂的话转身是瞬移的
        mob.xRotO = p.xRotO;
        mob.yRotO = p.yRotO;
        mob.yHeadRotO = p.yHeadRotO;
        mob.yBodyRotO = p.yBodyRotO;
        // 姿态类
        mob.setShiftKeyDown(p.isShiftKeyDown());
        mob.setSwimming(p.isSwimming());
        mob.setSprinting(p.isSprinting());
        mob.setOnGround(p.onGround());
        // 装备：玩家身上穿的 = 附身生物穿的（服务端已把生物的装备搬到玩家身上）
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            mob.setItemSlot(slot, p.getItemBySlot(slot).copy());
        }
        // 受伤闪红 / 死亡姿态
        mob.hurtTime = p.hurtTime;
        mob.deathTime = p.deathTime;
        // 挥手 / 攻击动画：手臂要跟着玩家动
        mob.attackAnim = p.attackAnim;
        mob.swinging = p.swinging;
        mob.swingTime = p.swingTime;
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        ClientSkinOverride.Skin skin = ClientSkinOverride.get(player.getUUID());
        if (skin != null) {
            // 带上 textures 属性才能解析出对方皮肤；只给 UUID 只会得到默认皮肤
            GameProfile profile = new GameProfile(player.getUUID(), player.getName().getString());
            profile.getProperties().put("textures",
                    new com.mojang.authlib.properties.Property("textures", skin.value(), skin.signature()));
            return Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(profile);
        }
        return super.getTextureLocation(player);
    }
}
