package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.validation.GoatClientValidation;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class GoatPoseProbe {
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
	private void observe(LivingEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		com.nakami.mcheadfunction.validation.GoatSmoothnessValidation.observeBody(entity, matrices.peek().getPositionMatrix());
		if (!(entity instanceof PlayerEntity) || GoatClientValidation.showcaseCamera != 1) return;
		var matrix = matrices.peek().getPositionMatrix();
		GoatClientValidation.renderedTilt = Math.max(GoatClientValidation.renderedTilt, (float) Math.hypot(matrix.m10(), matrix.m12()));
		float lean = com.nakami.mcheadfunction.client.GoatDriftPose.lean(entity, delta);
		double heading = Math.toRadians(entity.getYaw());
		float inside = Math.signum(lean) * (float) (matrix.m10() * Math.cos(heading) + matrix.m12() * Math.sin(heading));
		GoatClientValidation.renderedInsideLean = Math.max(GoatClientValidation.renderedInsideLean, inside);
	}
}
