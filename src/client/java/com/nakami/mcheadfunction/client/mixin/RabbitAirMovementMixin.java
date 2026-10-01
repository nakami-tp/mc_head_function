package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.wear.RabbitMovement;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class RabbitAirMovementMixin {
	@Inject(method = "getOffGroundSpeed", at = @At("RETURN"), cancellable = true)
	private void mhf$rabbitAirSpeed(CallbackInfoReturnable<Float> cir) {
		var player = (PlayerEntity) (Object) this;
		if (HeadLookups.worn(player) != HeadType.RABBIT || player.getAbilities().flying
			|| player.hasVehicle() || player.isTouchingWater()) return;
		// Vanilla air acceleration ignores movement-speed attributes. Use the same
		// server-synced rabbit bonus as ground movement, without scaling other effects.
		var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		var bonus = speed == null ? null : speed.getModifier(RabbitMovement.SPEED);
		if (bonus != null) cir.setReturnValue((float) (cir.getReturnValue() * (1 + bonus.value())));
	}
}
