package com.nakami.mcheadfunction.entity;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadEffects;
import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.net.SonarS2CPayload;
import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
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
	private Block eatBlock;
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
		// The superclass calls this while building dataTracker, before it is assigned.
		// The actual head item is set after construction and tracked by ThrownItemEntity.
		return HeadItems.item(HeadType.PIG);
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
		if (getWorld().isClient() && age % 2 == 0) {
			getWorld().addParticle(HeadEffects.particle(getHeadType()), getX(), getY() + 0.2, getZ(), 0, 0.015, 0);
		}
		if (!getWorld().isClient()) {
			if (isInLava() || getY() < getWorld().getBottomY() - 16) {
				discard();
				return;
			}
			if (style == HeadType.ThrowStyle.BAT_SONAR) {
				releaseSonar();
				return;
			}
			if (isActive()) {
				tickActive();
				if (recycled) {
					return;
				}
				super.tick();
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
				if (recycled) {
					return;
				}
			}
			if (style == HeadType.ThrowStyle.BLAZE_TRAIL) {
				blazeTrailTick();
				if (recycled) {
					return;
				}
			}
		}
		super.tick();
	}

	private void pierceTick() {
		ServerWorld world = (ServerWorld) getWorld();
		Vec3d start = getPos();
		Vec3d vel = getVelocity();
		double stepLen = vel.length();
		traveled += stepLen;
		int steps = Math.max(1, (int) Math.ceil(stepLen / HeadRules.PIERCE_STEP));
		for (int i = 0; i <= steps; i++) {
			Vec3d point = start.add(vel.multiply((double) i / steps));
			breakIfPossible(world, BlockPos.ofFloored(point));
			damagePierceTargets(world, point);
		}
		if (traveled >= HeadRules.PIERCE_DISTANCE) {
			explodeAround(4.0F, false);
			recycle();
		}
	}

	private void breakIfPossible(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!state.isAir() && state.getHardness(world, pos) >= 0 && !state.isOf(Blocks.BEDROCK)) {
			world.breakBlock(pos, true, this);
		}
	}

	private void damagePierceTargets(ServerWorld world, Vec3d point) {
		Box box = new Box(point, point).expand(0.45);
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, this::canHarm)) {
			living.damage(world.getDamageSources().thrown(this, getOwner()), HeadRules.PIERCE_DAMAGE);
		}
	}

	private void blazeTrailTick() {
		ServerWorld world = (ServerWorld) getWorld();
		Vec3d start = getPos();
		Vec3d vel = getVelocity();
		double stepLen = vel.length();
		traveled += stepLen;
		int steps = Math.max(1, (int) Math.ceil(stepLen / HeadRules.PIERCE_STEP));
		for (int i = 0; i <= steps; i++) {
			Vec3d point = start.add(vel.multiply((double) i / steps));
			BlockPos pos = BlockPos.ofFloored(point);
			if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolid()) {
				world.setBlockState(pos, Blocks.FIRE.getDefaultState());
			}
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
		if (style == HeadType.ThrowStyle.CHARGED_PIERCE) {
			return;
		}
		if (style == HeadType.ThrowStyle.BLAZE_TRAIL) {
			if (hitResult.getType() == HitResult.Type.ENTITY) {
				return;
			}
			HeadSounds.impact(this, getHeadType());
			recycle();
			return;
		}
		HeadSounds.impact(this, getHeadType());
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
			case BAT_SONAR -> releaseSonar();
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
			case BAT_SONAR -> releaseSonar();
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
			Vec3d dir = Vec3d.ZERO;
			if (getOwner() != null) {
				dir = getOwner().getRotationVec(1).multiply(1, 0, 1);
			}
			if (dir.lengthSquared() < 0.01) {
				dir = new Vec3d(0, 0, 1);
			}
			setNoGravity(false);
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
		if (activeTicks % 10 == 0) {
			setVelocity(to.normalize().multiply(0.35).add(0, 0.35, 0));
			HeadSounds.play(this, SoundEvents.BLOCK_WOOL_FALL, 0.18F, 0.7F);
		}
		if (biteCooldown > 0) {
			biteCooldown--;
		}
		if (squaredDistanceTo(target) < 1.6 && biteCooldown <= 0) {
			target.damage(world.getDamageSources().mobProjectile(this, ownerAsLiving()), HeadRules.ZOMBIE_BITE_DAMAGE);
			HeadEffects.burst(world, target.getPos().add(0, 0.5, 0), HeadType.ZOMBIE);
			HeadSounds.play(this, SoundEvents.ENTITY_GENERIC_EAT, 0.45F, 0.75F);
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
		eatBlock = state.getBlock();
		eatQueue.clear();
		eatQueue.addAll(HeadRules.collectConnected(
			origin,
			HeadRules.ENDERMAN_EAT_MAX,
			pos -> {
				BlockState next = world.getBlockState(pos);
				return next.isOf(eatBlock) && canEat(world, pos, next);
			},
			pos -> {
				List<BlockPos> neighbors = new ArrayList<>(6);
				for (Direction direction : Direction.values()) {
					neighbors.add(pos.offset(direction));
				}
				return neighbors;
			}
		));
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
		if (!HeadRules.shouldEatThisTick(activeTicks)) {
			return;
		}
		while (!eatQueue.isEmpty()) {
			BlockPos pos = eatQueue.removeFirst();
			BlockState state = world.getBlockState(pos);
			if (eatBlock == null || !state.isOf(eatBlock) || !canEat(world, pos, state)) {
				continue;
			}
			world.breakBlock(pos, true, this);
			HeadSounds.play(this, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.16F, 0.8F + eatCount * 0.025F);
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
		explodeAround(0.0F, false);
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
		if (activeTicks <= 10 && activeTicks % 2 == 0) HeadEffects.ring(world, getPos(), activeTicks * 0.5);
		if (activeTicks % 8 == 0 && getVelocity().horizontalLengthSquared() > 0.01) {
			HeadSounds.play(this, SoundEvents.BLOCK_GRINDSTONE_USE, 0.15F, 0.7F);
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
		explodeAround(radius, false);
		recycle();
	}

	private void explodeAround(float radius, boolean knockbackOwner) {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		UUID ownerId = getOwner() != null ? getOwner().getUuid() : null;
		world.createExplosion(
			this,
			world.getDamageSources().explosion(this, getOwner()),
			new OwnerSafeExplosionBehavior(ownerId, knockbackOwner),
			getX(),
			getY(),
			getZ(),
			radius,
			false,
			World.ExplosionSourceType.NONE
		);
	}

	private void summonBeesAndRecycle() {
		HeadSounds.play(this, SoundEvents.BLOCK_BEEHIVE_EXIT, 0.6F, 1.15F);
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

	public void releaseSonar() {
		sonarAndRecycle();
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
		if (getWorld() instanceof ServerWorld world) HeadEffects.burst(world, getPos(), getHeadType());
		if (!isInLava() && getY() >= getWorld().getBottomY()) {
			ItemStack stack = getStack().copy();
			if (!stack.isEmpty()) {
				RecycledHeadItemEntity item = new RecycledHeadItemEntity(getWorld(), getX(), getY(), getZ(), stack);
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
		nbt.putDouble("traveled", traveled);
		nbt.putDouble("rollTraveled", rollTraveled);
		nbt.putInt("eatCount", eatCount);
		nbt.putInt("biteCooldown", biteCooldown);
		if (eatBlock != null) {
			nbt.putString("eatBlock", Registries.BLOCK.getId(eatBlock).toString());
		}
		long[] queue = new long[eatQueue.size()];
		int i = 0;
		for (BlockPos pos : eatQueue) {
			queue[i++] = pos.asLong();
		}
		nbt.putLongArray("eatQueue", queue);
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
		traveled = nbt.getDouble("traveled");
		rollTraveled = nbt.getDouble("rollTraveled");
		eatCount = nbt.getInt("eatCount");
		biteCooldown = nbt.getInt("biteCooldown");
		if (nbt.contains("eatBlock")) {
			eatBlock = Registries.BLOCK.get(Identifier.of(nbt.getString("eatBlock")));
		}
		eatQueue.clear();
		for (long packed : nbt.getLongArray("eatQueue")) {
			eatQueue.add(BlockPos.fromLong(packed));
		}
	}
}
