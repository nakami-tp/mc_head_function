package com.nakami.mcheadfunction.behead;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.rule.BeheadingTracker;
import com.nakami.mcheadfunction.rule.HeadRules;
import com.nakami.mcheadfunction.throwing.ThrowHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadShakeAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class BeheadingHandler {
	private static final BeheadingTracker TRACKER = new BeheadingTracker();

	private BeheadingHandler() {
	}

	public static boolean tryShake(PlayerEntity player, LivingEntity target) {
		if (player.getWorld().isClient() || !shouldCancelMelee(player, target)) {
			return false;
		}
		player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		((HeadShakeAccess) target).mhf$setHeadShakeTicks(HeadShakeAccess.DURATION);
		int hit = TRACKER.registerHit(player.getUuid(), target.getUuid(), player.getWorld().getTime() * 50L);
		if (player.getMainHandStack().isOf(HeadItems.BEHEADING_GLOVE)) {
			((com.nakami.mcheadfunction.head.GlovePunchAccess) player).mhf$glovePunch(hit);
		}
		HeadSounds.punch(target, hit);
		if (player.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 6, 0.15, 0.15, 0.15, 0.02);
		}
		if (hit == HeadRules.SHAKE_HITS) {
			behead(player, target);
			if (player.getMainHandStack().isOf(HeadItems.BEHEADING_GLOVE)) {
				player.getMainHandStack().damage(1, player, EquipmentSlot.MAINHAND);
			}
		}
		return true;
	}

	private static void behead(PlayerEntity player, LivingEntity target) {
		HeadType type = HeadLookups.fromVictim(target);
		if (type == null) {
			return;
		}
		HeadlessAccess.setHeadless(target, true);
		((HeadShakeAccess) target).mhf$setHeadShakeTicks(0);
		ThrowHandler.knockOff(player, target, type);
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
		if (!target.isAlive() || !HeadLookups.canBehead(target)) return false;
		if (player.getMainHandStack().isOf(HeadItems.BEHEADING_GLOVE)) {
			// Vanilla already selects the entity under the crosshair. Keep range and line-of-sight
			// validation here too, so server-side callers cannot punch through walls or at a distance.
			return player.canInteractWithEntity(target, 0) && player.canSee(target);
		}
		return player.getMainHandStack().isEmpty() && aimingAtHead(player, target);
	}

	public static boolean holdingHead(ItemStack stack) {
		return com.nakami.mcheadfunction.head.HeadItems.isHead(stack);
	}
}
