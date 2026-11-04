package com.nakami.mcheadfunction.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nakami.mcheadfunction.wear.ZombieBlood;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public class ZombieSprintMixin {
    @ModifyExpressionValue(method = "canSprint", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;getFoodLevel()I"))
    private int mhf$hungrySprint(int food) {
        return ZombieBlood.active((ClientPlayerEntity) (Object) this) ? Math.max(7, food) : food;
    }
}
