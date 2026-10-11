package com.nakami.mcheadfunction.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityDropInvoker {
	@Invoker("drop")
	void mhf$drop(ServerWorld world, DamageSource source);

	@Invoker("getDeathSound")
	SoundEvent mhf$deathSound();

	@Invoker("getSoundVolume")
	float mhf$soundVolume();
}
