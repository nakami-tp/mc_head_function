package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.ZombieFrenzyEffects;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Select the alternating fist; vanilla still computes all third-person swing transforms. */
@Mixin(BipedEntityModel.class)
public abstract class ZombieSwingArmMixin {
    @Inject(method = "getPreferredArm", at = @At("HEAD"), cancellable = true)
    private void mhf$alternateFist(LivingEntity entity, CallbackInfoReturnable<Arm> cir) {
        if (entity instanceof PlayerEntity player && ZombieFrenzyEffects.posing(player)
            && player.preferredHand == Hand.MAIN_HAND) {
            cir.setReturnValue(ZombieFrenzyEffects.strikingArm(player));
        }
    }
}
