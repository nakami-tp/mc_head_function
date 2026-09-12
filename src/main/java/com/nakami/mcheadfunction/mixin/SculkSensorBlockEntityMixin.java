package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.wear.WearHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.Vibrations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Vibrations.VibrationListener.class)
public class SculkSensorBlockEntityMixin {
	@Inject(method = "listen", at = @At("HEAD"), cancellable = true)
	private void mhf$sheepQuiet(ServerWorld world, RegistryEntry<GameEvent> event, GameEvent.Emitter emitter, Vec3d pos, CallbackInfoReturnable<Boolean> cir) {
		Entity entity = emitter.sourceEntity();
		if (!(entity instanceof PlayerEntity player) || !WearHandler.wearingSheep(player)) {
			return;
		}
		if (player.squaredDistanceTo(pos) > 4.0) {
			cir.setReturnValue(false);
		}
	}
}
