package com.nakami.mcheadfunction.mixin;

import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreeperEntity.class)
public interface CreeperFuseAccessor {
	@Accessor("fuseTime")
	void mhf$setFuseTime(int ticks);

	@Invoker("explode")
	void mhf$explode();
}
