package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.rule.HeadRules;
import com.nakami.mcheadfunction.wear.WearHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.Vibrations;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Vibrations.VibrationListener.class)
public class SculkSensorBlockEntityMixin {
	@Shadow
	@Final
	private Vibrations receiver;

	@Inject(method = "listen", at = @At("HEAD"), cancellable = true)
	private void mhf$sheepQuiet(ServerWorld world, RegistryEntry<GameEvent> event, GameEvent.Emitter emitter, Vec3d pos, CallbackInfoReturnable<Boolean> cir) {
		Entity entity = emitter.sourceEntity();
		if (!(entity instanceof PlayerEntity player) || !WearHandler.wearingSheep(player)) {
			return;
		}
		Vec3d sensor = receiver.getVibrationCallback().getPositionSource().getPos(world).orElse(null);
		if (sensor == null) {
			return;
		}
		if (HeadRules.sheepQuiets(player.squaredDistanceTo(sensor)) || HeadRules.sheepQuiets(pos.squaredDistanceTo(sensor))) {
			cir.setReturnValue(false);
		}
	}
}
