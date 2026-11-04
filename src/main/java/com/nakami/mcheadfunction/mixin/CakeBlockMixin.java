package com.nakami.mcheadfunction.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.nakami.mcheadfunction.wear.WearHandler;
import com.nakami.mcheadfunction.wear.ZombieBlood;
import net.minecraft.block.CakeBlock;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Placed cake bypasses PlayerEntity.eatFood, but obeys the same worn diet. */
@Mixin(CakeBlock.class)
public class CakeBlockMixin {
    @WrapOperation(method = "tryEat", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;add(IF)V"))
    private static void mhf$zombieCake(HungerManager hunger, int food, float saturation, Operation<Void> original,
            @Local(argsOnly = true) PlayerEntity player) {
        if (!ZombieBlood.active(player)) { original.call(hunger, food, saturation); return; }
        if (!player.getWorld().isClient()) {
            WearHandler.onEat(player, new ItemStack(Items.CAKE), new FoodComponent.Builder().nutrition(food).build());
        }
    }
}
