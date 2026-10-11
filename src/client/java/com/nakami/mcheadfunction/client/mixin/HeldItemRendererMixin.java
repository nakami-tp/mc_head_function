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
public class HeldItemRendererMixin {
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
		at = @At("TAIL"))
	private void mhf$swallow(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertices,
		ClientPlayerEntity player, int light, CallbackInfo ci) {
		PigSwallowEffects.renderFirstPerson(matrices, vertices, player, light, tickDelta);
	}
}
