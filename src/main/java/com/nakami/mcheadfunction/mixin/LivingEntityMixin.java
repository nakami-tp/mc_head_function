package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.rule.HeadRules;
import com.nakami.mcheadfunction.wear.WearHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements HeadlessAccess {
	@Unique
	private static final TrackedData<Boolean> MHF_HEADLESS = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	@Unique
	private float mhf$healthBeforeDamage;

	protected LivingEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(method = "initDataTracker", at = @At("RETURN"))
	private void mhf$initHeadless(DataTracker.Builder builder, CallbackInfo ci) {
		builder.add(MHF_HEADLESS, false);
	}

	@Override
	public boolean mhf$isHeadless() {
		return this.dataTracker.get(MHF_HEADLESS);
	}

	@Override
	public void mhf$setHeadless(boolean headless) {
		this.dataTracker.set(MHF_HEADLESS, headless);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	private void mhf$writeHeadless(NbtCompound nbt, CallbackInfo ci) {
		nbt.putBoolean("mhf_headless", mhf$isHeadless());
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	private void mhf$readHeadless(NbtCompound nbt, CallbackInfo ci) {
		mhf$setHeadless(nbt.getBoolean("mhf_headless"));
	}

	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void mhf$beforeDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayerEntity player) {
			if (WearHandler.tryEndermanDodge(player)) {
				cir.setReturnValue(false);
				return;
			}
			if (WearHandler.reduceIncoming(player, source, amount)) {
				cir.setReturnValue(false);
			}
		}
	}

	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mhf$armadillo(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayerEntity player) {
			return WearHandler.armadilloScale(player, amount);
		}
		return amount;
	}

	@Inject(method = "applyDamage", at = @At("HEAD"))
	private void mhf$beforeApplyDamage(DamageSource source, float amount, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		mhf$healthBeforeDamage = self.getHealth();
	}

	@Inject(method = "applyDamage", at = @At("RETURN"))
	private void mhf$afterDamage(DamageSource source, float amount, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
			return;
		}
		float lost = Math.max(0.0F, mhf$healthBeforeDamage - self.getHealth());
		boolean melee = HeadRules.isMeleeHit(source.isDirect(), source.isIn(DamageTypeTags.IS_PROJECTILE));
		WearHandler.onDealtDamage(player, self, lost, !self.isAlive(), melee);
	}

	@ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private StatusEffectInstance mhf$cow(StatusEffectInstance effect) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof PlayerEntity player) {
			return WearHandler.maybeHalve(player, effect);
		}
		return effect;
	}

	@Inject(method = "getMovementSpeed", at = @At("RETURN"), cancellable = true)
	private void mhf$foxSneak(CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof PlayerEntity player && player.isSneaking() && WearHandler.wearingFox(player)) {
			cir.setReturnValue(cir.getReturnValueF() * 1.5F);
		}
	}

}
