package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.head.HeadEffects;
import com.nakami.mcheadfunction.head.HeadSounds;
import com.nakami.mcheadfunction.rule.HeadDepthRules;
import com.nakami.mcheadfunction.rule.HeadRules;
import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.rule.HauntRules.HostKind;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public final class HauntBehaviors {
	private HauntBehaviors() { }

	public static boolean canHarm(Entity source, LivingEntity target) {
		return target.isAlive() && !target.isSpectator() && !(target instanceof ArmorStandEntity);
	}

	public static void tick(ServerWorld world, GhostHeadEntity ghost) {
		if (ghost.kind() == null) return;
		switch (ghost.kind()) {
			case CHEST -> chest(world, ghost);
			case PUMPKIN -> pumpkin(world, ghost);
			case DISPENSER -> dispenser(world, ghost);
			case LIGHTNING_ROD -> rod(world, ghost);
			case FURNACE -> furnace(world, ghost);
			case MINECART -> minecart(world, ghost);
			case ARMOR_STAND -> armor(world, ghost);
			case CARPET -> carpet(world, ghost);
			case LANTERN -> lantern(world, ghost);
		}
	}

	public static void release(ServerWorld world, GhostHeadEntity ghost) {
		Entity host = ghost.host();
		if (host instanceof PossessedPropEntity prop) {
			BlockPos at = prop.getBlockPos();
			restore(world, ghost.original(), at);
			if (ghost.kind() == HostKind.LANTERN) {
				BlockPos light = at.up();
				if (world.getBlockState(light).isOf(Blocks.LIGHT)) world.removeBlock(light, false);
			}
			prop.discard();
		} else if (host instanceof AbstractMinecartEntity || host instanceof ArmorStandEntity) {
			host.setVelocity(Vec3d.ZERO);
			host.velocityModified = true;
		} else if (ghost.hostBlock() != null) {
			BlockState state = world.getBlockState(ghost.hostBlock());
			if (state.getBlock() instanceof ChestBlock) {
				for (var tongue : world.getEntitiesByClass(HauntTongueEntity.class, new Box(ghost.hostBlock()).expand(24), t -> t.belongsTo(ghost))) tongue.finish();
				world.addSyncedBlockEvent(ghost.hostBlock(), state.getBlock(), 1, 0);
			}
			if (state.getBlock() instanceof AbstractFurnaceBlock) {
				world.setBlockState(ghost.hostBlock(), state.with(AbstractFurnaceBlock.LIT, false));
			}
			if (ghost.original().getBlock() instanceof DispenserBlock && state.getBlock() instanceof DispenserBlock) {
				world.setBlockState(ghost.hostBlock(), ghost.original());
			}
			if (ghost.original().getBlock() instanceof AbstractFurnaceBlock && state.getBlock() instanceof AbstractFurnaceBlock) {
				world.setBlockState(ghost.hostBlock(), ghost.original().with(AbstractFurnaceBlock.LIT, false));
			}
		}
	}

	private static void restore(ServerWorld world, BlockState original, BlockPos pos) {
		if (original.isAir()) return;
		if (world.getBlockState(pos).isReplaceable()) world.setBlockState(pos, original);
		else Block.dropStack(world, pos, original.getBlock().asItem().getDefaultStack());
	}

	private static void chest(ServerWorld world, GhostHeadEntity ghost) {
		BlockPos pos = ghost.hostBlock();
		if (pos == null || !(world.getBlockEntity(pos) instanceof ChestBlockEntity)) return;
		if (ghost.age % 40 != 1) return;
		if (!world.getEntitiesByClass(HauntTongueEntity.class, new Box(pos).expand(16), t -> t.chest().equals(pos)).isEmpty()) return;
		var busy = world.getEntitiesByClass(HauntTongueEntity.class, new Box(pos).expand(32), t -> t.targetId() >= 0).stream()
			.map(HauntTongueEntity::targetId).collect(java.util.stream.Collectors.toSet());
		Entity living = world.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(16), HauntTongueEntity::canPull).stream()
			.filter(e -> !busy.contains(e.getId()))
			.filter(e -> e.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 16 * 16)
			.min(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(Vec3d.ofCenter(pos)))).orElse(null);
		ItemEntity item = nearestItem(world, Vec3d.ofCenter(pos), 16);
		if (living == null && item == null) return;
		world.addSyncedBlockEvent(pos, world.getBlockState(pos).getBlock(), 1, 1);
		world.spawnEntity(new HauntTongueEntity(world, pos, living != null ? living : item, ghost));
	}

	private static void pumpkin(ServerWorld world, GhostHeadEntity ghost) {
		if (!(ghost.host() instanceof PossessedPropEntity pumpkin)) return;
		pumpkin.noClip = false;
		LivingEntity target = nearest(world, pumpkin.getPos(), 16, ghost);
		if (target == null) return;
		Vec3d to = target.getPos().subtract(pumpkin.getPos()).multiply(1, 0, 1);
		if (pumpkin.isOnGround() && ghost.age % 4 == 0 && to.lengthSquared() > 0.01) {
			pumpkin.setVelocity(to.normalize().multiply(0.22).add(0, 0.28, 0));
			pumpkin.velocityModified = true;
		}
		if (pumpkin.getBoundingBox().expand(0.4).intersects(target.getBoundingBox()) && ghost.age % 15 == 0
			&& target.damage(world.getDamageSources().mobProjectile(pumpkin, null), HeadRules.ZOMBIE_BITE_DAMAGE)) {
			HeadEffects.burst(world, target.getPos().add(0, 0.5, 0), ghost.head());
			HeadSounds.play(pumpkin, SoundEvents.ENTITY_GENERIC_EAT, 0.8F, 0.7F);
		}
	}

	private static void dispenser(ServerWorld world, GhostHeadEntity ghost) {
		BlockPos pos = ghost.hostBlock();
		if (pos == null) return;
		LivingEntity target = nearest(world, Vec3d.ofCenter(pos), 16, ghost);
		if (target == null) return;
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof DispenserBlock)) return;
		Vec3d to = target.getBoundingBox().getCenter().subtract(Vec3d.ofCenter(pos));
		world.setBlockState(pos, state.with(DispenserBlock.FACING, Direction.getFacing(to.x, to.y, to.z)));
		int beat = ghost.age % 38;
		if (beat >= 18 || beat % 3 != 0) return;
		var spit = EntityType.LLAMA_SPIT.create(world);
		if (spit == null) return;
		Vec3d mouth = Vec3d.ofCenter(pos).add(Vec3d.of(Direction.getFacing(to.x, to.y, to.z).getVector()).multiply(0.6));
		spit.setPosition(mouth);
		spit.setVelocity(to.normalize().multiply(1.4));
		world.spawnEntity(spit);
		target.damage(world.getDamageSources().mobProjectile(spit, null), 1);
		HeadSounds.play(ghost, SoundEvents.ENTITY_LLAMA_SPIT, 0.35F, 1.15F);
	}

	private static void rod(ServerWorld world, GhostHeadEntity ghost) {
		if (ghost.age % (60 + world.random.nextInt(61)) != 0) return;
		BlockPos pos = ghost.hostBlock();
		if (pos == null) return;
		int x = pos.getX() + world.random.nextBetween(-10, 10);
		int z = pos.getZ() + world.random.nextBetween(-10, 10);
		BlockPos strike = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos(x, pos.getY(), z));
		var bolt = EntityType.LIGHTNING_BOLT.create(world);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(strike.getX(), strike.getY(), strike.getZ());
			world.spawnEntity(bolt);
		}
	}

	private static void furnace(ServerWorld world, GhostHeadEntity ghost) {
		BlockPos pos = ghost.hostBlock();
		if (pos == null) return;
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof AbstractFurnaceBlock)) return;
		if (!state.get(AbstractFurnaceBlock.LIT)) world.setBlockState(pos, state.with(AbstractFurnaceBlock.LIT, true));
		if (ghost.age % 25 == 0) world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT, net.minecraft.sound.SoundCategory.BLOCKS, 0.7F, 0.7F);
		if (ghost.age % 17 == 0) world.playSound(null, pos, SoundEvents.BLOCK_CAMPFIRE_CRACKLE, net.minecraft.sound.SoundCategory.BLOCKS, 0.4F, 0.85F);
	}

	static void lightFurnace(ServerWorld world, GhostHeadEntity ghost) {
		furnace(world, ghost);
	}

	private static void minecart(ServerWorld world, GhostHeadEntity ghost) {
		if (!(ghost.host() instanceof AbstractMinecartEntity cart)) return;
		cart.setNoGravity(true);
		Vec3d home = Vec3d.ofCenter(ghost.treePos());
		double cartX = cart.getX() - home.x;
		double cartZ = cart.getZ() - home.z;
		boolean occupied = cart.hasPassengers();
		var riders = occupied ? java.util.List.<LivingEntity>of()
			: world.getEntitiesByClass(LivingEntity.class, cart.getBoundingBox().expand(HauntRules.CART_RIDER_RANGE),
				e -> canBoard(cart, e));
		double[] riderX = new double[riders.size()];
		double[] riderZ = new double[riders.size()];
		for (int i = 0; i < riders.size(); i++) {
			riderX[i] = riders.get(i).getX() - home.x;
			riderZ[i] = riders.get(i).getZ() - home.z;
		}
		if (HauntRules.cartArrived(cartX, cartZ, ghost.dest().x - home.x, ghost.dest().z - home.z)) {
			var next = HauntRules.cartWaypoint(new java.util.Random(world.random.nextLong()), cartX, cartZ, riderX, riderZ);
			ghost.setDest(home.add(next.x(), 0, next.z()));
		}
		Vec3d vel = cart.getVelocity();
		double heading = vel.horizontalLengthSquared() < 0.0001
			? world.random.nextDouble() * Math.PI * 2
			: Math.atan2(vel.z, vel.x);
		double wander = (world.random.nextDouble() - 0.5) * 2 * HauntRules.CART_WANDER_TURN;
		heading = HauntRules.cartHeading(heading, wander, cartX, cartZ,
			ghost.dest().x - home.x, ghost.dest().z - home.z, occupied, riderX, riderZ);
		BlockPos front = BlockPos.ofFloored(cart.getPos().add(Math.cos(heading) * 0.8, 0, Math.sin(heading) * 0.8));
		BlockState hit = world.getBlockState(front);
		if (!hit.isAir() && !hit.isOf(HauntBlocks.TREE)) {
			float hardness = hit.getHardness(world, front);
			if (hardness >= 0 && hardness <= 3) world.breakBlock(front, true);
			else heading = HauntRules.cartBounce(heading, world.random.nextDouble());
		}
		double speed = Math.min(HauntRules.CART_MAX_SPEED, vel.horizontalLength() + HauntRules.CART_ACCEL);
		Vec3d dir = new Vec3d(Math.cos(heading), 0, Math.sin(heading));
		Box sweep = cart.getBoundingBox().stretch(dir.x * speed, 0, dir.z * speed).expand(0.4);
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, sweep, e -> canHarm(cart, e))) {
			if (living.getVehicle() == cart) continue;
			boolean boarded = HauntRules.cartBoards(occupied, canBoard(cart, living)) && living.startRiding(cart);
			if (boarded) {
				occupied = true;
				HeadSounds.play(cart, SoundEvents.ENTITY_GOAT_RAM_IMPACT, 0.7F, 1.05F);
				continue;
			}
			if (HauntRules.cartRams(boarded) && living.damage(world.getDamageSources().mobProjectile(cart, null), HauntRules.CART_HIT)) {
				living.setVelocity(dir.multiply(0.65).add(0, 0.8, 0));
				living.velocityModified = true;
			}
		}
		cart.setVelocity(dir.x * speed, 0, dir.z * speed);
		cart.velocityModified = true;
		cart.setYaw((float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90));
		cart.setPosition(cart.getX() + dir.x * speed, cart.getY(), cart.getZ() + dir.z * speed);
	}

	private static boolean canBoard(AbstractMinecartEntity cart, LivingEntity living) {
		return canHarm(cart, living) && !living.hasVehicle();
	}

	private static void armor(ServerWorld world, GhostHeadEntity ghost) {
		if (!(ghost.host() instanceof ArmorStandEntity stand)) return;
		stand.fallDistance = 0;
		if (ghost.age % 20 != 0) return;
		double homeY = ghost.hostBlock() != null ? ghost.hostBlock().getY() : stand.getY();
		if (stand.getY() > homeY + 0.25) return;
		double vx = (world.random.nextDouble() - 0.5) * 0.25;
		double vz = (world.random.nextDouble() - 0.5) * 0.25;
		stand.setVelocity(vx, HeadDepthRules.rabbitJumpVelocity(1), vz);
		stand.velocityModified = true;
		stand.setOnGround(false);
	}

	private static void carpet(ServerWorld world, GhostHeadEntity ghost) {
		if (!(ghost.host() instanceof PossessedPropEntity carpet)) return;
		carpet.noClip = true;
		double hover = 2.5 + Math.sin(ghost.age * 0.05) * 0.6;
		double destY = ghost.hostBlock().getY() + hover;
		double y = MathHelper.lerp(0.08, carpet.getY(), destY);
		double wobble = Math.sin(ghost.age * 0.07 + carpet.getId()) * 0.03;
		carpet.setPosition(carpet.getX() + wobble, y, carpet.getZ() + Math.cos(ghost.age * 0.06) * 0.03);
		carpet.setVelocity(Vec3d.ZERO);
	}

	private static void lantern(ServerWorld world, GhostHeadEntity ghost) {
		if (!(ghost.host() instanceof PossessedPropEntity lantern)) return;
		lantern.noClip = true;
		lantern.setVelocity(Vec3d.ZERO);
		if (ghost.age % 8 == 0) lantern.setLight(world.random.nextBetween(0, 15));
		BlockPos light = lantern.getBlockPos();
		int level = Math.max(0, Math.min(15, lantern.light()));
		BlockState want = Blocks.LIGHT.getDefaultState().with(net.minecraft.block.LightBlock.LEVEL_15, level);
		if (!world.getBlockState(light).isOf(Blocks.LIGHT)) {
			if (world.getBlockState(light).isAir()) world.setBlockState(light, want);
		} else if (world.getBlockState(light) != want) world.setBlockState(light, want);
	}

	private static LivingEntity nearest(ServerWorld world, Vec3d at, double range, Entity source) {
		LivingEntity best = null;
		double bestD = range * range;
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(range), e -> canHarm(source, e))) {
			double d = living.squaredDistanceTo(at);
			if (d < bestD) { bestD = d; best = living; }
		}
		return best;
	}

	private static ItemEntity nearestItem(ServerWorld world, Vec3d at, double range) {
		ItemEntity best = null;
		double bestD = range * range;
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(at, at).expand(range), e -> !e.getStack().isEmpty())) {
			double d = item.squaredDistanceTo(at);
			if (d < bestD) { bestD = d; best = item; }
		}
		return best;
	}

}
