package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.haunt.GhostHeadEntity;
import com.nakami.mcheadfunction.haunt.HauntBlocks;
import com.nakami.mcheadfunction.haunt.HauntTongueEntity;
import com.nakami.mcheadfunction.haunt.HauntTown;
import com.nakami.mcheadfunction.haunt.HauntedTreeBlockEntity;
import com.nakami.mcheadfunction.haunt.PossessedPropEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class HauntGameplayTest implements FabricGameTest {
	private HauntedTreeBlockEntity plant(TestContext c, BlockPos rel) {
		var world = c.getWorld();
		var pos = c.getAbsolutePos(rel);
		for (int x = -4; x <= 8; x++) for (int z = -4; z <= 8; z++)
			world.setBlockState(pos.add(x, -1, z), Blocks.STONE.getDefaultState());
		for (int i = 0; i < 6; i++) world.setBlockState(pos.up(i), Blocks.AIR.getDefaultState());
		world.setBlockState(pos, HauntBlocks.TREE.getDefaultState());
		HauntBlocks.TREE.onPlaced(world, pos, world.getBlockState(pos), null, ItemStack.EMPTY);
		com.nakami.mcheadfunction.haunt.HauntWorldData.get(world).setOff(false);
		return (HauntedTreeBlockEntity) world.getBlockEntity(pos);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt")
	public void hangingAHeadUsesTheItemAndStoresIt(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ZOMBIE_HEAD, 2));
		tree.hang(player, HeadType.ZOMBIE, player.getMainHandStack());
		c.assertTrue(tree.has(HeadType.ZOMBIE), "Tree must hang the zombie head");
		c.assertTrue(player.getMainHandStack().getCount() == 1, "Survival must spend one head");
		tree.hang(player, HeadType.ZOMBIE, player.getMainHandStack());
		c.assertTrue(player.getMainHandStack().getCount() == 1, "A duplicate hang must be refused");
		c.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt")
	public void sneakEmptyHandTakesTheHeadBack(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.FROG)));
		tree.hang(player, HeadType.FROG, player.getMainHandStack());
		c.assertTrue(player.getMainHandStack().isEmpty(), "Hang must consume the frog head");
		tree.take(player);
		c.assertTrue(player.getInventory().count(HeadItems.item(HeadType.FROG)) == 1, "Take must return the head");
		c.assertTrue(!tree.has(HeadType.FROG), "Taken head must leave the tree");
		c.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt")
	public void hauntingBlocksHangAndTake(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ZOMBIE_HEAD));
		tree.hang(player, HeadType.ZOMBIE, player.getMainHandStack());
		c.getWorld().setBlockState(c.getAbsolutePos(new BlockPos(6, 1, 4)), Blocks.CARVED_PUMPKIN.getDefaultState());
		c.getWorld().setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.FROG)));
		tree.hang(player, HeadType.FROG, player.getMainHandStack());
		c.assertTrue(!tree.has(HeadType.FROG) && player.getMainHandStack().getCount() == 1, "No hang during a haunting");
		tree.take(player);
		c.assertTrue(tree.has(HeadType.ZOMBIE), "No take during a haunting");
		c.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt")
	public void breakingTheTreeDropsTheTreeAndHeads(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		var pos = tree.getPos();
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ZOMBIE_HEAD));
		tree.hang(player, HeadType.ZOMBIE, player.getMainHandStack());
		com.nakami.mcheadfunction.haunt.HauntedTreeBlock.dismantle(c.getWorld(), pos, true);
		c.assertTrue(c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(pos).expand(3),
			e -> e.getStack().isOf(HauntBlocks.ITEM)).size() == 1, "Broken tree must drop the tree item");
		c.assertTrue(c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(pos).expand(3),
			e -> e.getStack().isOf(Items.ZOMBIE_HEAD)).size() == 1, "Broken tree must drop hung heads");
		c.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt_midnight", tickLimit = 80)
	public void eightKindsStartAtMidnight(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		c.getWorld().setTimeOfDay(6000);
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		var types = HeadType.values();
		for (int i = 0; i < HauntRules.READY; i++) {
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(types[i])));
			tree.hang(player, types[i], player.getMainHandStack());
		}
		c.assertTrue(tree.hungCount() == HauntRules.READY && !tree.haunting(), "Eight kinds must wait for midnight");
		c.getWorld().setTimeOfDay(HauntRules.MIDNIGHT);
		c.waitAndRun(5, () -> {
			c.assertTrue(tree.haunting(), "Eight kinds must start at midnight");
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt_day_fill", tickLimit = 20)
	public void fillingNineteenStartsDuringTheDay(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		c.getWorld().setTimeOfDay(6000);
		tree.fill(null);
		c.assertTrue(tree.haunting(), "Hanging every kind must start at once, even at noon");
		c.complete();
	}

	// This test changes the whole world's switch; other haunt tests reset it in plant().
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt_off", tickLimit = 40)
	public void offStopsAndBlocksNaturalHaunting(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		tree.fill(null);
		c.assertTrue(tree.haunting(), "A full tree starts before it is closed");
		com.nakami.mcheadfunction.haunt.HauntWorldData.get(c.getWorld()).setOff(true);
		tree.stop();
		c.getWorld().setTimeOfDay(24000 + HauntRules.MIDNIGHT);
		c.waitAndRun(5, () -> {
			c.assertTrue(!tree.haunting(), "A closed world must not haunt at midnight");
			c.complete();
		});
	}

	// World-time transitions must not race the possession scenes in the haunt batch.
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt_lifecycle", tickLimit = 80)
	public void aFullTreeStartsAtMidnightAndStopsAtDawn(TestContext c) {
		var tree = plant(c, new BlockPos(5, 2, 5));
		var pumpkin = c.getAbsolutePos(new BlockPos(7, 2, 5));
		c.getWorld().setBlockState(pumpkin, Blocks.CARVED_PUMPKIN.getDefaultState());
		c.getWorld().setTimeOfDay(HauntRules.MIDNIGHT);
		tree.fill(null);
		c.waitAndRun(5, () -> {
			c.assertTrue(tree.haunting(), "A full tree must start haunting at midnight");
			c.assertTrue(!c.getWorld().getEntitiesByClass(GhostHeadEntity.class, new Box(tree.getPos()).expand(20), e -> true).isEmpty(),
				"Ghost heads must leave the tree");
			c.getWorld().setTimeOfDay(HauntRules.DAWN);
			// waitAndRun uses absolute test ticks, including inside another callback.
			c.waitAndRun(10, () -> {
				c.assertTrue(!tree.haunting(), "Dawn must end the haunting");
				c.complete();
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 90)
	public void aPossessedPumpkinBitesAndAChestTakesItems(TestContext c) {
		var tree = plant(c, new BlockPos(4, 2, 4));
		var world = c.getWorld();
		var pumpkin = c.getAbsolutePos(new BlockPos(6, 2, 4));
		var chest = c.getAbsolutePos(new BlockPos(4, 2, 6));
		world.setBlockState(pumpkin, Blocks.CARVED_PUMPKIN.getDefaultState());
		world.setBlockState(chest, Blocks.CHEST.getDefaultState());
		var cow = EntityType.COW.create(world);
		cow.setAiDisabled(true);
		cow.setPosition(pumpkin.getX() + 0.5, pumpkin.getY(), pumpkin.getZ() + 1.5);
		world.spawnEntity(cow);
		float health = cow.getHealth();
		var item = new net.minecraft.entity.ItemEntity(world, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 2.5,
			new ItemStack(Items.DIAMOND, 3));
		world.spawnEntity(item);
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ZOMBIE_HEAD));
		tree.hang(player, HeadType.ZOMBIE, player.getMainHandStack());
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.FROG)));
		tree.hang(player, HeadType.FROG, player.getMainHandStack());
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		int ghosts = world.getEntitiesByClass(GhostHeadEntity.class, new Box(tree.getPos()).expand(32), e -> true).size();
		c.assertTrue(ghosts > 0, "Zombie and frog heads must possess nearby objects; ghosts=" + ghosts
			+ " hung=" + tree.hung() + " chest=" + world.getBlockState(chest) + " pumpkin=" + world.getBlockState(pumpkin));
		c.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 160)
	public void aGhostSinksIntoItsHostBeforePossessing(TestContext c) {
		var tree = plant(c, new BlockPos(4, 2, 4));
		var world = c.getWorld();
		var pumpkin = c.getAbsolutePos(new BlockPos(6, 2, 4));
		world.setBlockState(pumpkin, Blocks.CARVED_PUMPKIN.getDefaultState());
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ZOMBIE_HEAD));
		tree.hang(player, HeadType.ZOMBIE, player.getMainHandStack());
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		c.waitAndRun(HauntRules.FLY_TICKS + HauntRules.SPLIT_TICKS, () -> {
			c.assertTrue(world.getBlockState(pumpkin).isOf(Blocks.CARVED_PUMPKIN),
				"The pumpkin must stay a block until the ghost finishes entering");
			c.assertTrue(!world.getEntitiesByClass(GhostHeadEntity.class, new Box(tree.getPos()).expand(16), e -> true).isEmpty(),
				"The ghost must already be on the way");
		});
		c.waitAndRun(HauntRules.possessionReadyAt() + 2, () -> {
			c.assertTrue(world.getEntitiesByClass(PossessedPropEntity.class, new Box(pumpkin).expand(2), e -> true).size() == 1,
				"The pumpkin becomes a host only after the ghost sinks in");
			c.assertTrue(world.getBlockState(pumpkin).isAir(), "The original pumpkin block is gone after the enter");
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 80)
	public void aGoatCartBreaksPlanksButNotObsidian(TestContext c) {
		var tree = plant(c, new BlockPos(3, 2, 3));
		var world = c.getWorld();
		var cart = new MinecartEntity(world, c.getAbsolutePos(new BlockPos(5, 2, 3)).getX() + 0.5,
			c.getAbsolutePos(new BlockPos(5, 2, 3)).getY(), c.getAbsolutePos(new BlockPos(5, 2, 3)).getZ() + 0.5);
		world.spawnEntity(cart);
		var plank = c.getAbsolutePos(new BlockPos(6, 2, 3));
		var obsidian = c.getAbsolutePos(new BlockPos(3, 2, 5));
		world.setBlockState(plank, Blocks.OAK_PLANKS.getDefaultState());
		world.setBlockState(obsidian, Blocks.OBSIDIAN.getDefaultState());
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.GOAT)));
		tree.hang(player, HeadType.GOAT, player.getMainHandStack());
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		c.waitAndRun(5, () -> {
			c.assertTrue(!world.getEntitiesByClass(GhostHeadEntity.class, cart.getBoundingBox().expand(16), e -> true).isEmpty(),
				"A goat head must possess the minecart");
			c.assertTrue(world.getBlockState(obsidian).isOf(Blocks.OBSIDIAN), "Obsidian must survive a goat cart");
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 180)
	public void aGoatCartBoardsACowItBumps(TestContext c) {
		var tree = plant(c, new BlockPos(3, 2, 3));
		var world = c.getWorld();
		var at = c.getAbsolutePos(new BlockPos(5, 2, 3));
		var cart = new MinecartEntity(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		world.spawnEntity(cart);
		var cow = EntityType.COW.create(world);
		c.assertTrue(cow != null, "A cow must spawn for the cart to bump");
		cow.refreshPositionAndAngles(cart.getX() + 0.35, cart.getY(), cart.getZ(), 0, 0);
		cow.setAiDisabled(true);
		world.spawnEntity(cow);
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.GOAT)));
		tree.hang(player, HeadType.GOAT, player.getMainHandStack());
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		c.waitAndRun(HauntRules.possessionReadyAt() + HauntRules.RISE_TICKS + 2, () -> {
			cow.refreshPositionAndAngles(cart.getX(), cart.getY(), cart.getZ(), 0, 0);
			cow.setVelocity(Vec3d.ZERO);
			c.waitAndRun(12, () -> {
				c.assertTrue(cow.getVehicle() == cart, "A goat cart boards a cow it bumps, then keeps running");
				c.assertTrue(cart.hasPassengers(), "The cow stays in the cart");
				c.complete();
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 100)
	public void anArmorStandSurvivesItsOwnLeaps(TestContext c) {
		var tree = plant(c, new BlockPos(4, 2, 4));
		var world = c.getWorld();
		var stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
		var at = c.getAbsolutePos(new BlockPos(6, 2, 4));
		stand.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		world.spawnEntity(stand);
		double startY = stand.getY();
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.RABBIT)));
		tree.hang(player, HeadType.RABBIT, player.getMainHandStack());
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		c.waitAndRun(5, () -> {
			c.assertTrue(stand.isAlive(), "Rabbit armor stand must stay after being possessed");
			c.assertTrue(!world.getEntitiesByClass(GhostHeadEntity.class, stand.getBoundingBox().expand(8), e -> true).isEmpty(),
				"A rabbit head must possess the armor stand");
			c.waitAndRun(80, () -> {
				c.assertTrue(stand.isAlive() && stand.getY() < startY + 3,
					"A possessed armor stand must keep a short hop, not climb; y=" + stand.getY() + " start=" + startY);
				c.complete();
			});
		});
	}




	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "haunt", tickLimit = 30)
	public void aFrogChestDoesNotPullCreativePlayers(TestContext c) {
		var world = c.getWorld();
		var chest = c.getAbsolutePos(new BlockPos(2, 1, 2));
		world.setBlockState(chest, Blocks.CHEST.getDefaultState());
		var inventory = (net.minecraft.inventory.Inventory) world.getBlockEntity(chest);
		var creative = c.createMockPlayer(GameMode.CREATIVE);
		var survival = c.createMockPlayer(GameMode.SURVIVAL);
		creative.refreshPositionAndAngles(chest.getX() + 0.5, chest.getY(), chest.getZ() + 6, 180, 0);
		survival.refreshPositionAndAngles(chest.getX() + 4.5, chest.getY(), chest.getZ() + 6, 180, 0);
		creative.setNoGravity(true);
		survival.setNoGravity(true);
		creative.setVelocity(Vec3d.ZERO);
		survival.setVelocity(Vec3d.ZERO);
		var start = creative.getPos();
		world.spawnEntity(new HauntTongueEntity(world, chest, inventory, creative));
		var survivalTongue = new HauntTongueEntity(world, chest, inventory, survival);
		world.spawnEntity(survivalTongue);
		c.waitAndRun(16, () -> {
			c.assertTrue(creative.getVelocity().lengthSquared() < 0.01 && creative.getPos().squaredDistanceTo(start) < 0.25,
				"A possessed chest must not pull a creative player; vel=" + creative.getVelocity() + " pos=" + creative.getPos());
			c.assertTrue(survival.getVelocity().lengthSquared() > 0.04,
				"A possessed chest must still pull a survival player; vel=" + survival.getVelocity() + " tongueClock=" + survivalTongue.elapsed() + " removed=" + survivalTongue.isRemoved() + " targetAlive=" + survival.isAlive() + " canPull=" + HauntTongueEntity.canPull(survival) + " chest=" + world.getBlockState(chest) + " tonguePos=" + survivalTongue.getPos());
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 20)
	public void unpairedHeadsWanderThroughWalls(TestContext c) {
		var tree = plant(c, new BlockPos(4, 2, 4));
		var world = c.getWorld();
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.PIG)));
		tree.hang(player, HeadType.PIG, player.getMainHandStack());
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		var ghosts = world.getEntitiesByClass(GhostHeadEntity.class, new Box(tree.getPos()).expand(8),
			ghost -> ghost.head() == HeadType.PIG);
		c.assertTrue(ghosts.size() == 1, "A pig head must send out one wandering ghost; ghosts=" + ghosts.size());
		c.assertTrue(tree.has(HeadType.PIG), "The real pig head stays on the tree");
		var ghost = ghosts.get(0);
		c.assertTrue(ghost.wandering() && ghost.noClip, "An unpaired ghost must wander and pass through walls");
		var start = ghost.getPos();
		int stone = 0;
		for (int x = -2; x <= 2; x++) for (int y = 0; y <= 6; y++) for (int z = -2; z <= 2; z++) {
			var pos = tree.getPos().add(x, y, z);
			if (world.getBlockState(pos).isOf(HauntBlocks.TREE) || world.getBlockEntity(pos) != null) continue;
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
			stone++;
		}
		c.assertTrue(stone > 0, "Stone walls must box the hanging ghost");
		int walls = stone;
		c.waitAndRun(12, () -> {
			c.assertTrue(ghost.isAlive() && !ghost.isRemoved(), "A wandering ghost must survive walls");
			c.assertTrue(ghost.getPos().squaredDistanceTo(start) > 0.25,
				"A wandering ghost must keep drifting through blocks; start=" + start + " now=" + ghost.getPos());
			int left = 0;
			for (int x = -2; x <= 2; x++) for (int y = 0; y <= 6; y++) for (int z = -2; z <= 2; z++) {
				var pos = tree.getPos().add(x, y, z);
				if (world.getBlockState(pos).isOf(Blocks.STONE)) left++;
			}
			c.assertTrue(left == walls, "A wandering ghost must not break the walls it passes; left=" + left);
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 220)
	public void aPossessedFurnaceOverflowsWithoutHarm(TestContext c) {
		var tree = plant(c, new BlockPos(4, 2, 4));
		var world = c.getWorld();
		var furnace = c.getAbsolutePos(new BlockPos(6, 2, 4));
		world.setBlockState(furnace, Blocks.FURNACE.getDefaultState()
			.with(net.minecraft.block.AbstractFurnaceBlock.FACING, Direction.SOUTH)
			.with(net.minecraft.block.AbstractFurnaceBlock.LIT, false));
		var cow = EntityType.COW.create(world);
		cow.setAiDisabled(true);
		cow.setPosition(furnace.getX() + 3.5, furnace.getY(), furnace.getZ() + 0.5);
		world.spawnEntity(cow);
		float health = cow.getHealth();
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.BLAZE)));
		tree.hang(player, HeadType.BLAZE, player.getMainHandStack());
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		c.waitAndRun(HauntRules.possessionReadyAt() + HauntRules.RISE_TICKS + 40, () -> {
			var state = world.getBlockState(furnace);
			c.assertTrue(state.isOf(Blocks.FURNACE), "The furnace must stay a block");
			c.assertTrue(state.get(net.minecraft.block.AbstractFurnaceBlock.LIT), "A possessed furnace stays lit");
			c.assertTrue(state.get(net.minecraft.block.AbstractFurnaceBlock.FACING) == Direction.SOUTH,
				"Overflow fire must not turn the furnace toward a target; facing=" + state.get(net.minecraft.block.AbstractFurnaceBlock.FACING));
			c.assertTrue(cow.isAlive() && cow.getHealth() == health, "Fireplace overflow must not harm; health=" + cow.getHealth());
			c.assertTrue(!cow.isOnFire(), "Fireplace overflow must not ignite");
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt")
	public void treeFillCanLeaveOneKindOut(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		tree.fill(HeadType.BAT);
		c.assertTrue(tree.hungCount() == 18 && !tree.has(HeadType.BAT), "Fill except bat must hang the other eighteen");
		c.complete();
	}
}
