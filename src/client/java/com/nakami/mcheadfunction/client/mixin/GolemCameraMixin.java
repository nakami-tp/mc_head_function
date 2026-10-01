package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.GolemQuake;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class GolemCameraMixin {
	@Shadow public abstract float getYaw();
	@Shadow public abstract float getPitch();
	@Shadow public abstract Vec3d getPos();
	@Shadow protected abstract void setRotation(float yaw, float pitch);
	@Shadow protected abstract void setPos(double x, double y, double z);
	@Inject(method = "update", at = @At("TAIL"))
	private void quake(BlockView area, Entity focused, boolean thirdPerson, boolean inverse, float delta, CallbackInfo ci) {
		if (GolemQuake.amplitude(delta) <= 0) return;
		setRotation(getYaw() + GolemQuake.yaw(delta), getPitch() + GolemQuake.pitch(delta));
		Vec3d pos = getPos();
		setPos(pos.x, pos.y + GolemQuake.lift(delta), pos.z);
	}
}
