package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.GloveRenderer;
import com.nakami.mcheadfunction.head.HeadItems;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class GloveHeldFeatureMixin extends FeatureRenderer<LivingEntity, EntityModel<LivingEntity>> {
	protected GloveHeldFeatureMixin(FeatureRendererContext<LivingEntity, EntityModel<LivingEntity>> context) { super(context); }

	@Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
	private void mhf$wearGlove(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm,
		MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity) || !stack.isOf(HeadItems.BEHEADING_GLOVE)) return;
		matrices.push();
		((ModelWithArms) getContextModel()).setArmAngle(arm, matrices);
		GloveRenderer.render(arm, matrices, vertices, light);
		matrices.pop();
		ci.cancel();
	}
}
