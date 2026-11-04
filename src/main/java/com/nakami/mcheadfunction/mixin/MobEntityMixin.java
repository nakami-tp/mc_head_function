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
public class MobEntityMixin implements com.nakami.mcheadfunction.creeper.FearAccess {
    @org.spongepowered.asm.mixin.Unique
    private static final net.minecraft.entity.data.TrackedData<Integer> MHF_FEAR = net.minecraft.entity.data.DataTracker.registerData(MobEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);
    @org.spongepowered.asm.mixin.Unique
    private final com.nakami.mcheadfunction.creeper.FearState mhf$fear = new com.nakami.mcheadfunction.creeper.FearState();
    @Override public com.nakami.mcheadfunction.creeper.FearState mhf$fear() { return mhf$fear; }
    @Override public int mhf$fearLevel() { return ((MobEntity) (Object) this).getDataTracker().get(MHF_FEAR); }
    @Override public void mhf$setFearLevel(int level) { ((MobEntity) (Object) this).getDataTracker().set(MHF_FEAR, level); }

    @Inject(method = "initDataTracker", at = @At("RETURN"))
    private void mhf$initFear(net.minecraft.entity.data.DataTracker.Builder builder, CallbackInfo ci) { builder.add(MHF_FEAR, 0); }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mhf$tickFear(CallbackInfo ci) { com.nakami.mcheadfunction.creeper.FearSystem.tick((MobEntity) (Object) this); }

    @Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
    private void mhf$writeFear(net.minecraft.nbt.NbtCompound nbt, CallbackInfo ci) { mhf$fear.write(nbt); }

    @Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
    private void mhf$readFear(net.minecraft.nbt.NbtCompound nbt, CallbackInfo ci) { mhf$fear.read(nbt); }

	@Shadow
	@Final
	protected GoalSelector goalSelector;

	@Shadow
	@Final
	protected GoalSelector targetSelector;

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mhf$noTarget(LivingEntity target, CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (target != null && (HeadlessAccess.isHeadless(self) || com.nakami.mcheadfunction.creeper.FearSystem.pacified(self))) {
			ci.cancel();
		}
	}

	@Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
	private void mhf$headlessAi(CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (com.nakami.mcheadfunction.wear.GoatCharge.isStunned(self)) { ci.cancel(); return; }
		if (com.nakami.mcheadfunction.wear.FrogMouth.isHeld(self)) { ci.cancel(); return; }
		if (com.nakami.mcheadfunction.creeper.FearSystem.control(self)) { ci.cancel(); return; }
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
