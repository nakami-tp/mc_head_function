package com.nakami.mcheadfunction.entity;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.net.SonarS2CPayload;
import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ThrownHeadEntity extends ThrownItemEntity {
	private static final TrackedData<String> HEAD_TYPE = DataTracker.registerData(ThrownHeadEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Boolean> ACTIVE = DataTracker.registerData(ThrownHeadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	private HeadType.ThrowStyle style = HeadType.ThrowStyle.DEFAULT;
	private int activeTicks;
	private double traveled;
	private double rollTraveled;
	private BlockPos eatOrigin;
	private final ArrayDeque<BlockPos> eatQueue = new ArrayDeque<>();
	private int eatCount;
	private int biteCooldown;
	private boolean recycled;

	public ThrownHeadEntity(EntityType<? extends ThrownHeadEntity> type, World world) {
		super(type, world);
	}

	public ThrownHeadEntity(World world, LivingEntity owner, HeadType type, HeadType.ThrowStyle style) {
		super(ModEntities.THROWN_HEAD, owner, world);
		setHeadType(type);
		this.style = style;
		setItem(new ItemStack(HeadItems.item(type)));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(HEAD_TYPE, HeadType.PIG.name());
		builder.add(ACTIVE, false);
	}

	public void setHeadType(HeadType type) {
		this.dataTracker.set(HEAD_TYPE, type.name());
	}

	public HeadType getHeadType() {
		try {
			return HeadType.valueOf(this.dataTracker.get(HEAD_TYPE));
		} catch (IllegalArgumentException ignored) {
			return HeadType.PIG;
		}
	}

	public boolean isActive() {
		return this.dataTracker.get(ACTIVE);
	}

	private void setActive(boolean active) {
		this.dataTracker.set(ACTIVE, active);
	}

	@Override
	protected Item getDefaultItem() {
		return HeadItems.item(getHeadType());
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void tick() {
		if (recycled) {
			return;
		}
		if (!getWorld().isClient()) {
			if (isInLava() || getY() < getWorld().getBottomY() - 16) {
				discard();
				return;
			}
			if (isActive()) {
				tickActive();
				return;
			}
			if (style == HeadType.ThrowStyle.KNOCK_OFF) {
				traveled += getVelocity().length();
				if (traveled >= HeadRules.KNOCK_OFF_DISTANCE) {
					recycle();
					return;
				}
			}
			if (style == HeadType.ThrowStyle.CHARGED_PIERCE) {
				pierceTick();
			}
			if (style == HeadType.ThrowStyle.BLAZE_TRAIL) {
				blazeTrailTick();
			}
		}
		super.tick();
	}

	private void pierceTick() {
		ServerWorld world = (ServerWorld) getWorld();
		traveled += getVelocity().length();
		BlockPos pos = getBlockPos();
		BlockState state = world.getBlockState(pos);
		if (!state.isAir() && state.getHardness(world, pos) >= 0 && !state.isOf(Blocks.BEDROCK)) {
			world.breakBlock(pos, true, this);
		}
		if (traveled >= 8) {
			world.createExplosion(this, getX(), getY(), getZ(), 4.0F, World.ExplosionSourceType.NONE);
			recycle();
		}
	}

	private void blazeTrailTick() {
		ServerWorld world = (ServerWorld) getWorld();
		traveled += getVelocity().length();
		BlockPos pos = getBlockPos();
		if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolid()) {
			world.setBlockState(pos, Blocks.FIRE.getDefaultState());
		}
		hurtNearby(0, true);
		if (traveled >= 16) {
			recycle();
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		if (getWorld().isClient() || recycled || isActive()) {
			return;
		}
		if (style == HeadType.ThrowStyle.CHARGED_PIERCE || style == HeadType.ThrowStyle.BLAZE_TRAIL) {
			if (hitResult.getType() == HitResult.Type.ENTITY) {
				return;
			}
			if (style == HeadType.ThrowStyle.BLAZE_TRAIL) {
				recycle();
			}
			return;
		}
		if (hitResult.getType() == HitResult.Type.ENTITY) {
			onEntityHit((EntityHitResult) hitResult);
		} else if (hitResult.getType() == HitResult.Type.BLOCK) {
			onBlockHit((BlockHitResult) hitResult);
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		if (!(getWorld() instanceof ServerWorld world) || recycled) {
			return;
		}
		Entity target = hit.getEntity();
		switch (style) {
			case DEFAULT, KNOCK_OFF -> {
				if (target instanceof LivingEntity living && !ownedBy(living)) {
					living.damage(world.getDamageSources().thrown(this, getOwner()), style == HeadType.ThrowStyle.KNOCK_OFF ? 0 : HeadRules.DEFAULT_THROW_DAMAGE);
				}
				recycle();
			}
			case GOAT_RAM -> {
				if (target instanceof LivingEntity living && !ownedBy(living)) {
					living.damage(world.getDamageSources().thrown(this, getOwner()), 4);
					living.addVelocity(getVelocity().x, 0.9, getVelocity().z);
					living.velocityModified = true;
				}
				recycle();
			}
			case CREEPER_BLAST -> explodeAndRecycle(3.0F);
			case ZOMBIE_HOP, GOLEM_ROLL -> enterActive();
			case ENDERMAN_EAT -> recycle();
			case BEE_SUMMON -> summonBeesAndRecycle();
			case BAT_SONAR -> sonarAndRecycle();
			default -> recycle();
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		if (!(getWorld() instanceof ServerWorld) || recycled) {
			return;
		}
		switch (style) {
			case ZOMBIE_HOP, GOLEM_ROLL -> enterActive();
			case ENDERMAN_EAT -> startEating(hit.getBlockPos());
			case CREEPER_BLAST -> explodeAndRecycle(3.0F);
			case BEE_SUMMON -> summonBeesAndRecycle();
			case BAT_SONAR -> sonarAndRecycle();
			default -> recycle();
		}
	}

	private void enterActive() {
		setActive(true);
		setVelocity(Vec3d.ZERO);
		setNoGravity(style == HeadType.ThrowStyle.ZOMBIE_HOP);
		activeTicks = 0;
		if (style == HeadType.ThrowStyle.GOLEM_ROLL) {
			golemShockwave();
			Vec3d dir = getVelocity().multiply(1, 0, 1);
			if (getOwner() != null) {
				dir = getOwner().getRotationVec(1).multiply(1, 0, 1);
			}
			if (dir.lengthSquared() < 0.01) {
				dir = new Vec3d(getOwner() != null ? getOwner().getRotationVec(1).x : 0, 0, getOwner() != null ? getOwner().getRotationVec(1).z : 1);
			}
			setVelocity(dir.normalize().multiply(0.45));
		}
	}

	private void tickActive() {
		activeTicks++;
		if (activeTicks >= HeadRules.ACTIVE_MAX_TICKS) {
			recycle();
			return;
		}
		if (style == HeadType.ThrowStyle.ZOMBIE_HOP) {
			tickZombie();
		} else if (style == HeadType.ThrowStyle.ENDERMAN_EAT) {
			tickEat();
		} else if (style == HeadType.ThrowStyle.GOLEM_ROLL) {
			tickRoll();
		}
	}

	private void tickZombie() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		setNoGravity(false);
		LivingEntity target = nearestLiving(16);
		if (target == null) {
			if (activeTicks > 20) {
				recycle();
			}
			return;
		}
		Vec3d to = target.getPos().subtract(getPos());
		if (this.age % 10 == 0) {
			setVelocity(to.normalize().multiply(0.35).add(0, 0.35, 0));
			world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, getSoundCategory(), 0.4F, 1.2F);
		}
		if (biteCooldown > 0) {
			biteCooldown--;
		}
		if (squaredDistanceTo(target) < 1.6 && biteCooldown <= 0) {
			target.damage(world.getDamageSources().mobProjectile(this, ownerAsLiving()), HeadRules.ZOMBIE_BITE_DAMAGE);
			biteCooldown = 15;
		}
	}

	private void startEating(BlockPos origin) {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		BlockState state = world.getBlockState(origin);
		if (!canEat(world, origin, state)) {
			recycle();
			return;
		}
		eatOrigin = origin;
		eatQueue.clear();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		frontier.add(origin);
		seen.add(origin);
		Block id = state.getBlock();
		while (!frontier.isEmpty() && seen.size() < HeadRules.ENDERMAN_EAT_MAX) {
			BlockPos pos = frontier.removeFirst();
			eatQueue.add(pos);
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.offset(direction);
				if (seen.add(next) && world.getBlockState(next).isOf(id) && canEat(world, next, world.getBlockState(next))) {
					frontier.add(next);
				}
			}
		}
		setActive(true);
		setVelocity(Vec3d.ZERO);
		setNoGravity(true);
		activeTicks = 0;
		eatCount = 0;
	}

	private void tickEat() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (this.age % 5 != 0) {
			return;
		}
		while (!eatQueue.isEmpty()) {
			BlockPos pos = eatQueue.removeFirst();
			BlockState state = world.getBlockState(pos);
			if (!canEat(world, pos, state)) {
				continue;
			}
			world.breakBlock(pos, true, this);
			world.spawnParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.05);
			eatCount++;
			setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (eatCount >= HeadRules.ENDERMAN_EAT_MAX) {
				recycle();
			}
			return;
		}
		recycle();
	}

	private boolean canEat(World world, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getHardness(world, pos) < 0 || state.isOf(Blocks.BEDROCK)) {
			return false;
		}
		BlockEntity blockEntity = world.getBlockEntity(pos);
		boolean chestLike = state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL)
			|| state.isOf(Blocks.SHULKER_BOX);
		return HeadRules.canEndermanEat(false, blockEntity != null, chestLike);
	}

	private void golemShockwave() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		world.createExplosion(this, getX(), getY(), getZ(), 0.0F, false, World.ExplosionSourceType.NONE);
		hurtNearby(0, false);
		for (LivingEntity living : nearbyLiving(5)) {
			living.addVelocity(0, 1.15, 0);
			living.velocityModified = true;
		}
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, getSoundCategory(), 1.0F, 0.7F);
	}

	private void tickRoll() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		rollTraveled += getVelocity().horizontalLength();
		for (LivingEntity living : nearbyLiving(1.2)) {
			living.damage(world.getDamageSources().thrown(this, getOwner()), 6);
			Vec3d push = living.getPos().subtract(getPos()).normalize().multiply(1.4).add(0, 0.4, 0);
			living.addVelocity(push.x, push.y, push.z);
			living.velocityModified = true;
		}
		if (rollTraveled >= 8 || horizontalCollision) {
			recycle();
		}
	}

	private void explodeAndRecycle(float radius) {
		if (getWorld() instanceof ServerWorld world) {
			world.createExplosion(this, getX(), getY(), getZ(), radius, World.ExplosionSourceType.NONE);
		}
		recycle();
	}

	private void summonBeesAndRecycle() {
		if (getWorld() instanceof ServerWorld world) {
			UUID ownerId = getOwner() != null ? getOwner().getUuid() : null;
			for (int i = 0; i < HeadRules.BEE_SUMMON_COUNT; i++) {
				BeeEntity bee = EntityType.BEE.create(world);
				if (bee == null) {
					continue;
				}
				bee.refreshPositionAndAngles(getX() + (i - 1) * 0.4, getY(), getZ(), getYaw(), 0);
				if (ownerId != null) {
					bee.addCommandTag("mhf_owner:" + ownerId);
				}
				world.spawnEntity(bee);
			}
		}
		recycle();
	}

	private void sonarAndRecycle() {
		if (getOwner() instanceof ServerPlayerEntity player) {
			ServerPlayNetworking.send(player, new SonarS2CPayload());
		}
		recycle();
	}

	public void recycle() {
		if (recycled || getWorld().isClient()) {
			return;
		}
		recycled = true;
		if (!isInLava() && getY() >= getWorld().getBottomY()) {
			ItemStack stack = getStack().copy();
			if (!stack.isEmpty()) {
				ItemEntity item = new ItemEntity(getWorld(), getX(), getY(), getZ(), stack);
				item.setPickupDelay(10);
				getWorld().spawnEntity(item);
			}
		}
		discard();
	}

	private void hurtNearby(float damage, boolean ignite) {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		for (LivingEntity living : nearbyLiving(1.4)) {
			if (damage > 0) {
				living.damage(world.getDamageSources().thrown(this, getOwner()), damage);
			}
			if (ignite) {
				living.setOnFireFor(4);
			}
		}
	}

	private LivingEntity nearestLiving(double range) {
		LivingEntity best = null;
		double bestDist = range * range;
		for (LivingEntity living : nearbyLiving(range)) {
			double dist = squaredDistanceTo(living);
			if (dist < bestDist) {
				bestDist = dist;
				best = living;
			}
		}
		return best;
	}

	private java.util.List<LivingEntity> nearbyLiving(double range) {
		Box box = getBoundingBox().expand(range);
		return getWorld().getEntitiesByClass(LivingEntity.class, box, this::canHarm);
	}

	private boolean canHarm(LivingEntity living) {
		if (!living.isAlive() || living.isSpectator()) {
			return false;
		}
		Entity owner = getOwner();
		return owner == null || !owner.getUuid().equals(living.getUuid());
	}

	private boolean ownedBy(Entity entity) {
		Entity owner = getOwner();
		return owner != null && owner.getUuid().equals(entity.getUuid());
	}

	private LivingEntity ownerAsLiving() {
		return getOwner() instanceof LivingEntity living ? living : null;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("headType", getHeadType().name());
		nbt.putString("style", style.name());
		nbt.putBoolean("active", isActive());
		nbt.putInt("activeTicks", activeTicks);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("headType")) {
			setHeadType(HeadType.valueOf(nbt.getString("headType")));
		}
		if (nbt.contains("style")) {
			style = HeadType.ThrowStyle.valueOf(nbt.getString("style"));
		}
		setActive(nbt.getBoolean("active"));
		activeTicks = nbt.getInt("activeTicks");
	}
}
