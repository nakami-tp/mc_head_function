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
		if (hand == net.minecraft.util.Hand.MAIN_HAND && com.nakami.mcheadfunction.client.CreeperCookingControl.held(player) != null) {
            ci.cancel();
            if (!player.isInvisible()) {
                matrices.push();
                renderArmHoldingItem(matrices, vertices, light, 0, 0, player.getMainArm());
                matrices.pop();
            }
            return;
        }
        if (hand == net.minecraft.util.Hand.MAIN_HAND
            && com.nakami.mcheadfunction.client.ZombieFrenzyEffects.posing(player)) {
            ci.cancel();
            com.nakami.mcheadfunction.client.ZombieFrenzyEffects.firstPerson(player, swing, equip, matrices, vertices,
                light, this::renderArmHoldingItem);
            return;
        }
        if (!stack.isOf(com.nakami.mcheadfunction.head.HeadItems.BEHEADING_GLOVE)) return;
		ci.cancel();
		if (player.isInvisible()) return;
		var arm = hand == net.minecraft.util.Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        matrices.push();
        // Keep the wearable glove, but let vanilla animate the arm and equip transition.
        renderArmHoldingItem(matrices, vertices, light, equip, swing, arm);
        matrices.pop();
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
		at = @At("HEAD"))
	private void mhf$frogChewHands(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertices,
		ClientPlayerEntity player, int light, CallbackInfo ci) {
		float chew = com.nakami.mcheadfunction.client.FrogMouthEffects.hand(tickDelta);
		if (chew == 0) return;
		matrices.translate(0, chew * 0.05F, chew * 0.03F);
		matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(chew * 18));
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
		at = @At("TAIL"))
	private void mhf$swallow(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertices,
		ClientPlayerEntity player, int light, CallbackInfo ci) {
		PigSwallowEffects.renderFirstPerson(matrices, vertices, player, light, tickDelta);
        if (com.nakami.mcheadfunction.client.CreeperCookingControl.held(player) != null) {
            com.nakami.mcheadfunction.client.CreeperGrenadeRender.firstPerson(net.minecraft.client.MinecraftClient.getInstance(), matrices, vertices, light);
            vertices.draw();
        }
	}
}
