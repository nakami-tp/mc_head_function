package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.GoatChargeControl;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class GoatInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void steer(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		GoatChargeControl.input((KeyboardInput) (Object) this);
	}
}
