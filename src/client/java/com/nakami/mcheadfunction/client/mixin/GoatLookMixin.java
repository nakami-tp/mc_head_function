package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.GoatChargeControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class GoatLookMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void lockLook(double x, double y, CallbackInfo ci) {
		if ((Object) this == MinecraftClient.getInstance().player && GoatChargeControl.active()) ci.cancel();
	}
}
