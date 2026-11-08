package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.head.HeadlessAccess;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin {
	@Shadow
	@Final
	private static TrackedData<Boolean> IGNITED;

	@Shadow
	private int currentFuseTime;

	@Shadow
	public abstract boolean isIgnited();

	@Shadow
	public abstract void setFuseSpeed(int fuseSpeed);

	@Inject(method = "tick", at = @At("HEAD"))
	private void mhf$defuseHeadless(CallbackInfo ci) {
		CreeperEntity self = (CreeperEntity) (Object) this;
		if (!HeadlessAccess.isHeadless(self)) {
			return;
		}
		if (isIgnited()) {
			self.getDataTracker().set(IGNITED, false);
		}
		setFuseSpeed(-1);
		currentFuseTime = 0;
	}

	@Inject(method = "ignite", at = @At("HEAD"), cancellable = true)
	private void mhf$noIgniteHeadless(CallbackInfo ci) {
		if (HeadlessAccess.isHeadless((CreeperEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "explode", at = @At("HEAD"), cancellable = true)
	private void mhf$noExplodeHeadless(CallbackInfo ci) {
		if (HeadlessAccess.isHeadless((CreeperEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
