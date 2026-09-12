package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.entity.HeadAmmoEntity;
import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.head.PlayerHeadState;
import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class SkillHandler {
	private SkillHandler() {
	}

	public static void onSkill(ServerPlayerEntity player, boolean pressed) {
		PlayerHeadState state = PlayerHeadAccess.state(player);
		state.skillHeld = pressed;
		if (!pressed) {
			return;
		}
		HeadType worn = HeadLookups.worn(player);
		if (worn == null) {
			return;
		}
		switch (worn.wearStyle) {
			case CHARGED_CREEPER -> lightning(player, state);
			case CREEPER -> creeperAmmo(player, state);
			case GOAT -> state.goatDashTicks = 12;
			case FROG -> grab(player, state);
			case LLAMA -> spit(player, state);
			default -> {
			}
		}
	}

	private static void lightning(ServerPlayerEntity player, PlayerHeadState state) {
		int needed = HeadRules.lightningChargeNeeded(player.getWorld().isThundering());
		if (state.lightningCharge < needed) {
			return;
		}
		LivingEntity target = rayEntity(player, 16);
		if (target == null || target == player) {
			return;
		}
		state.lightningCharge = 0;
		ServerWorld world = player.getServerWorld();
		var bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(world);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
			world.spawnEntity(bolt);
		}
	}

	private static void creeperAmmo(ServerPlayerEntity player, PlayerHeadState state) {
		ServerWorld world = player.getServerWorld();
		List<HeadAmmoEntity> live = world.getEntitiesByClass(
			HeadAmmoEntity.class,
			player.getBoundingBox().expand(64),
			ammo -> player.getUuid().equals(ammo.ownerId())
		);
		if (state.creeperCharges <= 0 || live.size() >= HeadRules.CREEPER_MAX_CHARGES) {
			live.forEach(HeadAmmoEntity::detonate);
			return;
		}
		HeadAmmoEntity ammo = new HeadAmmoEntity(world, player);
		Vec3d look = player.getRotationVec(1.0F);
		ammo.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
		ammo.setVelocity(look.x, look.y, look.z, 1.2F, 1.0F);
		world.spawnEntity(ammo);
		state.creeperCharges--;
		state.lastAmmo = ammo.getUuid();
	}

	private static void spit(ServerPlayerEntity player, PlayerHeadState state) {
		if (state.llamaCooldown > 0) {
			return;
		}
		state.llamaCooldown = HeadRules.LLAMA_COOLDOWN_TICKS;
		LivingEntity target = rayEntity(player, 16);
		if (target != null && target != player) {
			target.damage(player.getDamageSources().playerAttack(player), 1);
		}
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LLAMA_SPIT, player.getSoundCategory(), 1.0F, 1.0F);
	}

	private static void grab(ServerPlayerEntity player, PlayerHeadState state) {
		if (state.frogCooldown > 0) {
			return;
		}
		HitResult hit = rayAnything(player, HeadRules.FROG_RANGE);
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}
		state.frogCooldown = HeadRules.FROG_COOLDOWN_TICKS;
		ServerWorld world = player.getServerWorld();
		if (hit instanceof EntityHitResult entityHit) {
			Entity entity = entityHit.getEntity();
			if (entity instanceof PlayerEntity && entity != player) {
				pull(entity, player);
				return;
			}
			if (entity instanceof LivingEntity living) {
				if (living == player || HeadLookups.isBoss(living)) {
					return;
				}
				pull(living, player);
				return;
			}
			if (entity instanceof ItemEntity item) {
				giveOrDrop(player, item.getStack());
				item.discard();
			}
			return;
		}
		if (hit instanceof BlockHitResult blockHit) {
			BlockPos pos = blockHit.getBlockPos();
			BlockState blockState = world.getBlockState(pos);
			if (blockState.getHardness(world, pos) < 0 || blockState.isOf(Blocks.BEDROCK)) {
				return;
			}
			BlockEntity blockEntity = world.getBlockEntity(pos);
			boolean container = blockEntity instanceof net.minecraft.inventory.Inventory || blockState.isIn(BlockTags.SHULKER_BOXES)
				|| blockState.isOf(Blocks.CHEST) || blockState.isOf(Blocks.TRAPPED_CHEST) || blockState.isOf(Blocks.BARREL);
			if (container || blockState.isSolid()) {
				List<ItemStack> drops = Block.getDroppedStacks(blockState, world, pos, blockEntity, player, ItemStack.EMPTY);
				world.breakBlock(pos, false, player);
				for (ItemStack drop : drops) {
					giveOrDrop(player, drop);
				}
			}
		}
	}

	private static void pull(Entity entity, PlayerEntity player) {
		Vec3d to = player.getPos().subtract(entity.getPos()).normalize().multiply(1.15).add(0, 0.25, 0);
		entity.addVelocity(to.x, to.y, to.z);
		entity.velocityModified = true;
	}

	private static void giveOrDrop(PlayerEntity player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	private static LivingEntity rayEntity(PlayerEntity player, double range) {
		HitResult hit = rayAnything(player, range);
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	private static HitResult rayAnything(PlayerEntity player, double range) {
		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(range));
		HitResult block = player.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		EntityHitResult entity = rayEntities(player, start, block.getType() == HitResult.Type.MISS ? end : block.getPos());
		if (entity != null) {
			double entityDist = entity.getPos().squaredDistanceTo(start);
			double blockDist = block.getPos().squaredDistanceTo(start);
			if (block.getType() == HitResult.Type.MISS || entityDist < blockDist) {
				return entity;
			}
		}
		return block;
	}

	private static EntityHitResult rayEntities(PlayerEntity player, Vec3d start, Vec3d end) {
		Box box = player.getBoundingBox().stretch(end.subtract(start)).expand(1.0);
		EntityHitResult best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity entity : player.getWorld().getOtherEntities(player, box)) {
			if (entity instanceof LivingEntity living && HeadlessAccess.isHeadless(living)) {
				// still grabbable
			}
			var optional = entity.getBoundingBox().expand(0.15).raycast(start, end);
			if (optional.isEmpty()) {
				continue;
			}
			double dist = optional.get().squaredDistanceTo(start);
			if (dist < bestDist) {
				bestDist = dist;
				best = new EntityHitResult(entity, optional.get());
			}
		}
		return best;
	}

}
