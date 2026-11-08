package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.GoatImpact;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class GoatCameraMixin {
	@Shadow public abstract float getYaw();
	@Shadow public abstract float getPitch();
	@Shadow protected abstract void setRotation(float yaw, float pitch);
	@Shadow public abstract org.joml.Quaternionf getRotation();
	@Shadow public abstract Entity getFocusedEntity();
	@ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0))
	private void smoothChargeView(Args args) {
		if (getFocusedEntity() == net.minecraft.client.MinecraftClient.getInstance().player
			&& com.nakami.mcheadfunction.client.GoatChargeControl.active()) {
			// Set the orientation before vanilla computes the third-person orbit
			// and collision distance; changing it afterward leaves the orbit jerky.
			args.set(0, com.nakami.mcheadfunction.client.GoatChargeControl.viewYaw());
			args.set(1, com.nakami.mcheadfunction.client.GoatChargeControl.viewPitch());
		}
	}
	@Inject(method = "update", at = @At("TAIL"))
	private void shake(BlockView area, Entity focused, boolean thirdPerson, boolean inverse, float delta, CallbackInfo ci) {
		if (GoatImpact.ticks > 0) setRotation(getYaw() + GoatImpact.yaw(delta), getPitch() + GoatImpact.pitch(delta));
		float roll = com.nakami.mcheadfunction.client.GoatDriftPose.cameraRoll(delta);
		if (roll != 0) getRotation().rotateZ((float) Math.toRadians(-roll));
	}
}
