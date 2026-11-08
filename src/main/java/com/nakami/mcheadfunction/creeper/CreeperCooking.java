package com.nakami.mcheadfunction.creeper;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.HeadSounds;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;

/** Reserves the actual head at ignition, so moving inventory cannot duplicate it. Switching items snuffs the fuse. */
public final class CreeperCooking {
    public static final int FUSE_TICKS = com.nakami.mcheadfunction.rule.HeadRules.CREEPER_FUSE_TICKS;
    private CreeperCooking() { }

    public static ThrownHeadEntity held(ServerPlayerEntity player) {
        return player.getServerWorld().getEntitiesByClass(ThrownHeadEntity.class,
            player.getBoundingBox().expand(3), entity -> entity.isCooking() && entity.getOwner() == player)
            .stream().findFirst().orElse(null);
    }

    public static void input(ServerPlayerEntity player, boolean pressed) {
        var pending = held(player);
        if (!pressed) {
            if (pending != null) pending.releaseCookedHead();
            return;
        }
        if (pending != null || !player.isAlive() || player.isSpectator()
            || com.nakami.mcheadfunction.wear.GoatCharge.isStunned(player)
            || !player.getMainHandStack().isOf(Items.CREEPER_HEAD)) return;
        var grenade = new ThrownHeadEntity(player.getWorld(), player, HeadType.CREEPER, HeadType.ThrowStyle.CREEPER_BLAST);
        grenade.setItem(player.getMainHandStack().copyWithCount(1));
        grenade.startCooking(player.getInventory().selectedSlot);
        grenade.setPosition(player.getEyePos().add(player.getRotationVec(1).multiply(0.6)));
        if (!player.getServerWorld().spawnEntity(grenade)) return;
        if (!player.getAbilities().creativeMode) player.getMainHandStack().decrement(1);
        HeadSounds.play(player, SoundEvents.ITEM_CROSSBOW_LOADING_END.value(), 0.7F, 1.35F);
    }
}
