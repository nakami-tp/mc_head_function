package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadCrushAccess;
import com.nakami.mcheadfunction.head.HeadType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public class GolemGameplayTest implements FabricGameTest {
	// Oversized destructive fixtures use separate elevations so parallel test structures cannot overlap.
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240, batchId = "golem_squash")
	public void giantRollerCrushesRepeatedlyAndRecovers(TestContext context) {
		var world = context.getWorld();
		var origin = context.getAbsolutePos(new BlockPos(5, 60, 5));
		for (int x = -4; x <= 4; x++) for (int z = -4; z <= 45; z++)
			world.setBlockState(origin.add(x, -1, z), Blocks.STONE.getDefaultState());
		for (int z = -1; z <= 3; z++) world.setChunkForced(origin.getX() >> 4, (origin.getZ() >> 4) + z, true);
		var wall = origin.add(0, 1, 3);
		world.setBlockState(wall, Blocks.OBSIDIAN.getDefaultState());
		var bedrock = origin.add(8, 0, 2);
		world.setBlockState(bedrock, Blocks.BEDROCK.getDefaultState());
		var owner = EntityType.COW.create(world);
		owner.setAiDisabled(true);
		owner.setPosition(origin.getX(), origin.getY(), origin.getZ());
		world.spawnEntity(owner);
		var victim = EntityType.COW.create(world);
		victim.setAiDisabled(true);
		victim.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
		victim.setHealth(100);
		victim.setPosition(origin.getX(), origin.getY(), origin.getZ() + 2);
		world.spawnEntity(victim);
		var head = new ThrownHeadEntity(world, owner, HeadType.IRON_GOLEM, HeadType.ThrowStyle.GOLEM_ROLL);
		head.setPosition(origin.getX(), origin.getY(), origin.getZ());
		head.setVelocity(0, 0, 1);
		world.spawnEntity(head);
		float[] initial = {100};
		context.waitAndRun(5, () -> {
			context.assertTrue(head.isGiantRoller() && head.getWidth() == ThrownHeadEntity.GOLEM_SIZE, "Roller must grow and synchronize dimensions");
			context.assertTrue(world.getBlockState(wall).isAir(), "Even obsidian in the path must be crushed");
			context.assertTrue(world.getBlockState(origin.down()).isOf(Blocks.STONE), "Supporting floor must remain");
			context.assertTrue(((HeadCrushAccess) victim).mhf$crushTicks() > 0 && victim.getHealth() < 100, "Victim must be flattened and damaged");
			initial[0] = victim.getHealth();
		});
		context.waitAndRun(25, () -> {
			context.assertTrue(((HeadCrushAccess) victim).mhf$crushTicks() > 180, "Continued contact must refresh ten-second squash");
			context.assertTrue(victim.getHealth() < initial[0], "Contact must cause repeated damage: hp=" + victim.getHealth() + " first=" + initial[0] + " victim=" + victim.getPos() + " head=" + head.getPos() + " removed=" + head.isRemoved());
			context.assertTrue(owner.getHealth() == owner.getMaxHealth() && ((HeadCrushAccess) owner).mhf$crushTicks() == 0, "Owner must be excluded");
			context.assertTrue(Math.abs(head.getZ() - origin.getZ() - 5) < 0.3, "Roller must advance four blocks per second");
		});
		context.waitAndRun(210, () -> {
			context.assertTrue(head.isRemoved(), "Roller must finish within ten seconds: pos=" + head.getPos() + " distance=" + head.getRollDistance(1));
			context.assertTrue(((HeadCrushAccess) victim).mhf$crushTicks() > 0, "Squash must remain before ten seconds after last contact");
			context.assertTrue(world.getBlockState(bedrock).isOf(Blocks.BEDROCK), "Bedrock remains intact");
			context.assertTrue(world.getEntitiesByClass(com.nakami.mcheadfunction.entity.RecycledHeadItemEntity.class,
				head.getBoundingBox().expand(2), e -> com.nakami.mcheadfunction.head.HeadItems.ofStack(e.getStack()) == HeadType.IRON_GOLEM).size() == 1, "Exactly one head must be recycled");
		});
		context.waitAndRun(235, () -> {
			context.assertTrue(((HeadCrushAccess) victim).mhf$crushTicks() == 0, "Squash must recover after ten seconds without contact");
			for (int z = -1; z <= 3; z++) world.setChunkForced(origin.getX() >> 4, (origin.getZ() >> 4) + z, false);
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 50)
	public void unbreakableWallStopsRollerAndKnockedOffHeadStaysSmall(TestContext context) {
		var world = context.getWorld();
		var origin = context.getAbsolutePos(new BlockPos(5, 80, 5));
		for (int x = -4; x <= 4; x++) for (int z = -4; z <= 7; z++)
			world.setBlockState(origin.add(x, -1, z), Blocks.STONE.getDefaultState());
		for (int x = -4; x <= 4; x++) for (int y = 0; y < 7; y++)
			world.setBlockState(origin.add(x, y, 5), Blocks.BEDROCK.getDefaultState());
		var owner = context.createMockCreativeServerPlayerInWorld();
		var head = new ThrownHeadEntity(world, owner, HeadType.IRON_GOLEM, HeadType.ThrowStyle.GOLEM_ROLL);
		head.setPosition(origin.getX(), origin.getY(), origin.getZ());
		head.setVelocity(0, 0, 1);
		world.spawnEntity(head);
		var knocked = new ThrownHeadEntity(world, owner, HeadType.IRON_GOLEM, HeadType.ThrowStyle.KNOCK_OFF);
		knocked.setPosition(origin.getX(), origin.getY() + 8, origin.getZ());
		knocked.setVelocity(0, 0, 0.1);
		world.spawnEntity(knocked);
		context.waitAndRun(3, () -> context.assertTrue(!knocked.isGiantRoller() && knocked.getWidth() == 0.5F,
			"Beheading knock-off must not activate the roller"));
		context.waitAndRun(35, () -> {
			context.assertTrue(head.isRemoved() && head.getZ() < origin.getZ() + 2.6, "Bedrock must block and end rolling");
			context.assertTrue(world.getBlockState(origin.add(0, 0, 5)).isOf(Blocks.BEDROCK), "Roller must not erase bedrock");
			knocked.discard();
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 10)
	public void throwStartsAheadEvenWhenLookingDown(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var origin = context.getAbsolutePos(new BlockPos(5, 100, 5));
		player.refreshPositionAndAngles(origin.getX(), origin.getY(), origin.getZ(), 45, 90);
		player.setHeadYaw(45);
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,
			com.nakami.mcheadfunction.head.HeadItems.item(HeadType.IRON_GOLEM).getDefaultStack());
		com.nakami.mcheadfunction.throwing.ThrowHandler.throwHeldHead(player);
		var head = context.getWorld().getEntitiesByClass(ThrownHeadEntity.class, player.getBoundingBox().expand(8),
			e -> e.getOwner() == player).getFirst();
		context.assertTrue(Math.abs(head.getPos().subtract(player.getPos()).horizontalLength() - 4.5) < 0.01,
			"Throw must spawn ahead even with vertical aim");
		context.waitAndRun(2, () -> {
			context.assertFalse(head.getBoundingBox().intersects(player.getBoundingBox()), "Expanded head must not contain player");
			context.assertTrue(ThrownHeadEntity.GOLEM_SCALE == 8, "Head scale must be eight");
			head.discard();
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void descentAcceleratesAndKeepsMomentumAfterReload(TestContext context) {
		var world = context.getWorld();
		var origin = context.getAbsolutePos(new BlockPos(5, 140, 5));
		for (int x = -4; x <= 4; x++) for (int z = -4; z <= 38; z++) {
			int height = -Math.max(0, z / 4);
			world.setBlockState(origin.add(x, height - 1, z), Blocks.STONE.getDefaultState());
		}
		for (int z = -1; z <= 3; z++) world.setChunkForced(origin.getX() >> 4, (origin.getZ() >> 4) + z, true);
		var head = new ThrownHeadEntity(world, context.createMockCreativeServerPlayerInWorld(), HeadType.IRON_GOLEM, HeadType.ThrowStyle.GOLEM_ROLL);
		head.setPosition(origin.getX(), origin.getY(), origin.getZ());
		head.setVelocity(0, 0, 1);
		world.spawnEntity(head);
		context.waitAndRun(65, () -> {
			context.assertTrue(head.getY() < origin.getY() - 1 && head.getVelocity().horizontalLength() > 0.25,
				"Descending terraces must accelerate rolling beyond base speed: " + head.getVelocity());
			var nbt = new net.minecraft.nbt.NbtCompound();
			head.writeNbt(nbt);
			var restored = new ThrownHeadEntity(com.nakami.mcheadfunction.entity.ModEntities.THROWN_HEAD, world);
			restored.readNbt(nbt);
			world.spawnEntity(restored);
			head.discard();
			context.waitAndRun(2, () -> {
				context.assertTrue(restored.getVelocity().horizontalLength() > 0.25, "Reload must retain slope momentum");
				restored.discard();
				for (int z = -1; z <= 3; z++) world.setChunkForced(origin.getX() >> 4, (origin.getZ() >> 4) + z, false);
				context.complete();
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 110)
	public void highLandingMakesBoundedCraterButSmallDropsDoNot(TestContext context) {
		var world = context.getWorld();
		var origin = context.getAbsolutePos(new BlockPos(5, 200, 5));
		for (int x = -8; x <= 8; x++) for (int z = -5; z <= 24; z++) for (int y = -6; y < 0; y++)
			world.setBlockState(origin.add(x, y, z), (y == -6 ? Blocks.BEDROCK : Blocks.STONE).getDefaultState());
		for (int z = -1; z <= 2; z++) world.setChunkForced(origin.getX() >> 4, (origin.getZ() >> 4) + z, true);
		var owner = context.createMockCreativeServerPlayerInWorld();
		var small = new ThrownHeadEntity(world, owner, HeadType.IRON_GOLEM, HeadType.ThrowStyle.GOLEM_ROLL);
		small.setPosition(origin.getX(), origin.getY() + 1, origin.getZ());
		small.setVelocity(0, 0, 1);
		world.spawnEntity(small);
		context.waitAndRun(15, () -> {
			context.assertTrue(world.getBlockState(origin.add(0, -1, 2)).isOf(Blocks.STONE), "Ordinary drop must not excavate ground");
			small.discard();
			var high = new ThrownHeadEntity(world, owner, HeadType.IRON_GOLEM, HeadType.ThrowStyle.GOLEM_ROLL);
			high.setPosition(origin.getX(), origin.getY() + 14, origin.getZ());
			high.setVelocity(0, 0, 1);
			world.spawnEntity(high);
			context.waitAndRun(55, () -> {
				int holes = 0, deep = 0;
				for (int x = -7; x <= 7; x++) for (int z = 0; z <= 22; z++) {
					if (world.getBlockState(origin.add(x, -1, z)).isAir()) holes++;
					if (world.getBlockState(origin.add(x, -3, z)).isAir()) deep++;
				}
				context.assertTrue(holes > 30 && holes < 180 && deep > 0, "High landing must dig a wide, bounded deep crater: " + holes + "/" + deep);
				context.assertTrue(world.getBlockState(origin.add(0, -6, 8)).isOf(Blocks.BEDROCK), "Crater must preserve bedrock");
				high.discard();
				for (int z = -1; z <= 2; z++) world.setChunkForced(origin.getX() >> 4, (origin.getZ() >> 4) + z, false);
				context.complete();
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 560)
	public void longFreeFallPreservesGroundRollingBudget(TestContext context) {
		var world = context.getWorld();
		var origin = context.getAbsolutePos(new BlockPos(5, 260, 5));
		// Unbreakable landing surface isolates the rolling budget from secondary crater drops.
		for (int x = -4; x <= 4; x++) for (int z = -4; z <= 125; z++)
			world.setBlockState(origin.add(x, -1, z), Blocks.BEDROCK.getDefaultState());
		for (int z = -1; z <= 9; z++) world.setChunkForced(origin.getX() >> 4, (origin.getZ() >> 4) + z, true);
		var head = new ThrownHeadEntity(world, context.createMockCreativeServerPlayerInWorld(), HeadType.IRON_GOLEM, HeadType.ThrowStyle.GOLEM_ROLL);
		head.setPosition(origin.getX(), origin.getY() + 350, origin.getZ());
		head.setVelocity(0, 0, 0.2);
		// Also reproduce a save loaded after a long flight, just before the former age limit.
		var saved = new net.minecraft.nbt.NbtCompound();
		head.writeNbt(saved);
		saved.putBoolean("active", true);
		saved.putInt("activeTicks", 599);
		head.readNbt(saved);
		world.spawnEntity(head);
		context.waitAndRun(220, () -> {
			context.assertTrue(!head.isRemoved() && !head.isOnGround(), "Long free fall must not expire the head");
			var data = new net.minecraft.nbt.NbtCompound();
			head.writeNbt(data);
			context.assertTrue(data.getDouble("rollTraveled") == 0 && data.getInt("rollTicks") == 0,
				"Airborne time and drift must not spend any ground rolling budget");
		});
		context.waitAndRun(340, () -> {
			context.assertTrue(!head.isRemoved() && head.isOnGround(), "Head must land and continue rolling after a long fall");
			var data = new net.minecraft.nbt.NbtCompound();
			head.writeNbt(data);
			context.assertTrue(data.getDouble("rollTraveled") > 2 && data.getDouble("rollTraveled") < 30, "Only post-landing travel is counted");
		});
		context.waitAndRun(535, () -> {
			context.assertTrue(head.isRemoved(), "Head must still recycle when ground rolling budget is exhausted");
			var data = new net.minecraft.nbt.NbtCompound();
			head.writeNbt(data);
			context.assertTrue(Math.abs(data.getDouble("rollTraveled") - 40) < 0.01, "Full forty-block ground journey must be preserved");
			for (int z = -1; z <= 9; z++) world.setChunkForced(origin.getX() >> 4, (origin.getZ() >> 4) + z, false);
			context.complete();
		});
	}
}
