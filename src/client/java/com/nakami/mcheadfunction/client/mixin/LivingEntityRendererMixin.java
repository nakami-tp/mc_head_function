package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.NeckCapRenderer;
import com.nakami.mcheadfunction.client.PigSwallowEffects;
import com.nakami.mcheadfunction.haunt.HauntRisenAccess;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.head.PigStuffedAccess;
import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
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
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
	@Shadow
	protected M model;

	@Unique private int mhf$previousDeathTime;
	@Unique private int mhf$previousHurtTime;

	@Inject(method = "render", at = @At("HEAD"))
	private void mhf$hideHead(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		matrices.push();
		var meal = com.nakami.mcheadfunction.client.ChestMealAnimation.tongue(entity);
		float mealScale = meal == null ? 1 : meal.preyScale(tickDelta);
		mhf$previousDeathTime = entity.deathTime;
		mhf$previousHurtTime = entity.hurtTime;
		if (meal != null && meal.deathTicks() > 0) {
			// Reuse vanilla death rotation and red overlay without changing health or loot.
			entity.deathTime = Math.max(entity.deathTime, Math.min(20, meal.deathTicks() * 2));
			if (meal.deathTicks() <= 4) entity.hurtTime = Math.max(entity.hurtTime, 8);
		}
		if (mealScale < 1) matrices.scale(mealScale, mealScale, mealScale);
		int crush = ((com.nakami.mcheadfunction.head.HeadCrushAccess) entity).mhf$crushTicks();
		if (crush > 0) {
			float amount = Math.min(1, crush / 5.0F);
			matrices.scale(1 + 0.45F * amount, 1 - 0.82F * amount, 1 + 0.45F * amount);
		}
		if (entity instanceof HauntRisenAccess risen && risen.mhf$hauntRiseTicks() > 0) {
			matrices.translate(0, -HauntRules.riseBuried(risen.mhf$hauntRiseTicks() - tickDelta), 0);
		}
		if (entity instanceof PlayerEntity player) {
			float fat = HeadRules.pigFatness(((PigStuffedAccess) player).mhf$stuffedTicks(), tickDelta);
			if (fat > 0) {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin((entity.age + tickDelta) * 0.32) * 7 * fat));
				matrices.scale(1 + 0.32F * fat, 1 + 0.08F * fat, 1 + 0.24F * fat);
			}
		}
		setHeadVisible(entity, !HeadlessAccess.isHeadless(entity));
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
	private void mhf$pigBelly(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		if (model instanceof BipedEntityModel<?> biped && HauntRisenAccess.dancing(entity)) {
			mhf$thriller(biped, HauntRules.thrillerPose(entity.getWorld().getTime()));
		}
		if (!(entity instanceof PlayerEntity player) || !(model instanceof BipedEntityModel<?> biped)) return;
		float fat = HeadRules.pigFatness(((PigStuffedAccess) player).mhf$stuffedTicks(), tickDelta);
		mhf$fatParts(biped, fat);
		float swallow = HeadRules.pigSwallowProgress(((PigStuffedAccess) player).mhf$stuffedTicks(), tickDelta);
		if (swallow < 1) {
			float raise = 1 - swallow;
			biped.rightArm.pitch = -1.55F * raise + biped.rightArm.pitch * swallow;
			biped.rightArm.yaw = -0.45F * raise;
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void mhf$restoreHead(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		if (HeadlessAccess.isHeadless(entity)) {
			NeckCapRenderer.render(entity, matrices, consumers, light);
		}
		if (entity instanceof PlayerEntity player) {
			PigSwallowEffects.renderIncoming(matrices, consumers, player, light, tickDelta);
			if (model instanceof BipedEntityModel<?> biped) mhf$fatParts(biped, 0);
		}
		setHeadVisible(entity, true);
		entity.deathTime = mhf$previousDeathTime;
		entity.hurtTime = mhf$previousHurtTime;
		matrices.pop();
	}

	@Unique
	private static void mhf$thriller(BipedEntityModel<?> biped, HauntRules.DancePose pose) {
		biped.body.pitch = pose.bodyPitch();
		biped.head.yaw = pose.headYaw();
		biped.head.pitch = pose.headPitch();
		biped.hat.copyTransform(biped.head);
		biped.leftArm.roll = pose.leftArmRoll();
		biped.rightArm.roll = pose.rightArmRoll();
		biped.leftArm.pitch = pose.leftArmPitch();
		biped.rightArm.pitch = pose.rightArmPitch();
		biped.leftArm.yaw = 0;
		biped.rightArm.yaw = 0;
		biped.leftLeg.roll = pose.leftLegRoll();
		biped.rightLeg.roll = pose.rightLegRoll();
		biped.leftLeg.pitch = 0;
		biped.rightLeg.pitch = 0;
	}

	@Unique
	private static void mhf$fatParts(BipedEntityModel<?> biped, float fat) {
		mhf$scale(biped.body, 1 + 0.82F * fat, 1 + 0.42F * fat, 1 + 1.15F * fat);
		biped.body.pivotZ = 2.4F * fat;
		mhf$scale(biped.leftArm, 1 + 0.28F * fat, 1, 1 + 0.28F * fat);
		mhf$scale(biped.rightArm, 1 + 0.28F * fat, 1, 1 + 0.28F * fat);
		mhf$scale(biped.leftLeg, 1 + 0.22F * fat, 1, 1 + 0.28F * fat);
		mhf$scale(biped.rightLeg, 1 + 0.22F * fat, 1, 1 + 0.28F * fat);
		if (biped instanceof PlayerEntityModel<?> slim) {
			mhf$scale(slim.jacket, 1 + 0.82F * fat, 1 + 0.42F * fat, 1 + 1.15F * fat);
			slim.jacket.pivotZ = 2.4F * fat;
			mhf$scale(slim.leftSleeve, 1 + 0.28F * fat, 1, 1 + 0.28F * fat);
			mhf$scale(slim.rightSleeve, 1 + 0.28F * fat, 1, 1 + 0.28F * fat);
			mhf$scale(slim.leftPants, 1 + 0.22F * fat, 1, 1 + 0.28F * fat);
			mhf$scale(slim.rightPants, 1 + 0.22F * fat, 1, 1 + 0.28F * fat);
		}
	}

	@Unique
	private static void mhf$scale(ModelPart part, float x, float y, float z) {
		part.xScale = x;
		part.yScale = y;
		part.zScale = z;
	}

	private void setHeadVisible(T entity, boolean visible) {
		if (model instanceof BipedEntityModel<?> biped) {
			biped.head.visible = visible;
			biped.hat.visible = visible;
		}
		if (model instanceof AnimalModelAccessor animal) {
			animal.mhf$getHeadParts().forEach(part -> part.visible = visible);
		}
		if (model instanceof SinglePartEntityModel<?> single) {
			hideNamedHeads(single.getPart(), visible);
		}
	}

	private static void hideNamedHeads(ModelPart root, boolean visible) {
		for (Map.Entry<String, ModelPart> entry : ((ModelPartAccessor) (Object) root).mhf$children().entrySet()) {
			String name = entry.getKey().toLowerCase(Locale.ROOT);
			ModelPart child = entry.getValue();
			if (isHeadName(name)) {
				child.visible = visible;
			}
			hideNamedHeads(child, visible);
		}
	}

	private static boolean isHeadName(String name) {
		return name.equals("head")
			|| name.equals("hat")
			|| name.equals("skull")
			|| name.equals("headwear")
			|| name.contains("head");
	}
}
