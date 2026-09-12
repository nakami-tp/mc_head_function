package com.nakami.mcheadfunction.behead;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.rule.BeheadingTracker;
import com.nakami.mcheadfunction.rule.HeadRules;
import com.nakami.mcheadfunction.throwing.ThrowHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class BeheadingHandler {
	private static final BeheadingTracker TRACKER = new BeheadingTracker();

	private BeheadingHandler() {
	}

	public static boolean tryShake(PlayerEntity player, LivingEntity target) {
		if (player.getWorld().isClient() || !player.getMainHandStack().isEmpty()) {
			return false;
		}
		if (!HeadLookups.canBehead(target) || !aimingAtHead(player, target)) {
			return false;
		}
		player.swingHand(player.getActiveHand());
		if (player.getWorld() instanceof ServerWorld world) {
			HeadSounds.play(target, SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 0.45F, 1.15F);
			world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 6, 0.15, 0.15, 0.15, 0.02);
		}
		boolean done = TRACKER.registerHit(player.getUuid(), target.getUuid(), player.getWorld().getTime() * 50L);
		if (done) {
			behead(player, target);
		}
		return true;
	}

	private static void behead(PlayerEntity player, LivingEntity target) {
		HeadType type = HeadLookups.fromVictim(target);
		if (type == null) {
			return;
		}
		HeadlessAccess.setHeadless(target, true);
		ThrowHandler.knockOff(player, type);
		HeadSounds.play(target, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.55F, 0.85F);
	}

	public static boolean aimingAtHead(PlayerEntity player, LivingEntity target) {
		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(player.getEntityInteractionRange() + 1.0));
		Box box = target.getBoundingBox();
		var hit = box.raycast(start, end);
		if (hit.isEmpty()) {
			return false;
		}
		HitResult block = player.getWorld().raycast(new RaycastContext(start, hit.get(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (block.getType() == HitResult.Type.BLOCK && block.getPos().squaredDistanceTo(start) + 0.01 < hit.get().squaredDistanceTo(start)) {
			return false;
		}
		return HeadRules.isHeadRegion(hit.get().y, box.minY, box.maxY);
	}

	public static boolean shouldCancelMelee(PlayerEntity player, LivingEntity target) {
		return player.getMainHandStack().isEmpty() && HeadLookups.canBehead(target) && aimingAtHead(player, target);
	}

	public static boolean holdingHead(ItemStack stack) {
		return com.nakami.mcheadfunction.head.HeadItems.isHead(stack);
	}
}
