package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.head.HeadItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeadFeatureRenderer.class)
public abstract class HeadFeatureRendererMixin {
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
		if (entity.getEquippedStack(EquipmentSlot.HEAD).getItem() instanceof HeadItem) {
			matrices.scale(2.0F, 2.0F, 2.0F);
		}
	}
}
