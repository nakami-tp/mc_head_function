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
import com.nakami.mcheadfunction.progression.HeadMastery;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
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

	private static final TrackedData<Integer> BITE_TICKS = DataTracker.registerData(ThrownHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private HeadType.ThrowStyle style = HeadType.ThrowStyle.DEFAULT;
	private int activeTicks;
	private double traveled;
	private double rollTraveled;
	private int rollTicks;
	private Vec3d rollDirection = new Vec3d(0, 0, 1);
	private static final TrackedData<Float> ROLL_DISTANCE = DataTracker.registerData(ThrownHeadEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private float previousRollDistance;
	private double rollSpeed = 0.2;
	private double rollDropDistance;
	private boolean rolledOnGround;
	private int impactCooldown;
	public static final float GOLEM_SCALE = 8;
	public static final float GOLEM_SIZE = 5F;
	public static final double GOLEM_SPEED = 0.20;

	public static final double GOLEM_RANGE = 40;
	public float getRollDistance(float delta) {
		return net.minecraft.util.math.MathHelper.lerp(delta, previousRollDistance, dataTracker.get(ROLL_DISTANCE));
	}

	public boolean isGiantRoller() { return isActive() && getHeadType() == HeadType.IRON_GOLEM; }

	@Override public net.minecraft.entity.EntityDimensions getDimensions(net.minecraft.entity.EntityPose pose) {
		return isGiantRoller() ? net.minecraft.entity.EntityDimensions.fixed(GOLEM_SIZE, GOLEM_SIZE) : super.getDimensions(pose);
	}

	@Override public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (ACTIVE.equals(data)) calculateDimensions();
	}
	private Block eatBlock;
	private final ArrayDeque<BlockPos> eatQueue = new ArrayDeque<>();
	private int eatCount;
	private int biteCooldown;
	private boolean recycled;
	private boolean practiced;

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
		builder.add(BITE_TICKS, 0);
		builder.add(ROLL_DISTANCE, 0F);
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

	public int getBiteTicks() { return dataTracker.get(BITE_TICKS); }

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
		previousRollDistance = dataTracker.get(ROLL_DISTANCE);
		if (getWorld().isClient() && age % 2 == 0) {
			getWorld().addParticle(HeadEffects.particle(getHeadType()), getX(), getY() + 0.2, getZ(), 0, 0.015, 0);
		}
		if (!getWorld().isClient() && !isActive() && style == HeadType.ThrowStyle.GOLEM_ROLL) enterActive();
		if (isActive()) {
			// ThrownEntity.tick uses setPosition, which bypasses solid-block collision.
			baseTick();
			if (!getWorld().isClient()) {
				if (isInLava() || getY() < getWorld().getBottomY() - 16) {
					discard();
					return;
				}
				tickActive();
			}
			if (isRemoved()) return;
			if (getHeadType() != HeadType.ENDERMAN) {
				applyGravity();
				Vec3d beforeMove = getPos();
				boolean wasOnGround = isOnGround();
				move(MovementType.SELF, getVelocity());
				if (isGiantRoller() && !getWorld().isClient()) finishRollMove(beforeMove, wasOnGround);
				double drag = getHeadType() == HeadType.ZOMBIE && isOnGround() ? 0.6 : 0.99;
				setVelocity(getVelocity().multiply(drag, 0.98, drag));
			}
			return;
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

	private void practice() {
		if (practiced || style == HeadType.ThrowStyle.KNOCK_OFF) return;
		practiced = true;
		if (getOwner() instanceof ServerPlayerEntity player) HeadMastery.practice(player, getHeadType());
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
			practice();
		}
	}

	private void damagePierceTargets(ServerWorld world, Vec3d point) {
		Box box = new Box(point, point).expand(0.45);
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, this::canHarm)) {
			if (living.damage(world.getDamageSources().thrown(this, getOwner()), HeadRules.PIERCE_DAMAGE)) practice();
		}
	}

	private void blazeTrailTick() {
		ServerWorld world = (ServerWorld) getWorld();
		Vec3d start = getPos();
		Vec3d delta = getVelocity();
		double length = Math.min(delta.length(), Math.max(0, 16 - traveled));
		Vec3d end = start.add(delta.normalize().multiply(length));
		var hit = world.raycast(new net.minecraft.world.RaycastContext(start, end,
			net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
			net.minecraft.world.RaycastContext.FluidHandling.ANY, this));
		end = hit.getPos();
		if (getOwner() instanceof ServerPlayerEntity owner) {
			com.nakami.mcheadfunction.blaze.BlazeFire.trail(world, owner, start, end);
		}
		traveled += start.distanceTo(end);
		if (hit.getType() != HitResult.Type.MISS || traveled >= 15.999) {
			setPosition(end);
			recycle();
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		if (recycled || isActive()) return;
		if (getWorld().isClient()) {
			// Stop client prediction at the surface while awaiting the server's impact state.
			if (hitResult instanceof BlockHitResult blockHit && getHeadType() != HeadType.CHARGED_CREEPER) {
				placeAtImpact(blockHit);
				setVelocity(Vec3d.ZERO);
			}
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
		placeAtImpact(hitResult);
		HeadSounds.impact(this, getHeadType());
		if (hitResult.getType() == HitResult.Type.ENTITY) {
			onEntityHit((EntityHitResult) hitResult);
		} else if (hitResult.getType() == HitResult.Type.BLOCK) {
			onBlockHit((BlockHitResult) hitResult);
		}
	}

	private void placeAtImpact(HitResult hit) {
		Vec3d contact = hit.getPos();
		if (hit instanceof BlockHitResult blockHit) {
			Direction side = blockHit.getSide();
			double offset = side == Direction.UP ? 0.001 : side == Direction.DOWN ? getHeight() + 0.001 : getWidth() / 2 + 0.001;
			contact = contact.add(Vec3d.of(side.getVector()).multiply(offset));
		}
		setPosition(contact);
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		if (!(getWorld() instanceof ServerWorld world) || recycled) {
			return;
		}
		Entity target = hit.getEntity();
		if (!ownedBy(target)) practice();
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
		practice();
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
		Vec3d launch = getVelocity().multiply(1, 0, 1);
		setActive(true);
		setVelocity(Vec3d.ZERO);
		setNoGravity(false);
		activeTicks = 0;
		if (style == HeadType.ThrowStyle.GOLEM_ROLL) {
			if (launch.lengthSquared() < 0.001 && getOwner() != null) launch = getOwner().getRotationVec(1).multiply(1, 0, 1);
			rollDirection = launch.lengthSquared() < 0.001 ? new Vec3d(0, 0, 1) : launch.normalize();
			setYaw((float) Math.toDegrees(Math.atan2(-rollDirection.x, rollDirection.z)));
			setVelocity(rollDirection.multiply(GOLEM_SPEED));
			HeadSounds.play(this, SoundEvents.BLOCK_ANVIL_LAND, 1.5F, 0.5F);
		}
	}

	private void tickActive() {
		activeTicks++;
		if (getBiteTicks() > 0) dataTracker.set(BITE_TICKS, getBiteTicks() - 1);
		if (style != HeadType.ThrowStyle.GOLEM_ROLL && activeTicks >= HeadRules.ACTIVE_MAX_TICKS) {
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
		Vec3d horizontal = to.multiply(1, 0, 1);
		if (horizontal.lengthSquared() > 0.01) {
			setYaw((float) Math.toDegrees(Math.atan2(-horizontal.x, horizontal.z)));
		}
		if (isOnGround() && activeTicks % 4 == 0) {
			double speed = Math.min(0.24, horizontal.length() / 10);
			setVelocity(horizontal.normalize().multiply(speed).add(0, 0.24, 0));
			velocityDirty = true;
			HeadSounds.play(this, SoundEvents.BLOCK_WOOL_FALL, 0.18F, 0.7F);
		}
		if (biteCooldown > 0) {
			biteCooldown--;
		}
		if (getBoundingBox().expand(0.35).intersects(target.getBoundingBox()) && biteCooldown <= 0
			&& world.raycast(new net.minecraft.world.RaycastContext(getPos().add(0, 0.25, 0), target.getPos().add(0, 0.25, 0),
				net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, this)).getType() == HitResult.Type.MISS
			&& target.damage(world.getDamageSources().mobProjectile(this, ownerAsLiving()), HeadRules.ZOMBIE_BITE_DAMAGE)) {
			HeadEffects.burst(world, target.getPos().add(0, 0.5, 0), HeadType.ZOMBIE);
			HeadSounds.play(this, SoundEvents.ENTITY_GENERIC_EAT, 0.9F, 0.7F);
			dataTracker.set(BITE_TICKS, 6);
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
			if (!world.breakBlock(pos, true, this)) continue;
			setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			HeadSounds.play(this, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.65F, 1.15F);
			HeadSounds.play(this, SoundEvents.ENTITY_GENERIC_EAT, 0.65F, 0.85F);
			world.spawnParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.05);
			eatCount++;
			if (eatCount >= HeadRules.ENDERMAN_EAT_MAX) {
				if (getOwner() instanceof ServerPlayerEntity player) HeadMastery.challenge(player, "enderman_vein");
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

	private void tickRoll() {
		if (!(getWorld() instanceof ServerWorld world)) return;
		if (impactCooldown > 0) impactCooldown--;
		if (isOnGround()) rollSpeed = Math.max(GOLEM_SPEED, rollSpeed - 0.001);
		double step = Math.min(rollSpeed, GOLEM_RANGE - rollTraveled);
		setVelocity(rollDirection.multiply(step).add(0, getVelocity().y, 0));
		Box sweep = getBoundingBox().stretch(rollDirection.multiply(step));
		// Leave the supporting surface intact; crush the volume above the roller's feet.
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(sweep.minX, sweep.minY + 0.05, sweep.minZ),
			BlockPos.ofFloored(sweep.maxX, sweep.maxY, sweep.maxZ))) {
			BlockState state = world.getBlockState(pos);
			var shape = state.getCollisionShape(world, pos);
			boolean support = !shape.isEmpty() && pos.getY() + shape.getMax(Direction.Axis.Y) <= getY() + 0.001;
			if (!support && !state.isAir() && !(state.getBlock() instanceof net.minecraft.block.FluidBlock) && state.getHardness(world, pos) >= 0) {
				if (world.breakBlock(pos, true, this)) practice();
			}
		}
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, sweep, this::canHarm)) {
			((com.nakami.mcheadfunction.head.HeadCrushAccess) living).mhf$crush(com.nakami.mcheadfunction.head.HeadCrushAccess.DURATION_TICKS);
			living.setVelocity(living.getVelocity().multiply(0.15, 1, 0.15));
			if (activeTicks % 10 == 1 && living.damage(world.getDamageSources().thrown(this, getOwner()), 4)) {
				practice();
				// No launch impulse: the target stays beneath the rolling mass.
				living.setVelocity(0, Math.min(0, living.getVelocity().y), 0);
				living.velocityModified = true;
				world.spawnParticles(ParticleTypes.CRIT, living.getX(), living.getY() + 0.2, living.getZ(), 10, 0.5, 0.1, 0.5, 0.03);
				HeadSounds.play(this, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 0.8F, 0.5F);
			}
		}
		if (activeTicks % 4 == 0) {
			world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.getDefaultState()),
				getX(), getY() + 0.15, getZ(), 18, 2.6, 0.12, 2.6, 0.06);
			world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.1, getZ(), 8, 2.5, 0.1, 2.5, 0.01);
		}
		if (activeTicks % 12 == 0) {
			HeadSounds.play(this, SoundEvents.BLOCK_GRINDSTONE_USE, 1.0F, 0.5F);
			HeadSounds.play(this, SoundEvents.ENTITY_IRON_GOLEM_STEP, 1.5F, 0.5F);
		}
	}

	private void finishRollMove(Vec3d before, boolean wasOnGround) {
		double moved = getPos().subtract(before).horizontalLength();
		// Visual rotation follows all movement, but flight spends no rolling lifetime or range.
		dataTracker.set(ROLL_DISTANCE, dataTracker.get(ROLL_DISTANCE) + (float) moved);
		if (wasOnGround && isOnGround() && moved > 0.000001) {
			rollTraveled += moved;
			rollTicks++;
		}
		double drop = Math.max(0, before.y - getY());
		rollDropDistance += drop;
		// Convert descent into rolling momentum; retain it briefly on the next flat section.
		if (rolledOnGround && drop > 0 && rollDropDistance < 4) {
			rollSpeed = Math.min(0.4, Math.sqrt(rollSpeed * rollSpeed + 0.05 * drop));
		}
		if (isOnGround()) {
			if (rollDropDistance >= 4 && impactCooldown == 0) {
				com.nakami.mcheadfunction.entity.GolemImpact.land(this, (ServerWorld) getWorld(), rollDropDistance);
				impactCooldown = 40;
			}
			rolledOnGround = true;
			rollDropDistance = 0;
		}
		if (rollTraveled >= GOLEM_RANGE - 0.001 || rollTicks >= 600 || horizontalCollision) recycle();
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
			getHeadType() == HeadType.CREEPER ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE
		);
	}

	private void summonBeesAndRecycle() {
		HeadSounds.play(this, SoundEvents.BLOCK_BEEHIVE_EXIT, 0.6F, 1.15F);
		if (getWorld() instanceof ServerWorld world) {
			UUID ownerId = getOwner() != null ? getOwner().getUuid() : null;
			int count = getOwner() instanceof ServerPlayerEntity player && HeadMastery.mastered(player, HeadType.BEE) ? 4 : HeadRules.BEE_SUMMON_COUNT;
			for (int i = 0; i < count; i++) {
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
			practice();
			ServerPlayNetworking.send(player, new SonarS2CPayload(HeadMastery.mastered(player, HeadType.BAT) ? 200 : 100));
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
				practice();
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
		nbt.putBoolean("practiced", practiced);
		nbt.putInt("activeTicks", activeTicks);
		nbt.putDouble("traveled", traveled);
		nbt.putDouble("rollTraveled", rollTraveled);
		nbt.putInt("rollTicks", rollTicks);
		nbt.putFloat("rollVisualDistance", dataTracker.get(ROLL_DISTANCE));
		nbt.putDouble("rollX", rollDirection.x);
		nbt.putDouble("rollZ", rollDirection.z);
		nbt.putDouble("rollSpeed", rollSpeed);
		nbt.putDouble("rollDropDistance", rollDropDistance);
		nbt.putBoolean("rolledOnGround", rolledOnGround);
		nbt.putInt("impactCooldown", impactCooldown);
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
		practiced = nbt.getBoolean("practiced");
		activeTicks = nbt.getInt("activeTicks");
		traveled = nbt.getDouble("traveled");
		rollTraveled = nbt.getDouble("rollTraveled");
		rollTicks = nbt.getInt("rollTicks");
		float visualDistance = nbt.contains("rollVisualDistance") ? nbt.getFloat("rollVisualDistance") : (float) rollTraveled;
		dataTracker.set(ROLL_DISTANCE, visualDistance);
		previousRollDistance = visualDistance;
		rollSpeed = nbt.contains("rollSpeed") ? Math.max(GOLEM_SPEED, Math.min(0.4, nbt.getDouble("rollSpeed"))) : GOLEM_SPEED;
		rollDropDistance = nbt.getDouble("rollDropDistance");
		rolledOnGround = nbt.getBoolean("rolledOnGround");
		impactCooldown = nbt.getInt("impactCooldown");
		if (nbt.contains("rollX")) rollDirection = new Vec3d(nbt.getDouble("rollX"), 0, nbt.getDouble("rollZ")).normalize();
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
