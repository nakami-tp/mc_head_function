package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.FrogMouthEffects;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class FrogCameraMixin {
	@Shadow public abstract float getYaw();
	@Shadow public abstract float getPitch();
	@Shadow protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "update", at = @At("TAIL"))
	private void chew(BlockView area, Entity focused, boolean thirdPerson, boolean inverse, float delta, CallbackInfo ci) {
		float pitch = FrogMouthEffects.pitch(delta);
		float yaw = FrogMouthEffects.yaw(delta);
		if (pitch != 0 || yaw != 0) setRotation(getYaw() + yaw, getPitch() + pitch);
	}
}
