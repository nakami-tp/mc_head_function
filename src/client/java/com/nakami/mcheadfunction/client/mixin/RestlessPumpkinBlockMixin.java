package com.nakami.mcheadfunction.client.mixin;

import com.nakami.mcheadfunction.client.HauntPumpkinEffects;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Render-only air also reveals the floor/neighbor faces underneath a lifted pumpkin. */
@Mixin(ChunkRendererRegion.class)
public abstract class RestlessPumpkinBlockMixin {
	@Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
	private void mhf$restless(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		if (HauntPumpkinEffects.replacesBlock(pos, cir.getReturnValue())) cir.setReturnValue(Blocks.AIR.getDefaultState());
	}
}
