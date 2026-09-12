package com.nakami.mcheadfunction.entity;

import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Creeper head-skill ammo. Detonates and vanishes; never recycles into a head.
 */
public class HeadAmmoEntity extends ThrownItemEntity {
	private boolean planted;
	private int plantTicks;

	public HeadAmmoEntity(EntityType<? extends HeadAmmoEntity> type, World world) {
		super(type, world);
	}

	public HeadAmmoEntity(World world, LivingEntity owner) {
		super(ModEntities.HEAD_AMMO, owner, world);
		setItem(Items.CREEPER_HEAD.getDefaultStack());
	}

	@Override
	protected Item getDefaultItem() {
		return Items.CREEPER_HEAD;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return source.isIn(DamageTypeTags.IS_EXPLOSION) ? false : super.damage(source, amount);
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		if (getWorld().isClient()) {
			return;
		}
		setVelocity(Vec3d.ZERO);
		setNoGravity(true);
		planted = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient()) {
			return;
		}
		if (isInLava() || getY() < getWorld().getBottomY() - 16) {
			discard();
			return;
		}
		if (planted) {
			plantTicks++;
			if (plantTicks > 200) {
				detonate();
			}
		}
	}

	public void detonate() {
		if (!(getWorld() instanceof ServerWorld world) || isRemoved()) {
			return;
		}
		world.createExplosion(this, getX(), getY(), getZ(), 2.0F, World.ExplosionSourceType.NONE);
		if (getOwner() instanceof PlayerEntity player) {
			Vec3d push = player.getPos().subtract(getPos());
			if (push.lengthSquared() < 64) {
				Vec3d knock = push.normalize().multiply(1.6).add(0, 0.55, 0);
				player.addVelocity(knock.x, knock.y, knock.z);
				player.velocityModified = true;
				player.hurtTime = 0;
			}
		}
		discard();
	}

	public UUID ownerId() {
		return getOwner() != null ? getOwner().getUuid() : null;
	}
}
