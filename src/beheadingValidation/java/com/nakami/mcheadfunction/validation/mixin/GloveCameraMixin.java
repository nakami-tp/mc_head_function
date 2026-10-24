package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.validation.BeheadingClientValidation;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class GloveCameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Inject(method = "update", at = @At("RETURN"))
    private void sideView(BlockView area, Entity entity, boolean third, boolean inverse, float delta, CallbackInfo ci) {
        if (BeheadingClientValidation.sideView) {
            setPos(5, 102, 5);
            setRotation(130, 12);
        }
    }
}
