package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void mhf$noSecondHead(ItemStack stack, CallbackInfoReturnable<ItemEntity> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LivingEntity living && HeadlessAccess.isHeadless(living) && HeadItems.isHead(stack)) {
			cir.setReturnValue(null);
		}
	}
	@Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void mhf$chestMeal(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
		Entity self = (Entity) (Object) this;
		if (com.nakami.mcheadfunction.haunt.HauntMeal.capturing(self)) {
			com.nakami.mcheadfunction.haunt.HauntMeal.collect(stack);
			cir.setReturnValue(new ItemEntity(self.getWorld(), self.getX(), self.getY() + yOffset, self.getZ(), stack.copy()));
		}
	}
}
