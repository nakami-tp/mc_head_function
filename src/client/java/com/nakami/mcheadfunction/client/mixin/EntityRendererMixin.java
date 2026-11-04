package com.nakami.mcheadfunction.client.mixin;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	/** Skip model, fire overlay and shadow: a mouthful is drawn as the wearer's swollen head instead. */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void mhf$hideMouthful(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (com.nakami.mcheadfunction.wear.FrogMouth.isHeld(entity)) cir.setReturnValue(false);
	}
}
