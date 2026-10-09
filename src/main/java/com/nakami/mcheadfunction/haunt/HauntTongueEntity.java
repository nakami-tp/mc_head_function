package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.entity.ModEntities;
import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadSounds;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Chest-owned grab used while a frog head is possessing a chest. */
public final class HauntTongueEntity extends Entity {
	public static final int EXTEND = 4, HOLD = 4, RETURN = 6, DURATION = EXTEND + HOLD + RETURN;
	private static final TrackedData<Integer> CLOCK = DataTracker.registerData(HauntTongueEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<BlockPos> CHEST = DataTracker.registerData(HauntTongueEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
	private Entity target;
	private Vec3d contact = Vec3d.ZERO;
	private ItemStack cargo = ItemStack.EMPTY;

	public HauntTongueEntity(EntityType<? extends HauntTongueEntity> type, World world) {
		super(type, world);
		noClip = true;
	}

	public HauntTongueEntity(ServerWorld world, BlockPos chest, Inventory inventory, Entity target) {
		this(ModEntities.HAUNT_TONGUE, world);
		this.target = target;
		dataTracker.set(CHEST, chest);
		contact = target.getBoundingBox().getCenter();
		setPosition(mouth());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(CLOCK, 0);
		builder.add(CHEST, BlockPos.ORIGIN);
	}

	public int elapsed() { return dataTracker.get(CLOCK); }
	public BlockPos chest() { return dataTracker.get(CHEST); }
	public Vec3d mouth() { return Vec3d.ofCenter(chest()).add(0, 0.45, 0); }

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) return;
		int time = elapsed() + 1;
		dataTracker.set(CLOCK, time);
		if (target != null && !target.isRemoved()) contact = target.getBoundingBox().getCenter();
		Vec3d mouth = mouth();
		if (time <= EXTEND) setPosition(mouth.lerp(contact, time / (double) EXTEND));
		else if (time <= EXTEND + HOLD) {
			setPosition(contact);
			if (time == EXTEND + HOLD) latch(world);
		} else {
			double t = MathHelper.clamp((time - EXTEND - HOLD) / (double) RETURN, 0, 1);
			setPosition(contact.lerp(mouth, t * t * (3 - 2 * t)));
			if (target instanceof LivingEntity living && !living.isRemoved() && !HeadLookups.isBoss(living)) {
				Vec3d pull = mouth.subtract(living.getBoundingBox().getCenter());
				if (pull.length() > 1.2) {
					living.setVelocity(pull.normalize().multiply(Math.min(1.1, pull.length() * 0.25)));
					living.velocityModified = true;
				}
			}
		}
		if (time == 1) HeadSounds.play(this, SoundEvents.ENTITY_FROG_TONGUE, 1.2F, 1.1F);
		if (time >= DURATION) {
			if (!cargo.isEmpty() && world.getBlockEntity(chest()) instanceof Inventory inventory) {
				for (int i = 0; i < inventory.size() && !cargo.isEmpty(); i++) {
					if (inventory.getStack(i).isEmpty()) { inventory.setStack(i, cargo); cargo = ItemStack.EMPTY; }
				}
				if (!cargo.isEmpty()) dropStack(cargo);
			}
			discard();
		}
	}

	private void latch(ServerWorld world) {
		if (target instanceof ItemEntity item && !item.isRemoved()) {
			cargo = item.getStack().copy();
			item.discard();
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (!cargo.isEmpty()) nbt.put("Cargo", cargo.encode(getRegistryManager()));
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Cargo")) ItemStack.fromNbt(getRegistryManager(), nbt.getCompound("Cargo")).ifPresent(stack -> cargo = stack);
	}
}
