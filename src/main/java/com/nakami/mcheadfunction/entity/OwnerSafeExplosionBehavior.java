package com.nakami.mcheadfunction.entity;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;

/**
 * Explosion that never damages the thrower/wearer. Knockback for that owner is optional.
 */
public final class OwnerSafeExplosionBehavior extends ExplosionBehavior {
	private final UUID ownerId;
	private final boolean knockbackOwner;

	public OwnerSafeExplosionBehavior(UUID ownerId, boolean knockbackOwner) {
		this.ownerId = ownerId;
		this.knockbackOwner = knockbackOwner;
	}

	@Override
	public boolean shouldDamage(Explosion explosion, Entity entity) {
		if (ownerId != null && ownerId.equals(entity.getUuid())) {
			return false;
		}
		return super.shouldDamage(explosion, entity);
	}

	@Override
	public float getKnockbackModifier(Entity entity) {
		if (!knockbackOwner && ownerId != null && ownerId.equals(entity.getUuid())) {
			return 0.0F;
		}
		return super.getKnockbackModifier(entity);
	}
}
