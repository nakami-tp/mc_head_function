package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.PigSwallowEffects;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@org.spongepowered.asm.mixin.Shadow
	protected abstract void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertices, int light,
		float equipProgress, float swingProgress, net.minecraft.util.Arm arm);

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void mhf$punchWithGlove(net.minecraft.client.network.AbstractClientPlayerEntity player, float delta,
		float pitch, net.minecraft.util.Hand hand, float swing, net.minecraft.item.ItemStack stack, float equip,
		MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
		if (!stack.isOf(com.nakami.mcheadfunction.head.HeadItems.BEHEADING_GLOVE)) return;
		ci.cancel();
		if (player.isInvisible()) return;
		var arm = hand == net.minecraft.util.Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == net.minecraft.util.Arm.RIGHT ? 1 : -1;
		float extension = com.nakami.mcheadfunction.client.GloveRenderer.extension(swing)
			* com.nakami.mcheadfunction.client.GloveRenderer.strength(player);
		float windup = com.nakami.mcheadfunction.client.GloveRenderer.windup(swing);
		matrices.push();
		matrices.translate(side * (-0.08F + 0.03F * windup - 0.24F * extension),
			0.12F - 0.03F * windup + 0.06F * extension, 0.12F * windup - 0.30F * extension);
		matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * (8 * extension)));
		// Keep the jab independent of vanilla item swing and attack-cooldown equip dip.
		renderArmHoldingItem(matrices, vertices, light, 0, 0, arm);
		matrices.pop();
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
		at = @At("TAIL"))
	private void mhf$swallow(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertices,
		ClientPlayerEntity player, int light, CallbackInfo ci) {
		PigSwallowEffects.renderFirstPerson(matrices, vertices, player, light, tickDelta);
	}
}
