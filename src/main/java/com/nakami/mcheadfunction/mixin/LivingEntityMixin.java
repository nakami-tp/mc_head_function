package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
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
public abstract class LivingEntityMixin extends Entity implements HeadlessAccess, com.nakami.mcheadfunction.head.HeadStunAccess {
	@Unique
	private static final TrackedData<Boolean> MHF_HEADLESS = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	@Unique
	private static final TrackedData<Boolean> MHF_STUNNED = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	@Override public boolean mhf$isStunned() { return dataTracker.get(MHF_STUNNED); }
	@Override public void mhf$setStunned(boolean stunned) { dataTracker.set(MHF_STUNNED, stunned); }

	@Unique
	private float mhf$healthBeforeDamage;

	protected LivingEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void mhf$tickStun(CallbackInfo ci) {
		com.nakami.mcheadfunction.wear.GoatCharge.tickTarget((LivingEntity) (Object) this);
	}

	@Inject(method = "jump", at = @At("HEAD"), cancellable = true)
	private void mhf$stunJump(CallbackInfo ci) {
		if (com.nakami.mcheadfunction.wear.GoatCharge.isStunned((LivingEntity) (Object) this)) ci.cancel();
	}

	@Inject(method = "initDataTracker", at = @At("RETURN"))
	private void mhf$initHeadless(DataTracker.Builder builder, CallbackInfo ci) {
		builder.add(MHF_HEADLESS, false);
		builder.add(MHF_STUNNED, false);
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

	@Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
	private void mhf$creeperLanding(float distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayerEntity player && PlayerHeadAccess.state(player).creeperFallProtected) {
			PlayerHeadAccess.state(player).creeperFallProtected = false;
			player.fallDistance = 0;
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void mhf$beforeDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayerEntity player) {
			if (source.isIn(DamageTypeTags.IS_FALL) && PlayerHeadAccess.state(player).creeperFallProtected) {
				PlayerHeadAccess.state(player).creeperFallProtected = false;
				cir.setReturnValue(false);
				return;
			}
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
		if (source.getSource() instanceof net.minecraft.entity.projectile.LlamaSpitEntity spit && spit.getOwner() instanceof ServerPlayerEntity owner) {
			if (com.nakami.mcheadfunction.progression.HeadMastery.mastered(owner, com.nakami.mcheadfunction.head.HeadType.LLAMA)) amount *= 2;
		}
		if (self instanceof ServerPlayerEntity player) {
			if (PlayerHeadAccess.state(player).rabbitFallProtected && source.isIn(DamageTypeTags.IS_FALL)) return 0;
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
		float lost = Math.max(0.0F, mhf$healthBeforeDamage - self.getHealth());
		if (source.getAttacker() instanceof net.minecraft.entity.passive.BeeEntity bee) com.nakami.mcheadfunction.wear.BeeCommand.onDamage(bee, self, lost);
		if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;
		if (lost > 0 && source.getSource() instanceof net.minecraft.entity.projectile.LlamaSpitEntity) com.nakami.mcheadfunction.progression.HeadMastery.practice(player, com.nakami.mcheadfunction.head.HeadType.LLAMA);
		if (lost > 0 && source.getSource() instanceof com.nakami.mcheadfunction.entity.HeadAmmoEntity) com.nakami.mcheadfunction.progression.HeadMastery.practice(player, com.nakami.mcheadfunction.head.HeadType.CREEPER);
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
			cir.setReturnValue(cir.getReturnValueF() * (com.nakami.mcheadfunction.progression.HeadMastery.mastered(player, com.nakami.mcheadfunction.head.HeadType.FOX) ? 1.8F : 1.5F));
		}
	}

}
