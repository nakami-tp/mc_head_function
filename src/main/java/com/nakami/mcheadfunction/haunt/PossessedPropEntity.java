package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.entity.ModEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** A pumpkin, carpet or lantern that left its block so it can move. */
public final class PossessedPropEntity extends Entity {
	private static final TrackedData<Integer> BLOCK = DataTracker.registerData(PossessedPropEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> LIGHT = DataTracker.registerData(PossessedPropEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public PossessedPropEntity(EntityType<? extends PossessedPropEntity> type, World world) {
		super(type, world);
	}

	public PossessedPropEntity(World world, GhostHeadEntity ghost, BlockState state) {
		this(ModEntities.POSSESSED_PROP, world);
		setBlock(state);
		Vec3d at = Vec3d.ofCenter(ghost.hostBlock());
		setPosition(at.x, ghost.hostBlock().getY(), at.z);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(BLOCK, Block.getRawIdFromState(Blocks.CARVED_PUMPKIN.getDefaultState()));
		builder.add(LIGHT, 15);
	}

	public BlockState block() { return Block.getStateFromRawId(dataTracker.get(BLOCK)); }
	public void setBlock(BlockState state) { dataTracker.set(BLOCK, Block.getRawIdFromState(state)); }
	public int light() { return dataTracker.get(LIGHT); }
	public void setLight(int light) { dataTracker.set(LIGHT, light); }

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient()) {
			if (noClip) {
				setPosition(getX() + getVelocity().x, getY() + getVelocity().y, getZ() + getVelocity().z);
			} else {
				setVelocity(getVelocity().add(0, -0.08, 0));
				move(net.minecraft.entity.MovementType.SELF, getVelocity());
				setVelocity(getVelocity().multiply(0.91, 0.98, 0.91));
			}
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Block", NbtHelper.fromBlockState(block()));
		nbt.putInt("Light", light());
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Block")) setBlock(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt.getCompound("Block")));
		setLight(nbt.getInt("Light"));
	}
}
