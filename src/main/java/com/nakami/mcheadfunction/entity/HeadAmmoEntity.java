package com.nakami.mcheadfunction.entity;

import java.util.UUID;
import com.nakami.mcheadfunction.head.HeadSounds;
import net.minecraft.sound.SoundEvents;
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
		if (!planted) HeadSounds.play(this, SoundEvents.BLOCK_STONE_HIT, 0.4F, 0.85F);
		setVelocity(Vec3d.ZERO);
		setNoGravity(true);
		planted = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient()) {
			if (age % 2 == 0) getWorld().addParticle(net.minecraft.particle.ParticleTypes.SMOKE, getX(), getY() + 0.35, getZ(), 0, 0.02, 0);
			return;
		}
		if (isInLava() || getY() < getWorld().getBottomY() - 16) {
			discard();
			return;
		}
		if (planted) {
			plantTicks++;
			if (plantTicks == 1 || plantTicks == 160 || plantTicks == 180 || plantTicks == 190) {
				HeadSounds.play(this, SoundEvents.BLOCK_DISPENSER_FAIL, 0.18F, 0.8F + plantTicks / 200F);
			}
			if (plantTicks > 200) {
				detonate();
			}
		}
	}

	public void detonate() {
		if (!(getWorld() instanceof ServerWorld world) || isRemoved()) {
			return;
		}
		java.util.UUID ownerId = getOwner() != null ? getOwner().getUuid() : null;
		world.createExplosion(
			this,
			world.getDamageSources().explosion(this, getOwner()),
			new OwnerSafeExplosionBehavior(ownerId, true),
			getX(),
			getY(),
			getZ(),
			2.0F,
			false,
			World.ExplosionSourceType.NONE
		);
		if (getOwner() instanceof PlayerEntity player) {
			Vec3d push = player.getPos().subtract(getPos());
			if (push.lengthSquared() < 64) {
				Vec3d knock = push.normalize().multiply(1.6).add(0, 0.55, 0);
				player.addVelocity(knock.x, knock.y, knock.z);
				player.velocityModified = true;
			}
		}
		discard();
	}

	public UUID ownerId() {
		return getOwner() != null ? getOwner().getUuid() : null;
	}
}
