package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.net.GoatImpactS2CPayload;
import com.nakami.mcheadfunction.rule.HeadDepthRules;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** One contact at a time; progress is discarded when the charge leaves that block. */
public final class GoatMining {
	private ServerWorld world;
	private BlockPos target;
	private BlockState original;
	private int breaker, ticks;

	public void clear() {
		if (target != null) world.setBlockBreakingInfo(breaker, target, -1);
		target = null;
		world = null;
		ticks = 0;
	}

	/** Returns true while the player must remain against the obstacle. */
	public boolean tick(ServerPlayerEntity player, Vec3d direction) {
		var level = player.getServerWorld();
		double speed = PlayerHeadAccess.state(player).goatSpeed;
		var body = player.getBoundingBox().contract(0.001);
		var sweep = body.stretch(direction.multiply(Math.max(0.35, speed)));
		var contact = body.stretch(direction.multiply(0.35));
		List<BlockPos> crush = new ArrayList<>();
		double highestSolid = player.getY();
		boolean anySolid = false;
		BlockPos next = null;
		double nearest = Double.MAX_VALUE;
		for (var pos : BlockPos.iterate(BlockPos.ofFloored(sweep.minX, sweep.minY, sweep.minZ),
			BlockPos.ofFloored(sweep.maxX, sweep.maxY, sweep.maxZ))) {
			var block = level.getBlockState(pos);
			if (block.isAir()) continue;
			var shape = block.getCollisionShape(level, pos);
			boolean inCell = new Box(pos).intersects(sweep);
			boolean inSweep = inCell && (shape.isEmpty()
				|| shape.getBoundingBoxes().stream().anyMatch(box -> box.offset(pos).intersects(sweep)));
			if (inCell && crushThrough(block, level, pos, player)) {
				crush.add(pos.toImmutable());
				continue;
			}
			if (!inSweep || shape.isEmpty()) continue;
			boolean inContact = shape.getBoundingBoxes().stream().anyMatch(box -> box.offset(pos).intersects(contact));
			if (!inContact) continue;
			anySolid = true;
			highestSolid = Math.max(highestSolid, pos.getY() + shape.getMax(Direction.Axis.Y));
			double distance = pos.toCenterPos().squaredDistanceTo(player.getPos().add(0, 0.9, 0));
			if (distance < nearest) { nearest = distance; next = pos.toImmutable(); }
		}
		for (BlockPos pos : crush) smash(level, pos, player);
		if (!anySolid || highestSolid <= player.getY() + HeadDepthRules.GOAT_STEP_HEIGHT) {
			clear();
			return false;
		}
		if (next == null) { clear(); return false; }
		var block = level.getBlockState(next);
		if (world != level || !next.equals(target) || block != original) {
			clear(); world = level; target = next; original = block;
			// Separate from vanilla mining overlays keyed by the player's positive entity ID.
			breaker = -player.getId() - 1;
		}
		float hardness = block.getHardness(level, next);
		if (hardness < 0 || !player.getAbilities().allowModifyWorld || !level.canPlayerModifyAt(player, next)
			|| !level.getWorldBorder().contains(next)) {
			HeadSounds.play(player, SoundEvents.ENTITY_GOAT_RAM_IMPACT, 0.8F, 0.65F);
			GoatCharge.stop(player);
			return true;
		}
		GoatCharge.setChargeVelocity(player, new Vec3d(direction.x * 0.08, player.getVelocity().y, direction.z * 0.08));
		ticks++;
		// Dirt: 3 ticks; stone: 8 ticks; obsidian: 250 ticks (longer than one charge).
		int required = Math.max(HeadDepthRules.GOAT_MINE_MIN_TICKS, (int) Math.ceil(hardness * HeadDepthRules.GOAT_MINE_TICKS_PER_HARDNESS));
		level.setBlockBreakingInfo(breaker, next, Math.min(9, ticks * 10 / required));
		if (ticks == 1 || ticks % 4 == 0) {
			var sound = block.getSoundGroup();
			level.playSound(null, next, sound.getHitSound(), SoundCategory.BLOCKS, 0.7F, 0.75F);
			HeadSounds.play(player, SoundEvents.ENTITY_GOAT_RAM_IMPACT, 0.3F, 0.85F);
			var hit = new Vec3d(player.getX(), next.getY() + 0.5, player.getZ()).add(direction.multiply(0.25));
			level.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, block), hit.x, hit.y, hit.z, 9, 0.18, 0.35, 0.18, 0.06);
			ServerPlayNetworking.send(player, new GoatImpactS2CPayload(5));
		}
		if (ticks >= required) {
			level.breakBlock(next, true, player);
			clear();
		}
		return true;
	}

	private static boolean crushThrough(BlockState state, ServerWorld level, BlockPos pos, ServerPlayerEntity player) {
		if (!player.getAbilities().allowModifyWorld || !level.canPlayerModifyAt(player, pos) || !level.getWorldBorder().contains(pos)) {
			return false;
		}
		float hardness = state.getHardness(level, pos);
		if (hardness < 0) return false;
		if (hardness <= HeadDepthRules.GOAT_CRUSH_HARDNESS) return true;
		var block = state.getBlock();
		if (block instanceof FenceGateBlock) return true;
		if (block instanceof DoorBlock && !state.isOf(Blocks.IRON_DOOR)) return true;
		return state.isOf(Blocks.BAMBOO) || state.isOf(Blocks.BAMBOO_SAPLING)
			|| state.isOf(Blocks.ICE) || state.isOf(Blocks.FROSTED_ICE);
	}

	private static void smash(ServerWorld level, BlockPos pos, ServerPlayerEntity player) {
		var block = level.getBlockState(pos);
		if (block.isAir()) return;
		var center = pos.toCenterPos();
		level.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, block), center.x, center.y, center.z, 18, 0.28, 0.28, 0.28, 0.14);
		level.breakBlock(pos, true, player);
	}
}
