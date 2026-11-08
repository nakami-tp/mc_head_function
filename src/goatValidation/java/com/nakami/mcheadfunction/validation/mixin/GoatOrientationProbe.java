package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.client.GoatChargeControl;
import com.nakami.mcheadfunction.net.GoatChargeS2CPayload;
import com.nakami.mcheadfunction.validation.GoatSmoothnessValidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GoatChargeControl.class, remap = false)
public class GoatOrientationProbe {
	@Inject(method = "receive", at = @At("HEAD"))
	private static void before(GoatChargeS2CPayload payload, CallbackInfo ci) { GoatSmoothnessValidation.beforeOrientation(payload); }
	@Inject(method = "receive", at = @At("RETURN"))
	private static void after(GoatChargeS2CPayload payload, CallbackInfo ci) { GoatSmoothnessValidation.afterOrientation(); }
}
