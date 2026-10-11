package com.nakami.mcheadfunction.haunt;

import java.util.Random;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;

/** Fixed landscape outside the original streets: all coordinates are relative to the tree. */
final class HauntTownOutskirts {
	private final HauntTown.Plot p;

	HauntTownOutskirts(HauntTown.Plot plot) { p = plot; }

	void build() {
		terrain();
		pondAndBridge();
		gate();
		orchard();
		pasture();
		fields();
		graveyard();
		forest();
		for (int z : new int[] {39, 49}) for (int x : new int[] {-4, 4}) roadsideLamp(x, z);
	}

	private static int elevation(int x, int z) {
		// Low hills frame the far corners, tapering to flat paths, plots and the outer edge.
		if (Math.abs(x) < 35 || Math.abs(z) < 46 || x > 0 && z > 0) return 0;
		double hill = 1 - Math.pow((Math.abs(x) - 44) / 11.0, 2) - Math.pow((Math.abs(z) - 49) / 6.0, 2);
		return Math.max(0, (int) Math.ceil(hill * 3));
	}

	private void terrain() {
		int r = HauntWorldData.TOWN / 2;
		for (int x = -r; x < r; x++) for (int z = -r; z < r; z++) {
			if (Math.abs(x) < 32 && Math.abs(z) < 32) continue;
			int h = elevation(x, z);
			p.fill(x, -4, z, x, h - 2, z, Blocks.DIRT);
			boolean road = Math.abs(x) <= 2 || Math.abs(z) <= 2;
			int noise = Math.floorMod(x * 117 + z * 211 + x * z, 29);
			p.put(x, h - 1, z, road ? (noise < 4 && x != 0 ? Blocks.GRAVEL : Blocks.COBBLESTONE)
				: noise < 2 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK);
			if (!road && noise == 4) p.put(x, h, z, Blocks.SHORT_GRASS);
		}
	}

