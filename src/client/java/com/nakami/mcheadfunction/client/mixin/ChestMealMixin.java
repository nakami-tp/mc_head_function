package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.ChestMealAnimation;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlockEntity.class)
public abstract class ChestMealMixin {
	@Inject(method = "getAnimationProgress", at = @At("RETURN"), cancellable = true)
	private void mhf$chew(float delta, CallbackInfoReturnable<Float> cir) {
		var chest = (ChestBlockEntity) (Object) this;
		var tongue = ChestMealAnimation.tongue(chest.getWorld(), chest.getPos());
		var state = chest.getCachedState();
		if (tongue == null && state.contains(ChestBlock.CHEST_TYPE) && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
			tongue = ChestMealAnimation.tongue(chest.getWorld(), chest.getPos().offset(ChestBlock.getFacing(state)));
		if (tongue != null) cir.setReturnValue(tongue.lidProgress(delta));
	}
}
