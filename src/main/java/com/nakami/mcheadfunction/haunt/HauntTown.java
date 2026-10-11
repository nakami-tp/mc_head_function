package com.nakami.mcheadfunction.haunt;

import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.RailShape;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/** Rebuildable 64 × 64 filming set: a lived-in harvest village around the haunted tree. */
public final class HauntTown {
	private HauntTown() { }

	public static void build(ServerWorld world, BlockPos center) {
		clear(world, center);
		new Village(world, center).build();
		HauntWorldData.get(world).setTown(center.toImmutable());
	}

	public static void reset(ServerWorld world) {
		BlockPos center = HauntWorldData.get(world).town();
		if (center != null) build(world, center);
	}

	public static void clear(ServerWorld world, BlockPos center) {
		// Stop before replacing blocks so possessed props cannot restore themselves over the new set.
		if (world.getBlockEntity(center) instanceof HauntedTreeBlockEntity tree) tree.stop();
		int r = HauntWorldData.TOWN / 2;
		var box = new Box(center.getX() - r, center.getY() - 2, center.getZ() - r, center.getX() + r, center.getY() + 17, center.getZ() + r);
		BlockPos.stream(center.add(-r, -2, -r), center.add(r - 1, 16, r - 1))
			.forEach(pos -> world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS));
		for (var entity : world.getOtherEntities(null, box, e -> !(e instanceof net.minecraft.entity.player.PlayerEntity))) entity.discard();
	}

	/** Local coordinates: facade at z=0 facing north, interior towards +z. Rotate the whole state and position together. */
	private record Plot(ServerWorld world, BlockPos origin, BlockRotation rotation) {
		BlockPos at(int x, int y, int z) { return origin.add(new BlockPos(x, y, z).rotate(rotation)); }
		void put(int x, int y, int z, Block block) { put(x, y, z, block.getDefaultState()); }
		void put(int x, int y, int z, BlockState state) { world.setBlockState(at(x, y, z), state.rotate(rotation), Block.NOTIFY_LISTENERS | Block.FORCE_STATE); }
		void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
			for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) put(x, y, z, block);
		}
	}

	private enum Home {
		BAKERY("南瓜面包铺", Blocks.WHITE_TERRACOTTA, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB),
		INN("弯月酒馆", Blocks.SMOOTH_SANDSTONE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB),
		HERBALIST("夜灯草药屋", Blocks.PACKED_MUD, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB),
		COTTAGE("丰收小屋", Blocks.WHITE_TERRACOTTA, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB),
		TAILOR("乌鸦裁缝铺", Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB),
		WORKSHOP("铜铃工坊", Blocks.STONE_BRICKS, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB);
		final String sign;
		final Block wall, roof, ridge;
		Home(String sign, Block wall, Block roof, Block ridge) { this.sign = sign; this.wall = wall; this.roof = roof; this.ridge = ridge; }
	}

	private static final class Village {
		private final ServerWorld world;
		private final BlockPos center;
		private final Plot town;
		Village(ServerWorld world, BlockPos center) {
			this.world = world;
			this.center = center;
			this.town = new Plot(world, center, BlockRotation.NONE);
		}

		void build() {
			ground();
			house(-20, -14, Direction.SOUTH, 11, 11, Home.HERBALIST);
			house(-1, -16, Direction.SOUTH, 13, 11, Home.INN);
			house(20, -14, Direction.SOUTH, 9, 11, Home.BAKERY);
			house(-20, 14, Direction.NORTH, 11, 9, Home.COTTAGE);
			house(20, 14, Direction.NORTH, 11, 9, Home.TAILOR);
			house(16, 0, Direction.WEST, 11, 11, Home.WORKSHOP);
			square();
			market(-9, 14, Blocks.ORANGE_WOOL);
			market(5, 15, Blocks.PURPLE_WOOL);
			garden(-27, 3);
			freightYard();
			boundary();
			for (int[] p : new int[][] {{-28,-27},{10,-27},{28,-11},{-28,27},{16,28},{28,27},{-28,-8}}) autumnTree(p[0], p[1]);
			world.setBlockState(center, HauntBlocks.TREE.getDefaultState(), Block.NOTIFY_LISTENERS);
			HauntBlocks.TREE.onPlaced(world, center, world.getBlockState(center), null, ItemStack.EMPTY);
			// Bulk placement avoids physics destroying half-built doors, beds and hanging lanterns.
			// Resolve pane/fence/wall/stair connections after every neighbor exists.
			for (var pos : BlockPos.iterate(center.add(-32, 0, -32), center.add(31, 15, 31))) {
				var state = world.getBlockState(pos);
				if (state.getBlock() instanceof LeavesBlock) world.scheduleBlockTick(pos, state.getBlock(), 1);
				if (state.getBlock() instanceof PaneBlock || state.getBlock() instanceof FenceBlock
					|| state.getBlock() instanceof WallBlock || state.getBlock() instanceof StairsBlock)
					world.setBlockState(pos, Block.postProcessState(state, world, pos), Block.NOTIFY_LISTENERS);
			}
		}

		private void ground() {
			for (int x = -32; x < 32; x++) for (int z = -32; z < 32; z++) {
				town.put(x, -2, z, Blocks.DIRT);
				int noise = Math.floorMod(x * 7349 + z * 9151 + x * z * 31, 23);
				boolean plaza = Math.abs(x) <= 10 && Math.abs(z) <= 10 && Math.abs(x) + Math.abs(z) <= 17;
				boolean road = Math.abs(x) <= 2 || Math.abs(z) <= 2
					|| (Math.abs(z) >= 11 && Math.abs(z) <= 12 && Math.abs(x) <= 26);
				Block ground = noise < 3 ? Blocks.COARSE_DIRT : noise == 5 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
				if (plaza) ground = noise < 3 ? Blocks.CRACKED_STONE_BRICKS : noise < 7 ? Blocks.ANDESITE : Blocks.STONE_BRICKS;
				else if (road) ground = noise < 5 ? Blocks.GRAVEL : noise < 11 ? Blocks.ANDESITE : Blocks.COBBLESTONE;
				town.put(x, -1, z, ground);
			}
			// A narrow contrasting border makes the plaza read as a designed public space.
			for (int i = -7; i <= 7; i++) for (int side : new int[] {-1, 1}) {
				if (Math.abs(i) > 2) {
					town.put(i, -1, side * 10, Blocks.POLISHED_ANDESITE);
					town.put(side * 10, -1, i, Blocks.POLISHED_ANDESITE);
				}
			}
		}

		private Plot plot(int x, int z, Direction face, int width) {
			BlockRotation rotation = switch (face) {
				case SOUTH -> BlockRotation.CLOCKWISE_180;
				case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
				case EAST -> BlockRotation.CLOCKWISE_90;
				default -> BlockRotation.NONE;
			};
			return new Plot(world, center.add(x, 0, z).offset(face.rotateYClockwise(), -width / 2), rotation);
		}

		private void house(int x, int z, Direction face, int w, int d, Home home) {
			Plot p = plot(x, z, face, w);
			int h = home == Home.INN ? 7 : 4;
			p.fill(0, -1, 0, w - 1, -1, d - 1, Blocks.STONE_BRICKS);
			p.fill(1, -1, 1, w - 2, -1, d - 2, Blocks.SPRUCE_PLANKS);
			for (int y = 0; y <= h; y++) {
				p.fill(0, y, 0, w - 1, y, 0, home.wall);
				p.fill(0, y, d - 1, w - 1, y, d - 1, home.wall);
				p.fill(0, y, 0, 0, y, d - 1, home.wall);
				p.fill(w - 1, y, 0, w - 1, y, d - 1, home.wall);
			}
			for (int post : new int[] {0, w / 2, w - 1}) {
				p.fill(post, 0, 0, post, h, 0, Blocks.STRIPPED_DARK_OAK_LOG);
				p.fill(post, 0, d - 1, post, h, d - 1, Blocks.STRIPPED_DARK_OAK_LOG);
			}
			for (int side : new int[] {0, w - 1}) p.fill(side, 0, d / 2, side, h, d / 2, Blocks.STRIPPED_DARK_OAK_LOG);
			for (int level : new int[] {h, 3}) {
				p.fill(0, level, 0, w - 1, level, 0, Blocks.DARK_OAK_PLANKS);
				p.fill(0, level, d - 1, w - 1, level, d - 1, Blocks.DARK_OAK_PLANKS);
			}
			// Solid gables, deep eaves, steep stair roof and capped ridge; the inn rises above the cottages.
			for (int rx = -1; rx <= w; rx++) {
				int rise = Math.min(rx + 1, w - rx);
				int roofY = h + 1 + rise;
				if (rx >= 0 && rx < w) for (int y = h + 1; y < roofY; y++) {
					p.put(rx, y, 0, rx == w / 2 ? Blocks.DARK_OAK_LOG : home.wall);
					p.put(rx, y, d - 1, rx == w / 2 ? Blocks.DARK_OAK_LOG : home.wall);
				}
				for (int rz = -1; rz <= d; rz++) {
					if (rx == w / 2) p.put(rx, roofY, rz, home.ridge.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
					else p.put(rx, roofY, rz, home.roof.getDefaultState().with(StairsBlock.FACING, rx < w / 2 ? Direction.EAST : Direction.WEST));
				}
			}
			p.fill(1, h, 1, w - 2, h, d - 2, Blocks.SPRUCE_PLANKS);
			window(p, 2, 0, 1);
			window(p, w - 3, 0, 1);
			for (int side : new int[] {0, w - 1}) for (int wz : new int[] {3, d - 3}) {
				p.put(side, 1, wz, Blocks.GLASS_PANE);
				p.put(side, 2, wz, Blocks.GLASS_PANE);
			}
			p.fill(2, h + 1, 0, w - 3, h + 1, 0, Blocks.DARK_OAK_PLANKS);
			for (int beam : new int[] {w / 2 - 2, w / 2 + 2}) p.fill(beam, h + 2, 0, beam, h + 3, 0, Blocks.STRIPPED_DARK_OAK_LOG);
			p.put(w / 2, h + 2, 0, Blocks.OCHRE_FROGLIGHT);
			p.put(w / 2, h + 2, -1, Blocks.DARK_OAK_FENCE);
			if (home == Home.INN) {
				p.fill(1, 3, 1, w - 2, 3, d - 2, Blocks.SPRUCE_PLANKS);
				window(p, 2, 0, 5); window(p, w - 3, 0, 5);
				// Interior stair along the side wall to the guest room.
				for (int i = 0; i < 4; i++) {
					p.fill(1, 0, 2 + i, 1, i, 2 + i, Blocks.SPRUCE_PLANKS);
					p.put(1, i, 2 + i, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
					p.put(1, 3, 2 + i, Blocks.AIR);
				}
				p.put(1, 3, 5, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
				bed(p, 3, 4, d - 3); bed(p, w - 4, 4, d - 3);
			}
			// Full-height real doors, a flush threshold and a three-wide connection to the street.
			for (int dy = 0; dy < 2; dy++) p.put(w / 2, dy, 0, Blocks.SPRUCE_DOOR.getDefaultState()
				.with(DoorBlock.FACING, Direction.NORTH).with(DoorBlock.HALF, dy == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
			p.fill(w / 2 - 1, -1, -4, w / 2 + 1, -1, -1, Blocks.COBBLESTONE);
			for (int px = w / 2 - 2; px <= w / 2 + 2; px++) for (int pz = -2; pz <= -1; pz++) p.put(px, 3, pz, Blocks.SPRUCE_SLAB);
			for (int px : new int[] {w / 2 - 2, w / 2 + 2}) p.fill(px, 0, -2, px, 2, -2, Blocks.SPRUCE_FENCE);
			p.put(w / 2 - 1, 2, -1, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
			p.put(w / 2 + 2, 0, -1, Blocks.JACK_O_LANTERN.getDefaultState().with(CarvedPumpkinBlock.FACING, Direction.NORTH));
			sign(p, w / 2 + 2, 2, -3, home.sign);
			p.put(1, 0, -1, Blocks.BARREL);
			p.put(1, 1, -1, Blocks.POTTED_DEAD_BUSH);
			p.put(w - 2, 0, -1, Blocks.HAY_BLOCK);
			p.put(w - 2, 1, -1, Blocks.CARVED_PUMPKIN);
			chimney(p, w - 3, d - 3, h + 5);
			furnish(p, w, d, home);
		}

		private void window(Plot p, int x, int z, int y) {
			p.put(x, y, z, Blocks.GLASS_PANE); p.put(x, y + 1, z, Blocks.GLASS_PANE);
			for (int dx : new int[] {-1, 1}) p.put(x + dx, y, z - 1, Blocks.SPRUCE_TRAPDOOR.getDefaultState()
				.with(TrapdoorBlock.FACING, Direction.NORTH).with(TrapdoorBlock.OPEN, true));
			p.put(x, y - 1, z - 1, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
			p.put(x, y, z - 1, Blocks.POTTED_ORANGE_TULIP);
		}

		private void chimney(Plot p, int x, int z, int top) {
			p.fill(x, 0, z, x, top, z, Blocks.BRICKS);
			p.put(x, 0, z - 1, Blocks.SMOKER.getDefaultState().with(AbstractFurnaceBlock.FACING, Direction.NORTH));
			p.put(x, top + 1, z, Blocks.CAMPFIRE);
			p.put(x, top + 2, z, Blocks.COBBLESTONE_WALL);
			p.put(x, top + 3, z, Blocks.LIGHTNING_ROD);
		}

		private void furnish(Plot p, int w, int d, Home home) {
			p.put(w - 2, 0, d - 2, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH));
			p.put(2, 0, d - 2, Blocks.BARREL);
			p.put(2, 1, d - 2, Blocks.LANTERN);
			p.put(3, 0, 3, Blocks.SPRUCE_FENCE);
			p.put(3, 1, 3, Blocks.SPRUCE_PRESSURE_PLATE);
			p.put(3, 0, 2, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
			p.put(3, 0, 4, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
			for (int rz = 2; rz <= 5; rz++) p.put(w / 2, 0, rz, (rz % 2 == 0 ? Blocks.ORANGE_CARPET : Blocks.BROWN_CARPET));
			p.put(w - 2, 0, 2, Blocks.CRAFTING_TABLE);
			p.put(w - 2, 1, 2, Blocks.POTTED_FERN);
			switch (home) {
				case INN -> {
					p.fill(4, 0, d - 4, w - 4, 0, d - 4, Blocks.DARK_OAK_PLANKS);
					p.put(5, 1, d - 4, Blocks.LANTERN);
					p.put(7, 1, d - 4, Blocks.FLOWER_POT);
					p.put(5, 1, d - 2, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.NORTH));
					p.put(7, 1, d - 2, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.NORTH));
					p.fill(4, 0, d - 2, 7, 0, d - 2, Blocks.BARREL);
				}
				case BAKERY -> {
					p.fill(2, 0, d - 4, 4, 0, d - 4, Blocks.SMOOTH_STONE);
					p.put(3, 1, d - 4, Blocks.CAKE);
					p.put(2, 1, d - 4, Blocks.FLOWER_POT);
					p.put(1, 0, d - 3, Blocks.FURNACE);
				}
				case HERBALIST -> {
					p.fill(1, 0, d - 4, 1, 2, d - 2, Blocks.BOOKSHELF);
					p.put(2, 0, d - 4, Blocks.BREWING_STAND);
					p.put(3, 0, d - 4, Blocks.CAULDRON);
					bed(p, w - 3, 0, 4);
				}
				case COTTAGE -> { bed(p, w - 3, 0, 4); p.put(1, 0, d - 3, Blocks.BOOKSHELF); }
				case TAILOR -> {
					p.put(1, 0, d - 3, Blocks.LOOM);
					p.fill(1, 0, 5, 1, 0, 7, Blocks.PURPLE_WOOL);
					stand(p, w - 3, 0, 4); stand(p, w - 3, 0, 6);
				}
				case WORKSHOP -> {
					p.put(2, 0, d - 4, Blocks.ANVIL);
					p.put(1, 0, d - 3, Blocks.BLAST_FURNACE);
					p.put(1, 0, d - 5, Blocks.SMITHING_TABLE);
					stand(p, w - 3, 0, 3);
				}
			}
		}

		private void bed(Plot p, int x, int y, int z) {
			p.put(x, y, z, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.FOOT));
			p.put(x, y, z + 1, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.HEAD));
		}

		private void square() {
			// Low octagonal tree surround, leaving four clear routes through the square.
			for (int i = -2; i <= 2; i++) for (int side : new int[] {-1, 1}) {
				town.put(i, -1, side * 3, Blocks.MOSSY_STONE_BRICKS);
				town.put(side * 3, -1, i, Blocks.MOSSY_STONE_BRICKS);
			}
			for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) town.put(x, -1, z, Blocks.COARSE_DIRT);
			for (int x : new int[] {-8, 8}) for (int z : new int[] {-8, 8}) lamp(x, z);
			for (int x : new int[] {-7, 5}) {
				for (int dx = 0; dx < 3; dx++) town.put(x + dx, 0, 8, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
				town.put(x - 1, 0, 8, Blocks.JACK_O_LANTERN);
			}
			for (int x : new int[] {-12, 12}) for (int z : new int[] {-6, 6}) {
				town.put(x, 0, z, Blocks.STONE_BRICKS);
				town.put(x, 1, z, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST));
				town.put(x, 2, z, Blocks.CARVED_PUMPKIN.getDefaultState().with(CarvedPumpkinBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST));
			}
			// Festival bunting on the north and south approach, clear of the tree canopy.
			for (int z : new int[] {-11, 11}) {
				for (int x : new int[] {-7, 7}) town.fill(x, 0, z, x, 5, z, Blocks.DARK_OAK_FENCE);
				for (int x = -6; x <= 6; x++) {
					town.put(x, 5, z, Blocks.DARK_OAK_FENCE);
					if (x % 2 == 0) town.put(x, 4, z, (x % 4 == 0 ? Blocks.ORANGE_WOOL : Blocks.PURPLE_WOOL));
				}
				town.put(-5, 4, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
				town.put(5, 4, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
			}
		}

		private void lamp(int x, int z) {
			town.put(x, 0, z, Blocks.STONE_BRICK_WALL);
			town.fill(x, 1, z, x, 4, z, Blocks.DARK_OAK_FENCE);
			town.put(x + 1, 4, z, Blocks.DARK_OAK_FENCE);
			town.put(x + 1, 3, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
			town.put(x, 0, z + 1, Blocks.JACK_O_LANTERN);
		}

		private void market(int x, int z, Block cloth) {
			Plot p = new Plot(world, center.add(x, 0, z), BlockRotation.NONE);
			p.fill(0, -1, 0, 5, -1, 5, Blocks.COBBLESTONE);
			for (int px : new int[] {0, 5}) for (int pz : new int[] {0, 4}) p.fill(px, 0, pz, px, 3, pz, Blocks.SPRUCE_FENCE);
			for (int px = 0; px <= 5; px++) for (int pz = -1; pz <= 4; pz++) p.put(px, 4, pz, px % 2 == 0 ? cloth : Blocks.WHITE_WOOL);
			p.fill(1, 0, 0, 4, 0, 0, Blocks.BARREL);
			p.put(1, 1, 0, Blocks.CARVED_PUMPKIN); p.put(3, 1, 0, Blocks.CAKE);
			p.put(4, 1, 0, Blocks.LANTERN);
			p.put(1, 0, 3, Blocks.CHEST);
			p.put(4, 0, 3, Blocks.HAY_BLOCK);
			p.put(4, 1, 3, Blocks.PUMPKIN);
			p.put(2, 0, 2, Blocks.ORANGE_CARPET); p.put(3, 0, 2, Blocks.PURPLE_CARPET);
			stand(p, 5, 0, 6);
		}

		private void garden(int x, int z) {
			Plot p = new Plot(world, center.add(x, 0, z), BlockRotation.NONE);
			for (int px = 0; px <= 9; px++) for (int pz = 0; pz <= 6; pz++) {
				if (px == 0 || px == 9 || pz == 0 || pz == 6) {
					if (pz != 3) p.put(px, 0, pz, Blocks.SPRUCE_FENCE);
				} else if (px == 4) p.put(px, -1, pz, Blocks.WATER);
				else {
					p.put(px, -1, pz, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
					p.put(px, 0, pz, Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 7));
				}
			}
			p.put(7, -1, 2, Blocks.COARSE_DIRT); p.put(7, -1, 4, Blocks.COARSE_DIRT); p.put(6, -1, 5, Blocks.COARSE_DIRT);
			p.put(7, 0, 2, Blocks.PUMPKIN); p.put(7, 0, 4, Blocks.PUMPKIN);
			p.put(6, 0, 5, Blocks.HAY_BLOCK); p.put(6, 1, 5, Blocks.CARVED_PUMPKIN);
			stand(p, 2, 0, 3);
			p.put(9, 1, 0, Blocks.LANTERN);
		}

		private void freightYard() {
			// Four parked carts on individual sidings, not a bare line of entities in a yard.
			for (int x : new int[] {-10, -6, 6, 10}) {
				for (int z = 23; z <= 28; z++) {
					town.put(x, -1, z, Blocks.GRAVEL);
					town.put(x, 0, z, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
				}
				town.put(x, 0, 29, Blocks.SPRUCE_PLANKS);
				var cart = new MinecartEntity(world, center.getX() + x + 0.5, center.getY() + 0.1, center.getZ() + 26.5);
				world.spawnEntity(cart);
			}
			town.fill(-4, 0, 26, -3, 0, 28, Blocks.HAY_BLOCK);
			town.put(4, 0, 26, Blocks.CHEST); town.put(4, 0, 28, Blocks.BARREL);
			lamp(-12, 27); lamp(12, 27);
		}

		private void boundary() {
			for (int i = -30; i <= 30; i++) {
				if (Math.abs(i) < 4) continue;
				for (int side : new int[] {-30, 30}) {
					town.put(i, 0, side, i % 5 == 0 ? Blocks.MOSSY_STONE_BRICK_WALL.getDefaultState() : Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
					town.put(side, 0, i, i % 5 == 0 ? Blocks.MOSSY_STONE_BRICK_WALL.getDefaultState() : Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
				}
			}
			for (int x : new int[] {-4, 4}) { lamp(x, 30); lamp(x, -30); }
		}

		private void autumnTree(int x, int z) {
			town.fill(x, 0, z, x, 4, z, Blocks.OAK_LOG);
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 3; y <= 6; y++) {
				if (Math.abs(dx) + Math.abs(dz) + Math.abs(y - 4) > 4 || (dx == 0 && dz == 0 && y < 5)) continue;
				var leaf = (dx + dz + y) % 4 == 0 ? Blocks.ORANGE_TERRACOTTA.getDefaultState() : Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
				if (world.getBlockState(center.add(x + dx, y, z + dz)).isAir()) town.put(x + dx, y, z + dz, leaf);
			}
			town.put(x + 1, 0, z, Blocks.PUMPKIN);
			town.put(x - 1, 0, z + 1, Blocks.DEAD_BUSH);
		}

		private void sign(Plot p, int x, int y, int z, String text) {
			p.put(x, y, z, Blocks.SPRUCE_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.NORTH));
			if (world.getBlockEntity(p.at(x, y, z)) instanceof SignBlockEntity sign)
				sign.setText(sign.getFrontText().withMessage(1, Text.literal(text)).withGlowing(true), true);
		}

		private void stand(Plot p, int x, int y, int z) {
			var at = p.at(x, y, z);
			var stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
			stand.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.rotation.rotate(Direction.NORTH).asRotation(), 0);
			stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
			stand.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
			world.spawnEntity(stand);
		}
	}
}