	private void gate() {
		for (int x : new int[] {-6, 5}) {
			p.fill(x, 0, 31, x + 1, 1, 33, Blocks.STONE_BRICKS);
			p.fill(x, 2, 31, x + 1, 5, 33, Blocks.STRIPPED_DARK_OAK_LOG);
			p.put(x, 0, 34, Blocks.STONE_BRICKS);
			p.put(x, 1, 34, Blocks.DARK_OAK_FENCE);
			p.put(x, 2, 34, Blocks.JACK_O_LANTERN.getDefaultState().with(CarvedPumpkinBlock.FACING, Direction.SOUTH));
		}
		for (int x = -6; x <= 6; x++) for (int z = 31; z <= 33; z++)
			p.put(x, 5, z, Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
		for (int x = -7; x <= 7; x++) for (int z = 30; z <= 34; z++) {
			int rise = Math.min(z - 30, 34 - z);
			p.put(x, 6 + rise, z, z == 32 ? Blocks.DEEPSLATE_TILE_SLAB.getDefaultState()
				: Blocks.DEEPSLATE_TILE_STAIRS.getDefaultState().with(StairsBlock.FACING, z < 32 ? Direction.SOUTH : Direction.NORTH));
		}
		for (int x : new int[] {-3, 3}) p.put(x, 4, 32, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
		p.put(0, 5, 34, Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.SOUTH));
		if (p.world().getBlockEntity(p.at(0, 5, 34)) instanceof SignBlockEntity sign) {
			sign.setText(sign.getFrontText().withMessage(0, Text.literal("弯月小镇"))
				.withMessage(2, Text.literal("丰收夜庆典")).withGlowing(true), true);
		}
		for (int side : new int[] {-1, 1}) {
			for (int x = 7; x <= 12; x++) {
				p.put(x * side, 0, 32, Blocks.MOSSY_STONE_BRICK_WALL);
				p.put(x * side, 1, 32, Blocks.SPRUCE_FENCE);
			}
			p.fill(side * 8, 0, 35, side * 8, 1, 35, Blocks.HAY_BLOCK);
			p.put(side * 8, 2, 35, Blocks.CARVED_PUMPKIN.getDefaultState().with(CarvedPumpkinBlock.FACING, Direction.SOUTH));
			p.put(side * 10, 0, 35, Blocks.PUMPKIN);
		}
	}

	private void pondAndBridge() {
		// A shallow irregular pond with a continuous solid bed, not water draining into the world below.
		for (int x = -52; x <= -34; x++) for (int z = -14; z <= 14; z++) {
			double radius = Math.pow((x + 43) / 8.5, 2) + Math.pow(z / 13.0, 2);
			if (radius > 1.15) continue;
			p.put(x, -3, z, Blocks.CLAY);
			p.put(x, -2, z, radius < 0.8 ? Blocks.WATER : Blocks.GRAVEL);
			p.put(x, -1, z, radius < 0.9 ? Blocks.WATER : Blocks.GRAVEL);
			p.put(x, 0, z, Blocks.AIR);
			if (radius < 0.7 && Math.abs(z) > 4 && Math.floorMod(x * 11 + z * 13, 19) == 0) p.put(x, 0, z, Blocks.LILY_PAD);
		}
		// Flush deck keeps a walk from the outer road to the square at the same foot height.
		for (int x = -53; x <= -33; x++) {
			for (int z = -2; z <= 2; z++) p.put(x, -1, z, Blocks.SPRUCE_PLANKS);
			for (int z : new int[] {-3, 3}) {
				p.put(x, -1, z, Blocks.DARK_OAK_LOG);
				p.put(x, 0, z, Blocks.SPRUCE_FENCE);
				if ((x + 53) % 5 == 0) {
					p.fill(x, -2, z, x, 1, z, Blocks.STRIPPED_SPRUCE_LOG);
					p.put(x, 2, z, Blocks.LANTERN);
				}
			}
		}
		for (int x : new int[] {-45, -43, -41}) {
			p.put(x, -1, 12, Blocks.SAND);
			p.put(x, 0, 12, Blocks.SUGAR_CANE);
			p.put(x, 1, 12, Blocks.SUGAR_CANE);
		}
	}

	private void orchard() {
		for (int x : new int[] {-26, -19, -12}) for (int z : new int[] {38, 46}) tree(x, z, 0, 4);
		for (int x = -28; x <= -10; x++) p.put(x, -1, 42, Blocks.DIRT_PATH);
		p.put(-12, 0, 43, Blocks.BARREL);
		p.put(-13, 0, 44, Blocks.HAY_BLOCK);
		p.put(-13, 1, 44, Blocks.PUMPKIN);
		p.put(-11, 0, 43, Blocks.COMPOSTER);
		roadsideLamp(-9, 38);
	}

	private void pasture() {
		pen(13, 36, 29, 49);
		for (int z = 37; z <= 48; z++) p.put(21, 0, z, Blocks.SPRUCE_FENCE);
		for (int x : new int[] {17, 25}) p.put(x, 0, 36, Blocks.SPRUCE_FENCE_GATE);
		// Open-front animal shelters with hay at the back and no blocks at the spawn points.
		for (int x : new int[] {14, 23}) {
			p.fill(x, 0, 47, x, 2, 48, Blocks.SPRUCE_LOG);
			p.fill(x + 4, 0, 47, x + 4, 2, 48, Blocks.SPRUCE_LOG);
			p.fill(x, 0, 49, x + 4, 2, 49, Blocks.SPRUCE_PLANKS);
			for (int xx = x; xx <= x + 4; xx++) for (int z = 46; z <= 49; z++) p.put(xx, 3, z, Blocks.SPRUCE_SLAB);
			p.put(x + 1, 0, 48, Blocks.HAY_BLOCK);
			p.put(x + 2, 2, 48, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
		}
		pen(35, 36, 46, 46);
		p.fill(38, 0, 43, 43, 2, 45, Blocks.SPRUCE_PLANKS);
		p.fill(39, 0, 43, 42, 1, 44, Blocks.AIR);
		for (int x = 37; x <= 44; x++) for (int z = 42; z <= 46; z++) p.put(x, 3, z, Blocks.BRICK_SLAB);
		p.put(40, 0, 45, Blocks.HAY_BLOCK);
		p.put(41, 2, 43, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
		p.put(35, 0, 40, Blocks.SPRUCE_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, Direction.EAST));
	}

	private void pen(int x1, int z1, int x2, int z2) {
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
			p.put(x, 0, z, Blocks.AIR);
			p.put(x, -1, z, Blocks.GRASS_BLOCK);
			if (x == x1 || x == x2 || z == z1 || z == z2) p.put(x, 0, z, Blocks.SPRUCE_FENCE);
		}
	}

	private void fields() {
		for (int x = 36; x <= 48; x++) for (int z = -23; z <= -7; z++) {
			p.put(x, 0, z, Blocks.AIR);
			if (x == 36 || x == 48 || z == -23 || z == -7) {
				p.put(x, -1, z, Blocks.COARSE_DIRT);
				if (z != -15) p.put(x, 0, z, Blocks.SPRUCE_FENCE);
			} else if (x == 40 || x == 45) p.put(x, -1, z, Blocks.WATER);
			else if (z % 4 == 0 && x > 40) {
				p.put(x, -1, z, Blocks.COARSE_DIRT); p.put(x, 0, z, Blocks.PUMPKIN);
			} else {
				p.put(x, -1, z, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
				p.put(x, 0, z, Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 7));
			}
		}
		for (int x = 32; x <= 35; x++) p.put(x, -1, -15, Blocks.COBBLESTONE);
		p.put(34, 0, -16, Blocks.COMPOSTER);
		p.put(35, 0, -6, Blocks.HAY_BLOCK); p.put(35, 1, -6, Blocks.CARVED_PUMPKIN);
	}

	private void graveyard() {
		for (int x = -49; x <= -35; x++) for (int z = -43; z <= -28; z++) {
			p.put(x, 0, z, Blocks.AIR);
			if (x == -49 || x == -35 || z == -43 || z == -28) {
				if (x >= -43 && x <= -41 && z == -28) continue;
				p.put(x, 0, z, Blocks.MOSSY_STONE_BRICK_WALL);
				p.put(x, 1, z, Blocks.IRON_BARS);
			}
		}
		for (var grave : HauntTownLandmarks.GRAVES) {
			p.put(grave.x(), 0, grave.z(), Blocks.STONE_BRICK_WALL);
			p.put(grave.x(), 1, grave.z(), Blocks.STONE_BRICK_SLAB);
			p.put(grave.x(), -1, grave.z() + 1, Blocks.COARSE_DIRT);
			p.put(grave.x(), 0, grave.z() + 1, Blocks.STONE_SLAB);
			p.put(grave.x(), 0, grave.z() + 2, Blocks.POTTED_RED_TULIP);
		}
		for (int x : new int[] {-44, -40}) {
			p.fill(x, 0, -28, x, 2, -28, Blocks.STONE_BRICKS);
			p.put(x, 3, -28, Blocks.SOUL_LANTERN);
		}
		for (int z = -28; z <= -16; z++) p.put(-42, -1, z, Blocks.GRAVEL);
		for (int x = -42; x <= -33; x++) p.put(x, -1, -16, Blocks.GRAVEL);
		for (int z = -16; z <= -4; z++) p.put(-33, -1, z, Blocks.GRAVEL);
	}

	private void forest() {
		Random random = new Random(0x48414C4C4F574545L);
		for (int x = -51; x <= 51; x += 6) for (int z = -51; z <= 51; z += 6) {
			int tx = x + random.nextInt(2), tz = z + random.nextInt(2);
			boolean belt = Math.abs(tx) > 43 || Math.abs(tz) > 43;
			boolean northGrove = tz < -34 && tx > -27;
			if (!(belt || northGrove) || Math.abs(tx) < 6 || Math.abs(tz) < 6) continue;
			if (tx < -32 && tz > -46 && tz < 17 || tx > 10 && tz > 31 && tz < 52
				|| tx > 32 && tz > -27 && tz < -3 || tx < -8 && tx > -30 && tz > 33 && tz < 49) continue;
			tree(tx, tz, elevation(tx, tz), 4 + random.nextInt(3));
		}
		for (int x : new int[] {15, 24, 33, 44}) tree(x, 53, 0, 5);
	}

	private void tree(int x, int z, int base, int height) {
		p.fill(x, base, z, x, base + height, z, Blocks.SPRUCE_LOG);
		Random foliage = new Random(x * 7349L + z * 9151L);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 2; dy++) {
			if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) > 4 || dx == 0 && dz == 0 && dy <= 0) continue;
			int shade = foliage.nextInt(10);
			var leaves = shade == 0 ? Blocks.YELLOW_TERRACOTTA.getDefaultState() : shade < 3 ? Blocks.ORANGE_TERRACOTTA.getDefaultState()
				: Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
			p.put(x + dx, base + height + dy, z + dz, leaves);
		}
		p.put(x + 1, base, z, Blocks.PUMPKIN);
	}

	private void roadsideLamp(int x, int z) {
		p.fill(x, 0, z, x, 3, z, Blocks.SPRUCE_FENCE);
		p.put(x, 4, z, Blocks.LANTERN);
		p.put(x + (x < 0 ? -1 : 1), 0, z, Blocks.JACK_O_LANTERN.getDefaultState().with(CarvedPumpkinBlock.FACING, Direction.SOUTH));
	}
}
