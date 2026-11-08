package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.creeper.FearSystem;
import net.minecraft.client.render.entity.EndermanEntityRenderer;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuse the screaming mouth and jitter without giving the fleeing mob an attack target. */
@Mixin(EndermanEntityRenderer.class)
public class EndermanFearRendererMixin {
    @Redirect(method = {"render", "getPositionOffset"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/mob/EndermanEntity;isAngry()Z"))
    private boolean mhf$screamingFromFear(EndermanEntity enderman) {
        return enderman.isAngry() || FearSystem.level(enderman) == 3;
    }
}
