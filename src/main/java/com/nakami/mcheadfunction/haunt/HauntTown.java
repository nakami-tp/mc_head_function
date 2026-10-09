package com.nakami.mcheadfunction.haunt;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/** Fixed-seed haunted town used to film the finale. */
public final class HauntTown {
	private HauntTown() { }

	public static void build(ServerWorld world, BlockPos center) {
		clear(world, center);
		int r = HauntWorldData.TOWN / 2;
		for (int x = -r; x < r; x++) for (int z = -r; z < r; z++) {
			BlockPos ground = center.add(x, -1, z);
			world.setBlockState(ground, (Math.abs(x) < 8 && Math.abs(z) < 8 ? Blocks.STONE_BRICKS : Blocks.COARSE_DIRT).getDefaultState());
			if ((x + z) % 17 == 0) world.setBlockState(center.add(x, 0, z), Blocks.DEAD_BUSH.getDefaultState());
		}
		world.setBlockState(center, HauntBlocks.TREE.getDefaultState());
		HauntBlocks.TREE.onPlaced(world, center, world.getBlockState(center), null, net.minecraft.item.ItemStack.EMPTY);
		house(world, center.add(-18, 0, -14), Direction.EAST);
		house(world, center.add(10, 0, -16), Direction.WEST);
		house(world, center.add(-16, 0, 12), Direction.SOUTH);
		house(world, center.add(12, 0, 10), Direction.NORTH);
		yard(world, center.add(0, 0, 18));
		yard(world, center.add(0, 0, -22));
		HauntWorldData.get(world).setTown(center.toImmutable());
	}

	public static void reset(ServerWorld world) {
		BlockPos center = HauntWorldData.get(world).town();
		if (center == null) return;
		if (world.getBlockEntity(center) instanceof HauntedTreeBlockEntity tree) tree.stop();
		clear(world, center);
		build(world, center);
	}

	public static void clear(ServerWorld world, BlockPos center) {
		int r = HauntWorldData.TOWN / 2;
		BlockPos.stream(center.add(-r, -2, -r), center.add(r - 1, 16, r - 1)).forEach(pos -> world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2));
		var box = new Box(center).expand(r, 12, r);
		for (var entity : world.getOtherEntities(null, box, e -> !(e instanceof net.minecraft.entity.player.PlayerEntity))) entity.discard();
	}

	private static void house(ServerWorld world, BlockPos origin, Direction face) {
		for (int x = 0; x < 9; x++) for (int z = 0; z < 7; z++) for (int y = 0; y < 5; y++) {
			boolean wall = x == 0 || x == 8 || z == 0 || z == 6 || y == 4;
			BlockState block = y == 4 ? Blocks.DARK_OAK_SLAB.getDefaultState()
				: wall ? Blocks.DARK_OAK_PLANKS.getDefaultState() : Blocks.AIR.getDefaultState();
			world.setBlockState(origin.add(x, y, z), block);
		}
		world.setBlockState(origin.add(4, 0, face == Direction.NORTH ? 0 : face == Direction.SOUTH ? 6 : 3), Blocks.AIR.getDefaultState());
		world.setBlockState(origin.add(1, 1, 1), Blocks.CHEST.getDefaultState());
		world.setBlockState(origin.add(2, 1, 1), Blocks.TRAPPED_CHEST.getDefaultState());
		world.setBlockState(origin.add(1, 1, 3), Blocks.FURNACE.getDefaultState());
		world.setBlockState(origin.add(3, 1, 5), Blocks.DISPENSER.getDefaultState());
		world.setBlockState(origin.add(5, 1, 5), Blocks.DISPENSER.getDefaultState());
		world.setBlockState(origin.add(7, 2, 1), Blocks.LANTERN.getDefaultState());
		world.setBlockState(origin.add(7, 2, 5), Blocks.SOUL_LANTERN.getDefaultState());
		world.setBlockState(origin.add(4, 0, 4), Blocks.RED_CARPET.getDefaultState());
		world.setBlockState(origin.add(5, 0, 4), Blocks.BLACK_CARPET.getDefaultState());
		world.setBlockState(origin.add(6, 3, 3), Blocks.COBWEB.getDefaultState());
		stand(world, origin.add(6, 1, 2));
	}

	private static void yard(ServerWorld world, BlockPos origin) {
		for (int i = 0; i < 5; i++) world.setBlockState(origin.add(i * 2, 0, 0), Blocks.CARVED_PUMPKIN.getDefaultState());
		for (int i = 0; i < 4; i++) world.setBlockState(origin.add(i * 2, 0, 3), Blocks.JACK_O_LANTERN.getDefaultState());
		for (int i = 0; i < 3; i++) {
			world.setBlockState(origin.add(-3, 0, i * 2), Blocks.LIGHTNING_ROD.getDefaultState());
			var cart = new MinecartEntity(world, origin.getX() + i * 3 + 0.5, origin.getY(), origin.getZ() + 6.5);
			world.spawnEntity(cart);
		}
		world.setBlockState(origin.add(8, 0, 5), Blocks.BLAST_FURNACE.getDefaultState());
		world.setBlockState(origin.add(10, 0, 5), Blocks.SMOKER.getDefaultState());
		world.setBlockState(origin.add(2, 0, 5), Blocks.ORANGE_CARPET.getDefaultState());
		world.setBlockState(origin.add(3, 0, 5), Blocks.PURPLE_CARPET.getDefaultState());
		stand(world, origin.add(12, 1, 2));
		stand(world, origin.add(13, 1, 2));
	}

	private static void stand(ServerWorld world, BlockPos pos) {
		var stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
		stand.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		world.spawnEntity(stand);
	}
}
