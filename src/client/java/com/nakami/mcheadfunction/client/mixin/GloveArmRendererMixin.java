package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.GloveRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class GloveArmRendererMixin {
	@Inject(method = "renderArm", at = @At("RETURN"))
	private void mhf$glovedSkin(MatrixStack matrices, VertexConsumerProvider vertices, int light,
		AbstractClientPlayerEntity player, ModelPart part, ModelPart sleeve, CallbackInfo ci) {
		var model = ((PlayerEntityRenderer) (Object) this).getModel();
		Arm arm = part == model.rightArm ? Arm.RIGHT : Arm.LEFT;
		if (!GloveRenderer.equipped(player, arm)) return;
		matrices.push();
		part.rotate(matrices);
		GloveRenderer.render(arm, matrices, vertices, light);
		matrices.pop();
	}
}
