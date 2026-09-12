package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.net.ThrowHeadC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Shadow
	public ClientPlayerEntity player;

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void mhf$throwHead(CallbackInfoReturnable<Boolean> cir) {
		if (player == null || !HeadItems.isHead(player.getMainHandStack())) {
			return;
		}
		ClientPlayNetworking.send(new ThrowHeadC2SPayload());
		player.swingHand(player.getActiveHand());
		cir.setReturnValue(true);
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void mhf$noMineWithHead(boolean breaking, CallbackInfo ci) {
		if (player != null && HeadItems.isHead(player.getMainHandStack())) {
			ci.cancel();
		}
	}
}
