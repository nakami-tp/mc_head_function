package com.nakami.mcheadfunction.validation.mixin;

import com.nakami.mcheadfunction.validation.GoatSmoothnessValidation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class GoatNetworkProbe {
	@Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
	private void correction(PlayerPositionLookS2CPacket packet, CallbackInfo ci) { GoatSmoothnessValidation.correction(); }
	@Inject(method = "onEntityVelocityUpdate", at = @At("TAIL"))
	private void velocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
		var client = MinecraftClient.getInstance();
		if (client.player != null && packet.getEntityId() == client.player.getId()) GoatSmoothnessValidation.velocity();
	}
}
