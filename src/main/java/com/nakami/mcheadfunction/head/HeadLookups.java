package com.nakami.mcheadfunction.head;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;

public final class HeadLookups {
	private HeadLookups() {
	}

	public static HeadType fromVictim(LivingEntity entity) {
		if (entity instanceof CreeperEntity creeper) {
			return creeper.shouldRenderOverlay() ? HeadType.CHARGED_CREEPER : HeadType.CREEPER;
		}
		if (entity instanceof ZombieEntity && entity.getType() == EntityType.ZOMBIE) {
			return HeadType.ZOMBIE;
		}
		if (entity instanceof EndermanEntity) {
			return HeadType.ENDERMAN;
		}
		if (entity instanceof BlazeEntity) {
			return HeadType.BLAZE;
		}
		if (entity instanceof IronGolemEntity) {
			return HeadType.IRON_GOLEM;
		}
		if (entity instanceof GoatEntity) {
			return HeadType.GOAT;
		}
		if (entity instanceof PigEntity) {
			return HeadType.PIG;
		}
		if (entity instanceof CowEntity && entity.getType() == EntityType.COW) {
			return HeadType.COW;
		}
		if (entity instanceof SheepEntity) {
			return HeadType.SHEEP;
		}
		if (entity instanceof ChickenEntity) {
			return HeadType.CHICKEN;
		}
		if (entity instanceof RabbitEntity) {
			return HeadType.RABBIT;
		}
		if (entity instanceof FoxEntity) {
			return HeadType.FOX;
		}
		if (entity instanceof WolfEntity) {
			return HeadType.WOLF;
		}
		if (entity instanceof FrogEntity) {
			return HeadType.FROG;
		}
		if (entity instanceof BeeEntity) {
			return HeadType.BEE;
		}
		if (entity instanceof ArmadilloEntity) {
			return HeadType.ARMADILLO;
		}
		if (entity instanceof LlamaEntity && !(entity instanceof TraderLlamaEntity)) {
			return HeadType.LLAMA;
		}
		if (entity instanceof BatEntity) {
			return HeadType.BAT;
		}
		return null;
	}

	public static boolean isBoss(LivingEntity entity) {
		return entity instanceof EnderDragonEntity
			|| entity instanceof WitherEntity
			|| entity instanceof WardenEntity;
	}

	public static boolean canBehead(LivingEntity entity) {
		if (entity instanceof PlayerEntity || entity.isBaby() || isBoss(entity)) {
			return false;
		}
		if (entity instanceof MobEntity mob && HeadlessAccess.isHeadless(mob)) {
			return false;
		}
		return fromVictim(entity) != null;
	}

	public static HeadType worn(PlayerEntity player) {
		return HeadItems.ofStack(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
	}
}
