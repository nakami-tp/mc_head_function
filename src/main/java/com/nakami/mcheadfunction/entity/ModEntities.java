package com.nakami.mcheadfunction.entity;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<ThrownHeadEntity> THROWN_HEAD = Registry.register(
		Registries.ENTITY_TYPE,
		McHeadFunction.id("thrown_head"),
		EntityType.Builder.<ThrownHeadEntity>create(ThrownHeadEntity::new, SpawnGroup.MISC)
			.dimensions(0.5F, 0.5F)
			.maxTrackingRange(8)
			.trackingTickInterval(1)
			.build("thrown_head")
	);
	public static final EntityType<HeadAmmoEntity> HEAD_AMMO = Registry.register(
		Registries.ENTITY_TYPE,
		McHeadFunction.id("head_ammo"),
		EntityType.Builder.<HeadAmmoEntity>create(HeadAmmoEntity::new, SpawnGroup.MISC)
			.dimensions(0.4F, 0.4F)
			.maxTrackingRange(8)
			.trackingTickInterval(1)
			.build("head_ammo")
	);

	private ModEntities() {
	}

	public static void register() {
		// static init
	}
}
