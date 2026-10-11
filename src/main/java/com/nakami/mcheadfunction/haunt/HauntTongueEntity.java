package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.entity.ModEntities;
import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.rule.HauntRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** A chest keeps its tongue attached until its prey reaches the mouth. */
public final class HauntTongueEntity extends Entity {
	public static final int EXTEND = 4, HOLD = 4, RETURN = 6, DURATION = EXTEND + HOLD + RETURN;
	public static final int SWALLOW = 16, BITE = 8, CHEW = 16, MAX_PULL = 200;
	private static final TrackedData<Integer> CLOCK = DataTracker.registerData(HauntTongueEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<BlockPos> CHEST = DataTracker.registerData(HauntTongueEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
	private static final TrackedData<Integer> TARGET = DataTracker.registerData(HauntTongueEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> MEAL_START = DataTracker.registerData(HauntTongueEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BLOCK = DataTracker.registerData(HauntTongueEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private Entity target;
	private Vec3d contact = Vec3d.ZERO;
	private Vec3d swallowFrom;
	private Vec3d grabFrom = Vec3d.ZERO;
	private boolean preyNoClip;
	private UUID interruptedPrey, possessionOwner;
	private boolean abortOnLoad, knownChest = true;
	private int biteTime;
	private final List<ItemStack> cargo = new ArrayList<>();

	public HauntTongueEntity(EntityType<? extends HauntTongueEntity> type, World world) {
		super(type, world);
		noClip = true;
	}

	public HauntTongueEntity(ServerWorld world, BlockPos chest, Inventory inventory, Entity target) {
		this(ModEntities.HAUNT_TONGUE, world);
		this.target = target;
		dataTracker.set(CHEST, chest);
		dataTracker.set(TARGET, target.getId());
		contact = target.getBoundingBox().getCenter();
		setPosition(mouth());
	}

	public HauntTongueEntity(ServerWorld world, BlockPos chest, Entity target, GhostHeadEntity ghost) {
		this(world, chest, null, target);
		possessionOwner = ghost.getUuid();
	}

	public boolean belongsTo(GhostHeadEntity ghost) { return ghost.getUuid().equals(possessionOwner); }

	@Override protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(CLOCK, 0);
		builder.add(CHEST, BlockPos.ORIGIN);
		builder.add(TARGET, -1);
		builder.add(MEAL_START, -1);
		builder.add(BLOCK, 0);
	}

	public int elapsed() { return dataTracker.get(CLOCK); }
	public BlockPos chest() { return dataTracker.get(CHEST); }
	public int targetId() { return dataTracker.get(TARGET); }
	public int mealTime() { return dataTracker.get(MEAL_START) < 0 ? -1 : elapsed() - dataTracker.get(MEAL_START); }
	public BlockState carriedBlock() { return Block.getStateFromRawId(dataTracker.get(BLOCK)); }
	public Vec3d mouth() {
		if (!knownChest) return getPos();
		var state = getWorld().getBlockState(chest());
		Vec3d front = state.contains(ChestBlock.FACING) ? Vec3d.of(state.get(ChestBlock.FACING).getVector()).multiply(0.65) : Vec3d.ZERO;
		return Vec3d.ofCenter(chest()).add(0, 0.25, 0).add(front);
	}

	public int deathTicks() { return MathHelper.clamp(mealTime() - BITE + 1, 0, 20); }

	public float preyScale(float delta) {
		if (mealTime() < 0) return 1;
		float meal = mealTime() + delta;
		// Keep the body readable until the jaws snap, then pull the dying pose inside.
		if (meal < BITE) return 1 - 0.25F * MathHelper.clamp(meal / BITE, 0, 1);
		return 0.75F - 0.69F * MathHelper.clamp((meal - BITE) / (SWALLOW - BITE), 0, 1);
	}

	public float lidProgress(float delta) {
		float meal = mealTime() + delta;
		if (mealTime() < 0) return MathHelper.clamp((elapsed() + delta) / EXTEND, 0, 0.85F);
		if (meal < BITE) return 0.95F;
		if (meal < BITE + 2) return MathHelper.lerp((meal - BITE) / 2, 0.95F, 0.12F);
		if (meal < SWALLOW) return MathHelper.lerp(MathHelper.clamp((meal - BITE - 2) / 4, 0, 1), 0.12F, 0.95F);
		float chew = meal - SWALLOW;
		return (0.12F + 0.5F * Math.abs(MathHelper.sin(chew * (float) Math.PI / 5)))
			* MathHelper.clamp((CHEW - chew) / 4, 0, 1);
	}

	public static boolean canPull(LivingEntity living) {
		return living.isAlive() && !living.isRemoved() && !HeadLookups.isBoss(living) && !(living instanceof ArmorStandEntity)
			&& !(living instanceof PlayerEntity player && (player.isCreative() || player.isSpectator()));
	}

	@Override public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) return;
		dataTracker.set(CLOCK, elapsed() + 1);
		if (interruptedPrey != null) {
			target = world.getEntity(interruptedPrey);
			if (target != null || elapsed() >= 20) { finish(); interruptedPrey = null; }
			return;
		}
		if (abortOnLoad || !(world.getBlockState(chest()).getBlock() instanceof ChestBlock)) { finish(); return; }
		if (possessionOwner != null && (!(world.getEntity(possessionOwner) instanceof GhostHeadEntity ghost) || ghost.phase() != GhostHeadEntity.HELD)) { finish(); return; }
		int meal = mealTime();
		if (meal >= 0) {
			if (meal < SWALLOW && target instanceof LivingEntity living && canPull(living)) {
				double t = MathHelper.clamp(meal / (double) SWALLOW, 0, 1);
				Vec3d inside = Vec3d.ofCenter(chest()).add(0, 0.12, 0);
				living.setPosition(swallowFrom.lerp(inside.add(0, -living.getHeight() * preyScale(0) / 2, 0), t * t * (3 - 2 * t)));
				living.setVelocity(Vec3d.ZERO);
				living.velocityModified = true;
				living.fallDistance = 0;
				setPosition(living.getPos().add(0, living.getHeight() * preyScale(0) / 2, 0));
				if (meal == BITE) {
					HauntMeal.bite(living);
					HeadSounds.play(this, SoundEvents.BLOCK_CHEST_CLOSE, 1.1F, 0.65F);
				}
			} else if (meal == SWALLOW && target instanceof LivingEntity living && canPull(living)) {
				setPosition(mouth());
				cargo.addAll(HauntMeal.consume(world, living, this));
				deliver();
				HeadSounds.play(this, SoundEvents.ENTITY_FROG_EAT, 1.2F, 0.65F);
			} else if (meal < SWALLOW) { finish(); return; }
			if (meal >= SWALLOW) setPosition(mouth());
			if (meal >= SWALLOW && meal % 5 == 1) HeadSounds.play(this, SoundEvents.ENTITY_GENERIC_EAT, 0.7F, 0.65F);
			if (meal >= SWALLOW + CHEW) finish();
			return;
		}
		if (!carriedBlock().isAir()) {
			retractBite();
			if (elapsed() >= MAX_PULL) finish();
			return;
		}
		if (!cargo.isEmpty()) {
			double t = MathHelper.clamp((elapsed() - EXTEND - HOLD) / (double) RETURN, 0, 1);
			setPosition(contact.lerp(mouth(), t * t * (3 - 2 * t)));
			if (elapsed() >= DURATION) finish();
			return;
		}
		if (target == null || target.isRemoved() || target instanceof LivingEntity living && !canPull(living)) { finish(); return; }
		contact = target.getBoundingBox().getCenter();
		int time = elapsed();
		var hit = obstacle(world);
		if (hit != null) {
			biteBlock(world, hit);
			if (time >= MAX_PULL) finish();
			return;
		}
		if (time == 1) HeadSounds.play(this, SoundEvents.ENTITY_FROG_TONGUE, 1.2F, 1.1F);
		if (time <= EXTEND) setPosition(mouth().lerp(contact, time / (double) EXTEND));
		else if (time <= EXTEND + HOLD) {
			setPosition(contact);
			if (time == EXTEND + HOLD && target instanceof ItemEntity item) capture(item);
		} else if (target instanceof ItemEntity item) {
			setPosition(contact);
			capture(item);
		} else if (target instanceof LivingEntity living) {
			setPosition(contact);
			Vec3d pull = mouth().subtract(contact);
			if (!(living instanceof PlayerEntity) && pull.length() <= 1.35
				&& world.raycast(new RaycastContext(mouth(), contact, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this)).getType() == HitResult.Type.MISS) {
				swallowFrom = living.getPos();
				preyNoClip = living.noClip;
				living.noClip = true;
				dataTracker.set(MEAL_START, time);
				HeadSounds.play(this, SoundEvents.BLOCK_CHEST_OPEN, 0.8F, 0.65F);
			} else {
				if (pull.length() > 0.6) {
					living.setVelocity(pull.normalize().multiply(Math.min(0.85, pull.length() * 0.22)));
					living.velocityModified = true;
					living.fallDistance = 0;
				}
				if (time >= MAX_PULL) finish();
			}
		}
	}

	private BlockHitResult obstacle(ServerWorld world) {
		var hit = world.raycast(new RaycastContext(mouth(), contact, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
		if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(chest())) return null;
		return hit;
	}

	private void biteBlock(ServerWorld world, BlockHitResult hit) {
		Vec3d stop = hit.getPos();
		biteTime++;
		if (biteTime == 1) HeadSounds.play(this, SoundEvents.ENTITY_FROG_TONGUE, 1.2F, 1.1F);
		if (biteTime <= EXTEND) {
			setPosition(mouth().lerp(stop, biteTime / (double) EXTEND));
			return;
		}
		setPosition(stop);
		if (biteTime < EXTEND + HOLD) return;
		BlockPos pos = hit.getBlockPos();
		BlockState state = world.getBlockState(pos);
		if (!HauntRules.canFrogChestEat(state.getHardness(world, pos) < 0, pos.equals(chest()) || state.isOf(HauntBlocks.TREE), world.getBlockEntity(pos) != null)) {
			biteTime = EXTEND + HOLD;
			return;
		}
		if (!state.isToolRequired()) cargo.addAll(Block.getDroppedStacks(state, world, pos, world.getBlockEntity(pos), this, ItemStack.EMPTY));
		world.removeBlock(pos, false);
		dataTracker.set(BLOCK, Block.getRawIdFromState(state));
		grabFrom = stop;
		biteTime = 0;
		HeadSounds.play(this, SoundEvents.BLOCK_SLIME_BLOCK_HIT, 0.6F, 1.35F);
	}

	private void retractBite() {
		biteTime++;
		double t = MathHelper.clamp(biteTime / (double) RETURN, 0, 1);
		setPosition(grabFrom.lerp(mouth(), t * t * (3 - 2 * t)));
		if (biteTime < RETURN) return;
		deliver();
		dataTracker.set(BLOCK, 0);
		biteTime = 0;
		setPosition(mouth());
		HeadSounds.play(this, SoundEvents.ENTITY_FROG_EAT, 1.0F, 0.85F);
	}

	private void capture(ItemEntity item) {
		cargo.add(item.getStack().copy());
		item.discard();
		target = null;
	}

	private Inventory inventory() {
		if (!knownChest) return null;
		var state = getWorld().getBlockState(chest());
		return state.getBlock() instanceof ChestBlock block ? ChestBlock.getInventory(block, state, getWorld(), chest(), true) : null;
	}

	private void deliver() {
		Inventory inventory = inventory();
		for (ItemStack stack : cargo) {
			if (inventory != null) {
				for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
					ItemStack slot = inventory.getStack(i);
					if (!slot.isEmpty() && !ItemStack.areItemsAndComponentsEqual(slot, stack)) continue;
					int count = Math.min(stack.getCount(), Math.min(stack.getMaxCount(), inventory.getMaxCountPerStack()) - slot.getCount());
					if (count <= 0) continue;
					if (slot.isEmpty()) inventory.setStack(i, stack.split(count));
					else { slot.increment(count); stack.decrement(count); }
				}
				inventory.markDirty();
			}
			if (!stack.isEmpty()) {
				Vec3d at = mouth();
				var item = new ItemEntity(getWorld(), at.x, at.y, at.z, stack.copy());
				item.setToDefaultPickupDelay();
				getWorld().spawnEntity(item);
			}
		}
		cargo.clear();
	}

	public void finish() {
		// If interrupted mid-swallow, release the living prey at the front, never inside a solid chest.
		if (target instanceof LivingEntity living && !living.isRemoved()
			&& (swallowFrom != null && mealTime() < SWALLOW || interruptedPrey != null)) {
			living.noClip = preyNoClip;
			var state = getWorld().getBlockState(chest());
			Vec3d front = state.contains(ChestBlock.FACING) ? Vec3d.of(state.get(ChestBlock.FACING).getVector()) : new Vec3d(0, 0, 1);
			living.setPosition(Vec3d.ofBottomCenter(chest()).add(front.multiply(1 + living.getWidth() / 2)));
		}
		deliver();
		if (getWorld() instanceof ServerWorld world && world.getBlockState(chest()).getBlock() instanceof ChestBlock block)
			world.addSyncedBlockEvent(chest(), block, 1, 0);
		discard();
	}

	@Override protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putLong("Chest", chest().asLong());
		if (interruptedPrey != null || mealTime() >= 0 && mealTime() < SWALLOW && target != null) {
			nbt.putUuid("InterruptedPrey", interruptedPrey != null ? interruptedPrey : target.getUuid());
			nbt.putBoolean("PreyNoClip", preyNoClip);
		}
		var items = new NbtList();
		for (ItemStack stack : cargo) items.add(stack.encode(getRegistryManager()));
		nbt.put("Items", items);
	}

	@Override protected void readCustomDataFromNbt(NbtCompound nbt) {
		abortOnLoad = true;
		knownChest = nbt.contains("Chest");
		dataTracker.set(CHEST, BlockPos.fromLong(nbt.getLong("Chest")));
		if (nbt.containsUuid("InterruptedPrey")) {
			interruptedPrey = nbt.getUuid("InterruptedPrey");
			preyNoClip = nbt.getBoolean("PreyNoClip");
			dataTracker.set(MEAL_START, 0);
		}
		for (var item : nbt.getList("Items", 10)) ItemStack.fromNbt(getRegistryManager(), item).ifPresent(cargo::add);
		if (nbt.contains("Cargo")) ItemStack.fromNbt(getRegistryManager(), nbt.getCompound("Cargo")).ifPresent(cargo::add);
	}
}
