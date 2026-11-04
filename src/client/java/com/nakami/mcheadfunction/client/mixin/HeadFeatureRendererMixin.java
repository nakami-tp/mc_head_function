package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.BlazeHeadAppearance;
import com.nakami.mcheadfunction.head.HeadItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeadFeatureRenderer.class)
public abstract class HeadFeatureRendererMixin {
	@Unique
	private LivingEntity mhf$blazeWearer;
	@Unique
	private float mhf$blazeDelta;

	/**
	 * Paired with generate_head_models.py's head_fit(). Minecraft clamps JSON display
	 * scales to 4; this extra factor lets even thin animal heads enclose the skin hat.
	 * Only the HEAD feature receives it, never inventory, held or thrown rendering.
	 * The surrounding vanilla matrix push/pop restores the transform afterwards.
	 */
	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
	private void mhf$fitWornHead(MatrixStack matrices, VertexConsumerProvider vertices, int light,
		LivingEntity entity, float limbAngle, float limbDistance, float tickDelta,
		float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		mhf$blazeWearer = entity;
		mhf$blazeDelta = tickDelta;
		if (entity.getEquippedStack(EquipmentSlot.HEAD).getItem() instanceof HeadItem) {
			float extra = BlazeHeadAppearance.pulseScale(entity, tickDelta);
			matrices.scale(2.0F * extra, 2.0F * extra, 2.0F * extra);
			if (com.nakami.mcheadfunction.wear.FrogProjectile.occupied(entity)) {
				float tremble = (float) Math.sin((entity.age + tickDelta) * 2.4) * .015F;
				matrices.scale(1.18F + tremble, 1.08F - tremble, 1.18F + tremble);
			}
			var held = com.nakami.mcheadfunction.wear.FrogMouth.held(entity);
			if (held != null) {
				// Swell around the neck pivot; shrink as the meal is digested; a lit creeper throbs as a warning.
				boolean lit = held instanceof net.minecraft.entity.mob.CreeperEntity creeper && creeper.isIgnited();
				float size = com.nakami.mcheadfunction.rule.HeadRules.frogMouthSize(
					held.getWidth(), held.getHeight(), held.getHealth() / Math.max(0.001F, held.getMaxHealth()));
				float chew = com.nakami.mcheadfunction.rule.HeadRules.frogChew(entity.age + tickDelta, held.hurtTime, lit);
				matrices.scale(size + chew, size - chew * 0.6F, size + chew);
			}
		}
	}

	@ModifyArg(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),
		index = 5)
	private VertexConsumerProvider mhf$tintBlaze(VertexConsumerProvider vertices) {
		return mhf$blazeWearer == null ? vertices : BlazeHeadAppearance.wrap(mhf$blazeWearer, vertices, mhf$blazeDelta);
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			shift = At.Shift.AFTER))
	private void mhf$blazeLook(MatrixStack matrices, VertexConsumerProvider vertices, int light,
		LivingEntity entity, float limbAngle, float limbDistance, float tickDelta,
		float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		BlazeHeadAppearance.worn(entity, matrices, vertices, tickDelta);
		if (com.nakami.mcheadfunction.wear.FrogProjectile.appearance(entity) == com.nakami.mcheadfunction.wear.FrogProjectile.ARROW) {
			matrices.push();
			matrices.translate(0, -0.08, -0.32);
			matrices.scale(.3F, .3F, .3F);
			matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(20));
			net.minecraft.client.MinecraftClient.getInstance().getItemRenderer().renderItem(
				new net.minecraft.item.ItemStack(net.minecraft.item.Items.ARROW),
				net.minecraft.client.render.model.json.ModelTransformationMode.FIXED, light,
				net.minecraft.client.render.OverlayTexture.DEFAULT_UV, matrices, vertices, entity.getWorld(), entity.getId());
			matrices.pop();
		}
		mhf$blazeWearer = null;
	}
}
