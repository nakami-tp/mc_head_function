package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.entity.ModEntities;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.rule.HauntRules.HostKind;
import java.util.UUID;
import net.minecraft.block.Block;
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
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** A visible split of a hung head: flies to a host, or wanders through walls if unpaired. */
public final class GhostHeadEntity extends Entity {
	private static final TrackedData<Integer> HEAD = DataTracker.registerData(GhostHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> PHASE = DataTracker.registerData(GhostHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> WANDERING = DataTracker.registerData(GhostHeadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	public static final int FLY = 0, SPLIT = 1, HELD = 2, RETURN = 3, WANDER = 4;
	private static final int FLY_TICKS = 20, SPLIT_TICKS = 15, RETURN_TICKS = 20, WANDER_TICKS = 80;
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

	public GhostHeadEntity(ServerWorld world, HauntedTreeBlockEntity tree, int hang, HostKind kind, Vec3d gather, HauntTargets.Target target) {
		this(ModEntities.GHOST_HEAD, world);
		this.tree = tree.getPos();
		this.hang = hang;
		this.kind = kind;
		this.gather = gather;
		this.from = HauntTreeShape.hangWorld(tree.getPos(), hang);
		this.hostBlock = target.block();
		this.hostEntity = target.entity() != null ? target.entity().getUuid() : null;
		this.original = target.original();
		this.dest = target.pos().add(0, 1.1, 0);
		setHead(kind.head);
		setPosition(from);
		if (target.entity() != null) {
			dataTracker.set(PHASE, HELD);
			cachedHost = target.entity();
		}
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
		setPosition(from);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(HEAD, HeadType.ZOMBIE.ordinal());
		builder.add(PHASE, FLY);
		builder.add(WANDERING, false);
	}

	public HeadType head() { return HeadType.values()[dataTracker.get(HEAD)]; }
	public void setHead(HeadType type) { dataTracker.set(HEAD, type.ordinal()); }
	public int phase() { return dataTracker.get(PHASE); }
	public ItemStack stack() { return new ItemStack(HeadItems.item(head())); }
	public BlockPos treePos() { return tree; }
	public HostKind kind() { return kind; }
	public boolean wandering() { return dataTracker.get(WANDERING); }
	public BlockPos hostBlock() { return hostBlock; }
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
	public void tick() {
		super.tick();
		clock++;
		if (phase() == FLY) {
			move(from, gather, FLY_TICKS, wandering() ? WANDER : SPLIT);
			if (phase() == WANDER) {
				from = gather;
			}
		} else if (phase() == SPLIT) {
			move(gather, dest, SPLIT_TICKS, HELD);
			if (phase() == HELD && getWorld() instanceof ServerWorld server) attach(server);
		} else if (phase() == WANDER) {
			move(from, dest, WANDER_TICKS, WANDER);
			if (clock == 0) {
				from = dest;
				dest = wanderPoint(getWorld());
			}
		} else if (phase() == HELD) {
			followHost();
			if (getWorld() instanceof ServerWorld server) HauntBehaviors.tick(server, this);
		} else {
			move(from, HauntTreeShape.hangWorld(tree, hang), RETURN_TICKS, -1);
			if (clock >= RETURN_TICKS) discard();
		}
	}

	private void move(Vec3d start, Vec3d end, int duration, int next) {
		double t = Math.min(1, clock / (double) duration);
		t = t * t * (3 - 2 * t);
		setPosition(start.lerp(end, t));
		if (clock >= duration && next >= 0) {
			dataTracker.set(PHASE, next);
			clock = 0;
		}
	}

	private void followHost() {
		Entity host = host();
		if (host != null) setPosition(host.getX(), host.getY() + host.getHeight() + 0.15, host.getZ());
		else if (hostBlock != null) setPosition(Vec3d.ofCenter(hostBlock).add(0, 1.05, 0));
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
