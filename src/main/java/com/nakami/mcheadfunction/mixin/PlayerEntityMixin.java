package com.nakami.mcheadfunction.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nakami.mcheadfunction.behead.BeheadingHandler;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.PigStuffedAccess;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.head.PlayerHeadState;
import com.nakami.mcheadfunction.wear.WearHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements PlayerHeadAccess, PigStuffedAccess, com.nakami.mcheadfunction.head.GlovePunchAccess {
	@Unique private static final TrackedData<Integer> MHF_GLOVE_PUNCH = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
	@Unique private static final TrackedData<Long> MHF_GLOVE_PUNCH_AT = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.LONG);
	@Override public int mhf$glovePunchStage() {
		PlayerEntity self = (PlayerEntity) (Object) this;
		long age = self.getWorld().getTime() - self.getDataTracker().get(MHF_GLOVE_PUNCH_AT);
		return age >= 0 && age < 10 ? self.getDataTracker().get(MHF_GLOVE_PUNCH) : 1;
	}
	@Override public void mhf$glovePunch(int stage) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		self.getDataTracker().set(MHF_GLOVE_PUNCH, stage);
		self.getDataTracker().set(MHF_GLOVE_PUNCH_AT, self.getWorld().getTime());
	}

	@Unique
	private static final TrackedData<Integer> MHF_STUFFED = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);

	@Unique
	private final PlayerHeadState mhf$state = new PlayerHeadState();

	@Override
	public PlayerHeadState mhf$state() {
		return mhf$state;
	}

	@Override
	public int mhf$stuffedTicks() {
		return ((PlayerEntity) (Object) this).getDataTracker().get(MHF_STUFFED);
	}

	@Override
	public void mhf$setStuffedTicks(int ticks) {
		((PlayerEntity) (Object) this).getDataTracker().set(MHF_STUFFED, ticks);
	}

	@Inject(method = "initDataTracker", at = @At("RETURN"))
	private void mhf$stuffed(DataTracker.Builder builder, CallbackInfo ci) {
		builder.add(MHF_STUFFED, 0);
		builder.add(MHF_GLOVE_PUNCH, 1);
		builder.add(MHF_GLOVE_PUNCH_AT, -100L);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	private void mhf$write(NbtCompound nbt, CallbackInfo ci) {
		mhf$state.write(nbt);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	private void mhf$read(NbtCompound nbt, CallbackInfo ci) {
		mhf$state.read(nbt);
		mhf$setStuffedTicks(mhf$state.stuffedTicks);
	}

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void mhf$attack(Entity target, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (com.nakami.mcheadfunction.wear.GoatCharge.isStunned(self)) { ci.cancel(); return; }
		if (self.getWorld().isClient()) {
			return;
		}
		if (HeadItems.isHead(self.getMainHandStack())) {
			ci.cancel();
			return;
		}
		if (target instanceof LivingEntity living && BeheadingHandler.tryShake(self, living)) {
			ci.cancel();
		}
	}

	@WrapMethod(method = "eatFood")
	private ItemStack mhf$eat(World world, ItemStack stack, FoodComponent food, Operation<ItemStack> original) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		// The final bite empties the original stack before the post-meal callback.
		ItemStack eaten = stack.copy();
		ItemStack result = original.call(world, stack, WearHandler.foodForEating(self, eaten, food));
		if (!world.isClient()) {
			WearHandler.onEat(self, eaten, food);
		}
		return result;
	}
}
