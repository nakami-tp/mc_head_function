package com.nakami.mcheadfunction.entity;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.progression.HeadMastery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** A visible, timed grab. Loot belongs to the tongue until it reaches its owner. */
public final class FrogTongueEntity extends Entity {
	public static final int EXTEND = 2, HOLD = 4, RETURN = 4, DURATION = EXTEND + HOLD + RETURN;
	private static final TrackedData<Integer> OWNER = DataTracker.registerData(FrogTongueEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> CLOCK = DataTracker.registerData(FrogTongueEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BLOCK = DataTracker.registerData(FrogTongueEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<ItemStack> ITEM = DataTracker.registerData(FrogTongueEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
	private ServerPlayerEntity owner;
	private Entity target;
	private BlockPos blockPos;
	private BlockState originalBlock;
	private Vec3d contact = Vec3d.ZERO;
	private final List<ItemStack> cargo = new ArrayList<>();
	private boolean restored;

	public FrogTongueEntity(EntityType<? extends FrogTongueEntity> type, World world) { super(type, world); noClip = true; }
	public FrogTongueEntity(ServerPlayerEntity player, HitResult hit) {
		this(ModEntities.FROG_TONGUE, player.getWorld());
		owner = player;
		dataTracker.set(OWNER, player.getId());
		contact = hit.getPos();
		if (hit instanceof EntityHitResult e) target = e.getEntity();
		if (hit instanceof BlockHitResult b) {
			blockPos = b.getBlockPos(); originalBlock = getWorld().getBlockState(blockPos);
		}
		setPosition(mouth(player, 1));
	}
	@Override protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(OWNER, -1); builder.add(CLOCK, 0); builder.add(BLOCK, 0); builder.add(ITEM, ItemStack.EMPTY);
	}
	public Entity owner() { return getWorld().getEntityById(dataTracker.get(OWNER)); }
	public int elapsed() { return dataTracker.get(CLOCK); }
	public BlockState carriedBlock() { return Block.getStateFromRawId(dataTracker.get(BLOCK)); }
	public ItemStack carriedItem() { return dataTracker.get(ITEM); }
	public static Vec3d mouth(Entity player, float delta) {
		return player.getLerpedPos(delta).add(0, player.getStandingEyeHeight() - 0.28, 0)
			.add(player.getRotationVec(delta).multiply(0.45));
	}
	@Override public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) return;
		// An interrupted/reloaded grab releases its saved loot instead of replaying block removal.
		if (restored || owner == null || owner.isRemoved() || !owner.isAlive() || owner.getWorld() != world
			|| HeadLookups.worn(owner) != HeadType.FROG || owner.squaredDistanceTo(this) > 32 * 32) {
			release(); return;
		}
		int time = elapsed() + 1;
		dataTracker.set(CLOCK, time);
		if (target != null && !target.isRemoved()) contact = target.getBoundingBox().getCenter();
		Vec3d mouth = mouth(owner, 1);
		if (time <= EXTEND) setPosition(mouth.lerp(contact, time / (double) EXTEND));
		else if (time <= EXTEND + HOLD) setPosition(contact);
		else {
			double progress = MathHelper.clamp((time - EXTEND - HOLD) / (double) RETURN, 0, 1);
			setPosition(contact.lerp(mouth, progress * progress * (3 - 2 * progress)));
			if (target instanceof LivingEntity living && !living.isRemoved() && !HeadLookups.isBoss(living)) {
				Vec3d pull = mouth.subtract(living.getBoundingBox().getCenter());
				if (pull.length() > 1.3) {
					// Bound the pull velocity even when the animation window is short.
					living.setVelocity(pull.normalize().multiply(Math.min(1.19, pull.length() * 0.252)));
					living.velocityModified = true;
				}
				setPosition(living.getBoundingBox().getCenter());
			}
		}
		if (time == EXTEND) HeadSounds.play(this, SoundEvents.BLOCK_SLIME_BLOCK_HIT, 0.6F, 1.35F);
		if (time == EXTEND + HOLD) {
			latch(world);
			HeadSounds.play(owner, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.45F, 0.85F);
		}
		if (time >= DURATION) {
			for (ItemStack stack : cargo) if (!owner.getInventory().insertStack(stack)) owner.dropItem(stack, false);
			cargo.clear();
			HeadSounds.play(owner, SoundEvents.ENTITY_FROG_EAT, 0.45F, 1.2F);
			discard();
		}
	}
	private void latch(ServerWorld world) {
		if (blockPos != null && world.getBlockState(blockPos).equals(originalBlock)
			&& !originalBlock.isAir() && originalBlock.getHardness(world, blockPos) >= 0
			&& world.canPlayerModifyAt(owner, blockPos) && owner.canModifyBlocks()) {
			var blockEntity = world.getBlockEntity(blockPos);
			// Empty containers before computing drops, including shulker-box contents.
			if (blockEntity instanceof Inventory inventory) {
				HeadMastery.challenge(owner, "frog_unpack");
				for (int i = 0; i < inventory.size(); i++) cargo.add(inventory.removeStack(i));
			}
			if (!originalBlock.isToolRequired()) cargo.addAll(Block.getDroppedStacks(originalBlock, world, blockPos, blockEntity, owner, ItemStack.EMPTY));
			world.removeBlock(blockPos, false);
			dataTracker.set(BLOCK, Block.getRawIdFromState(originalBlock));
			HeadMastery.practice(owner, HeadType.FROG);
		} else if (target instanceof ItemEntity item && !item.isRemoved()) {
			cargo.add(item.getStack().copy()); dataTracker.set(ITEM, item.getStack().copy()); item.discard();
			HeadMastery.practice(owner, HeadType.FROG);
		} else if (target instanceof LivingEntity living && living.isAlive() && !HeadLookups.isBoss(living)) {
			HeadMastery.practice(owner, HeadType.FROG);
		}
	}
	private void release() { for (ItemStack stack : cargo) dropStack(stack); cargo.clear(); discard(); }
	@Override protected void writeCustomDataToNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		for (ItemStack stack : cargo) if (!stack.isEmpty()) list.add(stack.encode(getRegistryManager()));
		nbt.put("Cargo", list);
	}
	@Override protected void readCustomDataFromNbt(NbtCompound nbt) {
		cargo.clear();
		for (var entry : nbt.getList("Cargo", 10)) ItemStack.fromNbt(getRegistryManager(), entry).ifPresent(cargo::add);
		restored = true;
	}
}
