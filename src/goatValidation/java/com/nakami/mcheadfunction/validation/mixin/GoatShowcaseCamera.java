package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.validation.GoatClientValidation;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-only side views; the real player still owns movement and collisions. */
@Mixin(Camera.class)
public abstract class GoatShowcaseCamera {
	@Shadow protected abstract void setPos(double x, double y, double z);
	@Shadow protected abstract void setRotation(float yaw, float pitch);
	@Inject(method = "update", at = @At("TAIL"))
	private void showcase(BlockView world, Entity focused, boolean third, boolean inverse, float delta, CallbackInfo ci) {
		int mode = GoatClientValidation.showcaseCamera;
		if (mode == 0) return;
		Vec3d center = mode == 1 ? focused.getLerpedPos(delta).add(0, .8, 0) : new Vec3d(.5, 101.4, 247);
		Vec3d position = mode == 1 ? center.add(5, 3, -6) : new Vec3d(7.5, 104, 243);
		Vec3d look = center.subtract(position);
		setPos(position.x, position.y, position.z);
		setRotation((float) Math.toDegrees(Math.atan2(-look.x, look.z)), (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalLength())));
	}
}
