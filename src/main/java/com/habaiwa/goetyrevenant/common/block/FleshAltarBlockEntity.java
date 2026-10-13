package com.habaiwa.goetyrevenant.common.block;

import com.Polarice3.Goety.common.blocks.DarkAltarBlock;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.crafting.ModRecipeSerializer;
import com.Polarice3.Goety.common.crafting.RitualRecipe;
import com.Polarice3.Goety.common.ritual.RitualRequirements;
import com.habaiwa.goetyrevenant.common.flesh.BloodVessels;
import com.habaiwa.goetyrevenant.common.ritual.FleshRitual;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

/**
 * **血肉祭坛**的方块实体 —— 血肉仪式的发动机。
 *
 * <h3>本体给的便利与钉子</h3>
 * <p>{@code DarkAltarBlockEntity#checkCage()} 是 private，而且写死了
 * {@code blockState.is(ModBlocks.CURSED_CAGE_BLOCK.get())} —— 方块<b>身份</b>比较，继承也绕不过去。</p>
 * <p>但 {@code tick()} / {@code activate()} 都是 public，状态字段也几乎全 public，
 * 所以这里把两个方法搬过来，只换掉"认哪口笼子"。<b>不写 Mixin、不动本体一个字节。</b></p>
 *
 * <h3>和本体祭坛的三点不同</h3>
 * <ol>
 *   <li>正下方必须是<b>血肉之笼</b>，而且笼子里<b>必须放着血肉图腾</b></li>
 *   <li>扣的是<b>血肉能量</b>（从笼中的图腾上扣），和灵魂能量毫无关系</li>
 *   <li>每一个<b>吞噬基座</b>都必须用<b>血管</b>接到祭坛上，断了仪式不成立</li>
 * </ol>
 */
public class FleshAltarBlockEntity extends DarkAltarBlockEntity {

    /** 脚下那口血肉之笼（每 tick 重新认一次）。 */
    private FleshCageBlockEntity fleshCage;

    public FleshAltarBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /* ------------------------------------------------------------------ */
    /*  认笼子                                                             */
    /* ------------------------------------------------------------------ */

