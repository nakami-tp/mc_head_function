package com.nakami.mcheadfunction.mixin;

import com.nakami.mcheadfunction.wear.ZombieBlood;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    /** Only automatic allocation: explicit slot placement and existing-stack merges stay vanilla. */
    @Redirect(method = {"addStack(Lnet/minecraft/item/ItemStack;)I", "insertStack(ILnet/minecraft/item/ItemStack;)Z"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getEmptySlot()I"))
    private int mhf$keepZombieHandEmpty(PlayerInventory inventory) {
        if (!ZombieBlood.unarmed(inventory.player)) return inventory.getEmptySlot();
        for (int slot = 0; slot < inventory.main.size(); slot++) {
            if (slot != inventory.selectedSlot && inventory.main.get(slot).isEmpty()) return slot;
        }
        return -1;
    }
}
