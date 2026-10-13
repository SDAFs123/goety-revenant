package com.habaiwa.goetyrevenant.client;

import com.habaiwa.goetyrevenant.common.tooltip.HighlightHook;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 灵魂状态下的寻尸高亮：**只在自己视野里**点亮自己那具身体。
 *
 * <p>别人（以及旁观者看别人）都看不到 —— 因为客户端只在本地玩家视角渲染，
 * 所以「按当前视角过滤」天然就是「只有你自己看得见」。</p>
 */
public class ClientHighlight implements HighlightHook.Impl {
    @Override
    public boolean isHighlighted(CorpseEntity corpse) {
        LocalPlayer local = Minecraft.getInstance().player;
        if (local == null) {
            return false;
        }
        // 只认服务端下发的「回魂灵魂状态」标记 ——
        // 管理员开原版旁观者时不该触发这套表现。
        if (!ClientSoulState.isSoul()) {
            return false;
        }
        // **出窍游离不识别身体** —— 不高亮、不给箭头，你得自己记住身体放哪儿了。
        // 死亡游离才帮你找（那条路身体是被"留下"的，不是被你"丢下"的）。
        if (ClientSoulState.isDrifting()) {
            return false;
        }
        return corpse.isOwner(local);
    }
}
