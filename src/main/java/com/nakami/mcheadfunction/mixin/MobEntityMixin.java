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

	@Inject(method = "tickNewAi", at = @At("HEAD"))
	private void mhf$headlessAi(CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (HeadlessAccess.isHeadless(self)) {
			HeadlessAi.suppress(self, goalSelector, targetSelector);
		}
	}
}
