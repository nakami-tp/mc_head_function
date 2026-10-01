package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.net.RabbitJumpC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class RabbitJumpMixin {
	@Inject(method = "jump", at = @At("HEAD"), cancellable = true)
	private void mhf$rabbitJump(CallbackInfo ci) {
		var player = MinecraftClient.getInstance().player;
		if ((Object) this == player && player != null && HeadLookups.worn(player) == HeadType.RABBIT
			&& player.isOnGround() && !player.isTouchingWater() && !player.getAbilities().flying) {
			ClientPlayNetworking.send(new RabbitJumpC2SPayload());
			ci.cancel();
		}
	}
}
