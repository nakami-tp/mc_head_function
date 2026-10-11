package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.entity.ModEntities;
import com.nakami.mcheadfunction.head.HeadEffects;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.rule.HauntRules.HostKind;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** A visible split of a hung head: flies to a host, or wanders through walls if unpaired. */
public final class GhostHeadEntity extends Entity {
	private static final TrackedData<Integer> HEAD = DataTracker.registerData(GhostHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> PHASE = DataTracker.registerData(GhostHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> WANDERING = DataTracker.registerData(GhostHeadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> LEAD = DataTracker.registerData(GhostHeadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	public static final int FLY = 0, SPLIT = 1, HELD = 2, RETURN = 3, WANDER = 4, ENTER = 5, WAIT = 6;
	private BlockPos tree = BlockPos.ORIGIN;
	private int hang;
	private HostKind kind;
	private Vec3d from = Vec3d.ZERO, gather = Vec3d.ZERO, dest = Vec3d.ZERO;
	private BlockPos hostBlock;
	private UUID hostEntity;
	private BlockState original = Blocks.AIR.getDefaultState();
	private int clock;
	private Entity cachedHost;

	public GhostHeadEntity(EntityType<? extends GhostHeadEntity> type, World world) {
		super(type, world);
		noClip = true;
		setNoGravity(true);
	}

	public GhostHeadEntity(ServerWorld world, HauntedTreeBlockEntity tree, int hang, HostKind kind, Vec3d gather, HauntTargets.Target target, boolean lead) {
		this(ModEntities.GHOST_HEAD, world);
		this.tree = tree.getPos();
		this.hang = hang;
		this.kind = kind;
		this.gather = gather;
		this.from = HauntTreeShape.hangWorld(tree.getPos(), hang);
		this.hostBlock = target.block();
		this.hostEntity = target.entity() != null ? target.entity().getUuid() : null;
		this.original = target.original();
		this.dest = target.pos().add(0, 1.35, 0);
		setHead(kind.head);
		dataTracker.set(LEAD, lead);
		if (lead) {
			setPosition(from);
		} else {
			setPosition(gather);
			dataTracker.set(PHASE, WAIT);
		}
		if (target.entity() != null) cachedHost = target.entity();
	}

	/** An unpaired hung head: fly out, then drift through walls until the haunting ends. */
	public GhostHeadEntity(ServerWorld world, HauntedTreeBlockEntity tree, int hang, HeadType head) {
		this(ModEntities.GHOST_HEAD, world);
		this.tree = tree.getPos();
		this.hang = hang;
		this.from = HauntTreeShape.hangWorld(tree.getPos(), hang);
		this.gather = wanderPoint(world);
		this.dest = wanderPoint(world);
		setHead(head);
		dataTracker.set(WANDERING, true);
		dataTracker.set(LEAD, true);
		setPosition(from);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(HEAD, HeadType.ZOMBIE.ordinal());
		builder.add(PHASE, FLY);
		builder.add(WANDERING, false);
		builder.add(LEAD, false);
	}

	public HeadType head() { return HeadType.values()[dataTracker.get(HEAD)]; }
	public void setHead(HeadType type) { dataTracker.set(HEAD, type.ordinal()); }
	public int phase() { return dataTracker.get(PHASE); }
	public ItemStack stack() { return new ItemStack(HeadItems.item(head())); }
	public BlockPos treePos() { return tree; }
	public HostKind kind() { return kind; }
	public boolean wandering() { return dataTracker.get(WANDERING); }
	public boolean lead() { return dataTracker.get(LEAD); }
	public int clock() { return clock; }
	public boolean visibleSplit() {
		int phase = phase();
		if (phase == WAIT) return false;
		return lead() || wandering() || phase != FLY;
	}
	public float displayScale(float delta) {
		int phase = phase();
		if (phase == ENTER) return 1 - enterProgress(delta) * 0.88F;
		if (phase == HELD && clock < HauntRules.RISE_TICKS) {
			return 0.15F + (clock + delta) / HauntRules.RISE_TICKS * 0.85F;
		}
		return 1;
	}
	public float enterProgress(float delta) {
		if (phase() != ENTER) return phase() == HELD || phase() == RETURN ? 1 : 0;
		return Math.min(1, (clock + delta) / HauntRules.ENTER_TICKS);
	}
	public BlockPos hostBlock() { return hostBlock; }
	public Vec3d dest() { return dest; }
	public void setDest(Vec3d dest) { this.dest = dest; }
	public Entity host() {
		if (cachedHost != null && !cachedHost.isRemoved()) return cachedHost;
		if (hostEntity != null && getWorld() instanceof ServerWorld server) cachedHost = server.getEntity(hostEntity);
		return cachedHost;
	}

	public boolean claims(Entity entity, BlockPos pos) {
		if (phase() == RETURN) return false;
		if (entity != null && entity.getUuid().equals(hostEntity)) return true;
		return pos != null && pos.equals(hostBlock);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (data.equals(PHASE) && getWorld().isClient()) clock = 0;
	}

	@Override
	public void tick() {
		super.tick();
		clock++;
		if (getWorld().isClient()) return;
		if (phase() == WAIT) {
			if (clock >= HauntRules.FLY_TICKS) {
				dataTracker.set(PHASE, SPLIT);
				clock = 0;
				from = gather;
			}
		} else if (phase() == FLY) {
			move(from, gather, HauntRules.FLY_TICKS, wandering() ? WANDER : SPLIT);
			if (phase() == WANDER || phase() == SPLIT) from = gather;
		} else if (phase() == SPLIT) {
			move(from, dest, HauntRules.SPLIT_TICKS, ENTER);
			if (phase() == ENTER) from = dest;
		} else if (phase() == ENTER) {
			move(from, sink(), HauntRules.ENTER_TICKS, HELD);
			trailEnter();
			if (phase() == HELD && getWorld() instanceof ServerWorld server) finishEnter(server);
		} else if (phase() == WANDER) {
			move(from, dest, HauntRules.WANDER_TICKS, WANDER);
			if (clock == 0) {
				from = dest;
				dest = wanderPoint(getWorld());
			}
		} else if (phase() == HELD) {
			riseThenFollow();
			if (clock >= HauntRules.RISE_TICKS && getWorld() instanceof ServerWorld server) HauntBehaviors.tick(server, this);
		} else {
			move(from, HauntTreeShape.hangWorld(tree, hang), HauntRules.RETURN_TICKS, -1);
			if (clock >= HauntRules.RETURN_TICKS) discard();
		}
	}

	private void move(Vec3d start, Vec3d end, int duration, int next) {
		double t = Math.min(1, clock / (double) duration);
		t = t * t * (3 - 2 * t);
		Vec3d at = start.lerp(end, t);
		setVelocity(at.subtract(getPos()));
		setPosition(at);
		if (clock >= duration && next >= 0) {
			dataTracker.set(PHASE, next);
			clock = 0;
		}
	}

	private void riseThenFollow() {
		Vec3d above = hostAbove();
		if (clock < HauntRules.RISE_TICKS) {
			double t = clock / (double) HauntRules.RISE_TICKS;
			t = t * t * (3 - 2 * t);
			setPosition(from.lerp(above, t));
		} else {
			setPosition(above);
		}
	}

	private Vec3d hostAbove() {
		Entity host = host();
		if (host != null) return new Vec3d(host.getX(), host.getY() + host.getHeight() + 0.2, host.getZ());
		if (hostBlock != null) return Vec3d.ofCenter(hostBlock).add(0, 1.05, 0);
		return dest;
	}

	private Vec3d sink() {
		Entity host = host();
		if (host != null) return new Vec3d(host.getX(), host.getY() + host.getHeight() * 0.35, host.getZ());
		if (hostBlock != null) return Vec3d.ofCenter(hostBlock);
		return dest.add(0, -1.2, 0);
	}

	private void trailEnter() {
		if (!(getWorld() instanceof ServerWorld server) || clock % 2 != 0) return;
		server.spawnParticles(ParticleTypes.SOUL, getX(), getY(), getZ(), 3, 0.1, 0.1, 0.1, 0.01);
		server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 1, 0.06, 0.06, 0.06, 0);
	}

	private void finishEnter(ServerWorld world) {
		from = getPos();
		attach(world);
		HeadEffects.burst(world, sink(), head());
		world.spawnParticles(ParticleTypes.SOUL, sink().x, sink().y, sink().z, 18, 0.28, 0.2, 0.28, 0.03);
		world.playSound(null, getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.AMBIENT, 0.7F, 0.7F);
	}

	private void attach(ServerWorld world) {
		if (kind == HostKind.MINECART || kind == HostKind.ARMOR_STAND) {
			cachedHost = host();
			return;
		}
		if (hostBlock == null) return;
		if (kind == HostKind.PUMPKIN || kind == HostKind.CARPET || kind == HostKind.LANTERN) {
			if (!HauntTargets.matches(kind, world.getBlockState(hostBlock))) return;
			original = world.getBlockState(hostBlock);
			world.removeBlock(hostBlock, false);
			var prop = new PossessedPropEntity(world, this, original);
			world.spawnEntity(prop);
			hostEntity = prop.getUuid();
			cachedHost = prop;
		} else if (kind == HostKind.FURNACE) {
			HauntBehaviors.lightFurnace(world, this);
		}
	}

	public void releaseAndReturn() {
		if (getWorld() instanceof ServerWorld server) HauntBehaviors.release(server, this);
		from = getPos();
		dataTracker.set(PHASE, RETURN);
		clock = 0;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putLong("tree", tree.asLong());
		nbt.putInt("hang", hang);
		if (kind != null) nbt.putString("kind", kind.name());
		nbt.putString("head", head().name());
		nbt.putInt("phase", phase());
		nbt.putInt("clock", clock);
		nbt.putBoolean("lead", lead());
		nbt.put("from", vec(from));
		nbt.put("gather", vec(gather));
		nbt.put("dest", vec(dest));
		if (hostBlock != null) nbt.putLong("host", hostBlock.asLong());
		if (hostEntity != null) nbt.putUuid("entity", hostEntity);
		nbt.put("original", NbtHelper.fromBlockState(original));
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		tree = BlockPos.fromLong(nbt.getLong("tree"));
		hang = nbt.getInt("hang");
		kind = nbt.contains("kind") ? HostKind.valueOf(nbt.getString("kind")) : null;
		setHead(HeadType.valueOf(nbt.getString("head")));
		dataTracker.set(WANDERING, kind == null);
		dataTracker.set(LEAD, nbt.getBoolean("lead") || kind == null);
		dataTracker.set(PHASE, nbt.getInt("phase"));
		clock = nbt.getInt("clock");
		from = vec(nbt.getCompound("from"));
		gather = vec(nbt.getCompound("gather"));
		dest = vec(nbt.getCompound("dest"));
		if (nbt.contains("host")) hostBlock = BlockPos.fromLong(nbt.getLong("host"));
		if (nbt.containsUuid("entity")) hostEntity = nbt.getUuid("entity");
		if (nbt.contains("original")) original = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt.getCompound("original"));
	}

	private static NbtCompound vec(Vec3d v) {
		var nbt = new NbtCompound();
		nbt.putDouble("x", v.x); nbt.putDouble("y", v.y); nbt.putDouble("z", v.z);
		return nbt;
	}
	private static Vec3d vec(NbtCompound nbt) {
		return new Vec3d(nbt.getDouble("x"), nbt.getDouble("y"), nbt.getDouble("z"));
	}

	public BlockState original() { return original; }
	public void setOriginal(BlockState state) { original = state; }

	private Vec3d wanderPoint(World world) {
		HauntRules.Offset at = HauntRules.wanderOffset(new java.util.Random(world.random.nextLong()));
		return Vec3d.ofCenter(tree).add(at.x(), at.y(), at.z());
	}
}
