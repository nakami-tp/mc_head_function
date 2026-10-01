package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.head.HeadEquipment;
import com.nakami.mcheadfunction.head.HeadItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void useTableHead(World world, PlayerEntity player, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (HeadItems.isHead(player.getStackInHand(hand))) {
			cir.setReturnValue(HeadEquipment.equip(world, player, hand));
		}
	}
}
