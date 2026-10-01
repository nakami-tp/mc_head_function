package com.nakami.mcheadfunction.entity;

import java.util.UUID;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import com.nakami.mcheadfunction.wear.CreeperLaunch;
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
	private static final TrackedData<Integer> ATTACHED_FACE =
		DataTracker.registerData(HeadAmmoEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private Vec3d plantedPosition;
	private int plantTicks;

	public HeadAmmoEntity(EntityType<? extends HeadAmmoEntity> type, World world) {
		super(type, world);
	}

	public HeadAmmoEntity(World world, LivingEntity owner) {
		super(ModEntities.HEAD_AMMO, owner, world);
		setItem(Items.CREEPER_HEAD.getDefaultStack());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ATTACHED_FACE, -1);
	}

	public boolean isPlanted() { return dataTracker.get(ATTACHED_FACE) >= 0; }
	public Direction attachedFace() {
		return Direction.byId(Math.max(0, dataTracker.get(ATTACHED_FACE)));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("AttachedFace", dataTracker.get(ATTACHED_FACE));
		nbt.putInt("PlantTicks", plantTicks);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		dataTracker.set(ATTACHED_FACE, nbt.contains("AttachedFace") ? nbt.getInt("AttachedFace") : -1);
		plantTicks = nbt.getInt("PlantTicks");
		if (isPlanted()) {
			plantedPosition = getPos();
			setVelocity(Vec3d.ZERO);
			setNoGravity(true);
		}
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
		if (isPlanted() || hitResult.getType() == HitResult.Type.MISS) return;
		var face = hitResult instanceof BlockHitResult block
			? block.getSide() : Direction.UP;
		Vec3d contact = hitResult.getPos();
		double offset = face == Direction.UP ? 0.005
			: face == Direction.DOWN ? getHeight() + 0.005 : getWidth() / 2 + 0.005;
		plantedPosition = contact.add(Vec3d.of(face.getVector()).multiply(offset));
		setPosition(plantedPosition);
		setVelocity(Vec3d.ZERO);
		setNoGravity(true);
		dataTracker.set(ATTACHED_FACE, face.getId());
		if (!getWorld().isClient()) HeadSounds.play(this, SoundEvents.BLOCK_STONE_HIT, 0.4F, 0.85F);
	}

	@Override
	public void tick() {
		super.tick();
		// ThrownEntity may finish its movement after onCollision; pin to the contact afterwards.
		if (isPlanted()) {
			if (!getWorld().isClient() && plantedPosition != null) setPosition(plantedPosition);
			setVelocity(Vec3d.ZERO);
		}
		if (getWorld().isClient()) {
			if (age % 4 == 0) {
				Vec3d spark = getPos().add(0, getHeight() / 2, 0);
				if (isPlanted()) spark = spark.add(Vec3d.of(attachedFace().getVector()).multiply(0.08));
				getWorld().addParticle(ParticleTypes.FLAME, spark.x, spark.y, spark.z, 0, 0.005, 0);
				getWorld().addParticle(ParticleTypes.SMOKE, spark.x, spark.y, spark.z, 0, 0.015, 0);
			}
			return;
		}
		if (isInLava() || getY() < getWorld().getBottomY() - 16) {
			discard();
			return;
		}
		if (isPlanted()) {
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
		UUID ownerId = getOwner() != null ? getOwner().getUuid() : null;
		world.createExplosion(
			this,
			world.getDamageSources().explosion(this, getOwner()),
			new OwnerSafeExplosionBehavior(ownerId, false),
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
				Vec3d knock = push.normalize().multiply(0.8).add(0, 0.275, 0);
				player.addVelocity(knock.x, knock.y, knock.z);
				player.velocityModified = true;
				if (player instanceof ServerPlayerEntity serverPlayer) {
					CreeperLaunch.protect(serverPlayer);
				}
			}
		}
		discard();
	}

	public UUID ownerId() {
		return getOwner() != null ? getOwner().getUuid() : null;
	}
}
