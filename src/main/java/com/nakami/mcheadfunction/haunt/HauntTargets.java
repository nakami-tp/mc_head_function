package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.rule.HauntRules.HostKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.LightningRodBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class HauntTargets {
	private HauntTargets() { }

	public record Target(BlockPos block, Entity entity, BlockState original) {
		static Target of(BlockPos pos, BlockState state) { return new Target(pos.toImmutable(), null, state); }
		static Target of(Entity entity) { return new Target(entity.getBlockPos(), entity, Blocks.AIR.getDefaultState()); }
		public Vec3d pos() { return entity != null ? entity.getPos() : Vec3d.ofCenter(block); }
	}

	public static Box range(BlockPos tree) {
		return new Box(tree).expand(HauntTreeShape.RANGE, HauntTreeShape.RANGE_Y, HauntTreeShape.RANGE);
	}

	public static List<Target> pick(ServerWorld world, BlockPos tree, HostKind kind, int count) {
		var box = range(tree);
		List<Target> candidates = new ArrayList<>();
		if (kind == HostKind.MINECART) {
			for (var cart : world.getEntitiesByClass(AbstractMinecartEntity.class, box, Entity::isAlive)) {
				if (!already(world, cart, null)) candidates.add(Target.of(cart));
			}
		} else if (kind == HostKind.ARMOR_STAND) {
			for (var stand : world.getEntitiesByClass(ArmorStandEntity.class, box, Entity::isAlive)) {
				if (!already(world, stand, null)) candidates.add(Target.of(stand));
			}
		} else {
			int r = (int) HauntTreeShape.RANGE;
			int y = (int) HauntTreeShape.RANGE_Y;
			for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) for (int dy = -y; dy <= y; dy++) {
				BlockPos pos = tree.add(x, dy, z);
				BlockState state = world.getBlockState(pos);
				if (matches(kind, state) && !already(world, null, pos)) candidates.add(Target.of(pos, state));
			}
		}
		return HauntRules.pick(candidates, count, new java.util.Random(world.random.nextLong()));
	}

	public static Vec3d gather(BlockPos tree, List<Target> targets) {
		Vec3d sum = Vec3d.ZERO;
		for (Target target : targets) sum = sum.add(target.pos());
		Vec3d mid = sum.multiply(1.0 / targets.size());
		return new Vec3d(mid.x, Math.max(mid.y, tree.getY()) + 6, mid.z);
	}

	public static boolean matches(HostKind kind, BlockState state) {
		return switch (kind) {
			case CHEST -> state.getBlock() instanceof ChestBlock;
			case PUMPKIN -> state.isOf(Blocks.CARVED_PUMPKIN) || state.isOf(Blocks.JACK_O_LANTERN);
			case DISPENSER -> state.getBlock() instanceof DispenserBlock;
			case LIGHTNING_ROD -> state.getBlock() instanceof LightningRodBlock;
			case FURNACE -> state.getBlock() instanceof AbstractFurnaceBlock;
			case CARPET -> state.getBlock() instanceof CarpetBlock;
			case LANTERN -> state.isOf(Blocks.LANTERN) || state.isOf(Blocks.SOUL_LANTERN);
			default -> false;
		};
	}

	private static boolean already(ServerWorld world, Entity entity, BlockPos pos) {
		return !world.getEntitiesByClass(GhostHeadEntity.class, range(pos != null ? pos : entity.getBlockPos()).expand(2),
			ghost -> ghost.claims(entity, pos)).isEmpty();
	}
}
