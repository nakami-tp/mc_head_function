package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.GoatDriftPose;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class GoatDriftRendererMixin {
	@Shadow protected EntityModel<?> model;
	@Unique private float[] mhf$beforeDrift;
	@Inject(method = "setupTransforms", at = @At("TAIL"))
	private void drift(LivingEntity entity, MatrixStack matrices, float animation, float bodyYaw, float delta, float scale, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity)) return;
		float lean = GoatDriftPose.lean(entity, delta);
		if (Math.abs(lean) < .05F) return;
		// The character faces into the bend while its velocity still slides out.
		float facing = entity == net.minecraft.client.MinecraftClient.getInstance().player
			&& com.nakami.mcheadfunction.client.GoatChargeControl.active()
			? com.nakami.mcheadfunction.client.GoatChargeControl.viewYaw() : entity.getYaw();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((bodyYaw - facing) * Math.min(1, Math.abs(lean) / 12)));
		// Vanilla's 180-degree body rotation reverses the local roll sign.
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-lean));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-Math.abs(lean) * .4F));
	}
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
	private void stance(LivingEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity) || !(model instanceof BipedEntityModel<?> biped)) return;
		float lean = GoatDriftPose.lean(entity, delta);
		if (Math.abs(lean) < .05F) return;
		ModelPart[] limbs = {biped.rightArm, biped.leftArm, biped.rightLeg, biped.leftLeg};
		mhf$beforeDrift = new float[8];
		for (int i = 0; i < limbs.length; i++) {
			mhf$beforeDrift[i * 2] = limbs[i].pitch;
			mhf$beforeDrift[i * 2 + 1] = limbs[i].roll;
		}
		float weight = Math.min(1, Math.abs(lean) / 18);
		// Brace the inside leg and extend the outside leg; arms balance the skid.
		float[] pitch = {-.55F, -.55F, lean > 0 ? -.50F : .25F, lean > 0 ? .25F : -.50F};
		float[] roll = {-.35F, .35F, -.16F, .16F};
		for (int i = 0; i < limbs.length; i++) {
			limbs[i].pitch += (pitch[i] - limbs[i].pitch) * weight;
			limbs[i].roll += (roll[i] - limbs[i].roll) * weight;
		}
		mhf$clothing(biped);
	}
	@Inject(method = "render", at = @At("RETURN"))
	private void restore(LivingEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		if (mhf$beforeDrift == null || !(model instanceof BipedEntityModel<?> biped)) return;
		ModelPart[] limbs = {biped.rightArm, biped.leftArm, biped.rightLeg, biped.leftLeg};
		for (int i = 0; i < limbs.length; i++) {
			limbs[i].pitch = mhf$beforeDrift[i * 2]; limbs[i].roll = mhf$beforeDrift[i * 2 + 1];
		}
		mhf$clothing(biped);
		mhf$beforeDrift = null;
	}
	@Unique private static void mhf$clothing(BipedEntityModel<?> biped) {
		if (!(biped instanceof net.minecraft.client.render.entity.model.PlayerEntityModel<?> player)) return;
		ModelPart[] limbs = {biped.rightArm, biped.leftArm, biped.rightLeg, biped.leftLeg};
		ModelPart[] clothing = {player.rightSleeve, player.leftSleeve, player.rightPants, player.leftPants};
		for (int i = 0; i < limbs.length; i++) {
			clothing[i].pitch = limbs[i].pitch; clothing[i].roll = limbs[i].roll;
		}
	}
}
