package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.haunt.HauntStayOut;
import com.nakami.mcheadfunction.haunt.HauntVillagers;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public class VillagerEntityMixin {
	@Inject(method = "mobTick", at = @At("HEAD"))
	private void mhf$stayOut(CallbackInfo ci) {
		HauntStayOut.tick((VillagerEntity) (Object) this);
	}

	@Inject(method = "sleep", at = @At("HEAD"), cancellable = true)
	private void mhf$noBed(BlockPos pos, CallbackInfo ci) {
		if (HauntStayOut.active((VillagerEntity) (Object) this)) ci.cancel();
	}

	@Inject(method = "onStruckByLightning", at = @At("HEAD"), cancellable = true)
	private void mhf$noWitch(ServerWorld world, LightningEntity lightning, CallbackInfo ci) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		if (!HauntVillagers.linger(self)) return;
		self.damage(world.getDamageSources().lightningBolt(), 5);
		ci.cancel();
	}
}
