package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.behead.HeadlessAi;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobEntityMixin {
	@Shadow
	@Final
	protected GoalSelector goalSelector;

	@Shadow
	@Final
	protected GoalSelector targetSelector;

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mhf$noTarget(LivingEntity target, CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (target != null && HeadlessAccess.isHeadless(self)) {
			ci.cancel();
		}
	}

	@Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
	private void mhf$headlessAi(CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (com.nakami.mcheadfunction.wear.GoatCharge.isStunned(self)) { ci.cancel(); return; }
		if (HeadlessAccess.isHeadless(self)) {
			HeadlessAi.suppress(self, goalSelector, targetSelector);
		}
	}

	@Inject(method = "isDisallowedInPeaceful", at = @At("HEAD"), cancellable = true)
	private void mhf$keepHauntExtras(CallbackInfoReturnable<Boolean> cir) {
		if (com.nakami.mcheadfunction.haunt.HauntGraveyardRising.isExtra((MobEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void mhf$hauntExtrasStay(CallbackInfo ci) {
		if (com.nakami.mcheadfunction.haunt.HauntGraveyardRising.isExtra((MobEntity) (Object) this)) ci.cancel();
	}
}