    private boolean checkFleshCage() {
        if (this.level == null) {
            return false;
        }
        BlockPos below = this.worldPosition.below();
        if (!this.level.getBlockState(below).is(com.habaiwa.goetyrevenant.init.ModBlocks.FLESH_CAGE.get())) {
            return false;
        }
        BlockEntity be = this.level.getBlockEntity(below);
        if (be instanceof FleshCageBlockEntity cage) {
            this.fleshCage = cage;
            // 图腾必须在位，而且还有点血肉能量 —— 和本体"笼里要有东西且有灵魂"一个道理
            return cage.hasTotem() && cage.energy() > 0;
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /*  结构检查：吞噬基座 + 血管连通                                        */
    /* ------------------------------------------------------------------ */

    /**
     * 附近每一个基座都必须是<b>吞噬基座</b>，而且必须有一条<b>血管</b>通到祭坛。
     *
     * @param complain 是否给玩家提示（发动时提示，仪式途中静默）
     */
    private boolean structureOk(Player who, boolean complain) {
        if (this.level == null) {
            return false;
        }
        for (BlockPos p : BloodVessels.pedestals(this.level, this.worldPosition)) {
            if (!BloodVessels.isDevourPedestal(this.level, p)) {
                if (complain && who != null) {
                    who.displayClientMessage(Component.translatable(
                            "message.goety_revenant.ritual_wrong_pedestal").withStyle(ChatFormatting.RED), true);
                }
                return false;
            }
            if (!BloodVessels.connected(this.level, this.worldPosition, p)) {
                if (complain && who != null) {
                    who.displayClientMessage(Component.translatable(
                            "message.goety_revenant.ritual_vessel_broken").withStyle(ChatFormatting.RED), true);
                }
                return false;
            }
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /*  仪式推进                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public void tick() {
        boolean flag = this.checkFleshCage();
        if (this.level == null) {
            return;
        }
        RitualRecipe running = this.getCurrentRitualRecipe();
        boolean open = false;

        if (!this.level.isClientSide) {
            ServerLevel serverWorld = (ServerLevel) this.level;

            if (running != null && !this.structureOk(this.castingPlayer, false)) {
                // 血管被挖断了 —— 静默中断
                if (this.castingPlayer != null) {
                    this.castingPlayer.displayClientMessage(Component.translatable(
                            "message.goety_revenant.ritual_vessel_broken").withStyle(ChatFormatting.RED), true);
                }
                this.stopRitual(false);
                running = null;
            }

            if (!flag) {
                // 笼子没了 / 图腾被拿走 / 能量见底
                this.stopRitual(false);
            } else if (running == null) {
                // 空转：嘴闭着
            } else {
                open = true;
                this.restoreCastingPlayer();

                double d0 = (double) this.worldPosition.getX() + this.level.random.nextDouble();
                double d1 = (double) this.worldPosition.getY() + this.level.random.nextDouble();
                double d2 = (double) this.worldPosition.getZ() + this.level.random.nextDouble();

                if (this.castingPlayer == null || !this.sacrificeFulfilled()) {
                    for (int p = 0; p < 4; ++p) {
                        serverWorld.sendParticles(ParticleTypes.FLAME, d0, d1, d2, 1, 0.0F, 0.0F, 0.0F, 0.0F);
                    }
                }

                if (this.remainingAdditionalIngredients == null) {
                    this.restoreRemainingAdditionalIngredients();
                    if (this.remainingAdditionalIngredients == null) {
                        return;
                    }
                }

                if (running.getRitual().isPending(this)) {
                    return;
                }

                IItemHandler handler = this.itemStackHandler.orElseThrow(RuntimeException::new);
                if (!running.getRitual().isValid(this.level, this.worldPosition, this, this.castingPlayer,
                        handler.getStackInSlot(0), this.remainingAdditionalIngredients)) {
                    this.stopRitual(false);
                    return;
                }

                if (this.castingPlayer == null || !this.sacrificeFulfilled()) {
                    return;
                }

                // 每 20 tick 从笼里的血肉图腾扣一次血肉能量
                if (this.level.getGameTime() % 20 == 0) {
                    if (this.fleshCage.canPay(running.getSoulCost())) {
                        this.fleshCage.pay(running.getSoulCost());
                        serverWorld.playSound(null, this.worldPosition, SoundEvents.SCULK_CATALYST_BLOOM,
                                SoundSource.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F);
                        serverWorld.sendParticles(ParticleTypes.CRIMSON_SPORE,
                                (double) this.worldPosition.getX() + 0.5D,
                                (double) this.worldPosition.getY() + 1.15D,
                                (double) this.worldPosition.getZ() + 0.5D,
                                4, 0.25D, 0.0D, 0.25D, 0.0D);
                        this.currentTime++;
                    } else {
                        this.castingPlayer.displayClientMessage(
                                Component.translatable("message.goety_revenant.ritual_no_flesh_energy")
                                        .withStyle(ChatFormatting.RED), true);
                        this.stopRitual(false);
                        return;
                    }
                }

                running.getRitual().update(this.level, this.worldPosition, this, this.castingPlayer,
                        handler.getStackInSlot(0), this.currentTime, running.getDuration());

                if (!running.getRitual().consumeAdditionalIngredients(this.level, this.worldPosition,
                        this.castingPlayer, this.remainingAdditionalIngredients, this.currentTime,
                        this.consumedIngredients)) {
                    this.stopRitual(false);
                    return;
                }

                if (running.getDuration() >= 0 && this.currentTime >= running.getDuration()) {
                    this.stopRitual(true);
                    open = false;
                }

                int totalTime = 60;
                if (!RitualRequirements.getProperStructure(running.getCraftType(), this.castingPlayer, this,
                        this.worldPosition, this.level)) {
                    ++this.structureTime;
                    if (this.structureTime >= totalTime) {
                        this.castingPlayer.displayClientMessage(
                                Component.translatable("info.goety.ritual.structure.fail"), true);
                        this.stopRitual(false);
                    }
                } else {
                    this.structureTime = 0;
                }
            }
        } else {
            open = running != null;
        }

        // 嘴和眼睛：仪式一开就张眼张嘴
        this.level.setBlock(this.getBlockPos(), this.getBlockState()
                .setValue(DarkAltarBlock.LIT, flag)
                .setValue(FleshAltarBlock.MOUTH_OPEN, open)
                .setValue(FleshAltarBlock.EYES, open), 3);
    }

    /* ------------------------------------------------------------------ */
    /*  发动仪式                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean activate(Level world, BlockPos pos, Player player, InteractionHand hand, Direction face) {
        if (!world.isClientSide) {
            if (this.checkFleshCage()) {
                ItemStack activationItem = player.getItemInHand(hand);
                if (activationItem == ItemStack.EMPTY) {
                    this.removeItem();
                }

                if (this.getCurrentRitualRecipe() == null) {
                    RitualRecipe ritualRecipe = world.getRecipeManager()
                            .getAllRecipesFor(ModRecipeSerializer.RITUAL_TYPE.get()).stream()
                            .filter(r -> r.matches(world, pos, player, activationItem))
                            .filter(FleshRitual::isFleshRitual)
                            .findFirst().orElse(null);

                    if (ritualRecipe == null) {
                        player.displayClientMessage(Component.translatable(
                                "message.goety_revenant.ritual_not_flesh").withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    if (!this.structureOk(player, true)) {
                        return false;
                    }
                    if (!ritualRecipe.getRitual().isValid(world, pos, this, player, activationItem,
                            ritualRecipe.getIngredients())) {
                        player.displayClientMessage(
                                Component.translatable("info.goety.ritual.itemProblem.fail"), true);
                        return false;
                    }
                    if (!RitualRequirements.getProperStructure(ritualRecipe.getCraftType(), player, this, pos, world)) {
                        return false;
                    }
                    if (!FleshRitual.canStart(player, activationItem, ritualRecipe)) {
                        return false;
                    }
                    this.startRitual(player, activationItem, ritualRecipe);
                } else {
                    this.stopRitual(false);
                }
            } else {
                // 笼子不对 —— 明确告诉玩家缺什么
                if (world.getBlockState(pos.below()).is(com.habaiwa.goetyrevenant.init.ModBlocks.FLESH_CAGE.get())) {
                    player.displayClientMessage(Component.translatable(
                            "message.goety_revenant.ritual_need_totem").withStyle(ChatFormatting.RED), true);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "message.goety_revenant.ritual_need_cage").withStyle(ChatFormatting.RED), true);
                }
            }
        }
        return true;
    }
}
