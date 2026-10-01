package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.net.GoatImpactS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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
		// Only the immediate contact surface, never the floor or blocks beyond a wall.
		var body = player.getBoundingBox().contract(0.001);
		var contact = body.stretch(direction.multiply(0.35));
		BlockPos next = null;
		double nearest = Double.MAX_VALUE;
		for (var pos : BlockPos.iterate(BlockPos.ofFloored(contact.minX, contact.minY, contact.minZ),
			BlockPos.ofFloored(contact.maxX, contact.maxY, contact.maxZ))) {
			var block = level.getBlockState(pos);
			boolean intersects = block.getCollisionShape(level, pos).getBoundingBoxes().stream()
				.anyMatch(box -> box.offset(pos).intersects(contact));
			if (!intersects) continue;
			double distance = pos.toCenterPos().squaredDistanceTo(player.getPos().add(0, 0.9, 0));
			if (distance < nearest) { nearest = distance; next = pos.toImmutable(); }
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
		// Dirt: 5 ticks; stone: 15 ticks; obsidian: 500 ticks (longer than one charge).
		int required = Math.max(3, (int) Math.ceil(hardness * 10));
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
}
