package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.behead.BeheadingHandler;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.head.PlayerHeadState;
import com.nakami.mcheadfunction.wear.WearHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements PlayerHeadAccess {
	@Unique
	private final PlayerHeadState mhf$state = new PlayerHeadState();

	@Override
	public PlayerHeadState mhf$state() {
		return mhf$state;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	private void mhf$write(NbtCompound nbt, CallbackInfo ci) {
		mhf$state.write(nbt);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	private void mhf$read(NbtCompound nbt, CallbackInfo ci) {
		mhf$state.read(nbt);
	}

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void mhf$attack(Entity target, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
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

	@Inject(method = "eatFood", at = @At("RETURN"))
	private void mhf$eat(World world, ItemStack stack, FoodComponent food, CallbackInfoReturnable<ItemStack> cir) {
		WearHandler.onEat((PlayerEntity) (Object) this, stack, food);
	}
}
