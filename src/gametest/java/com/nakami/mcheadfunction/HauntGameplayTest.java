package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.haunt.GhostHeadEntity;
import com.nakami.mcheadfunction.haunt.HauntBlocks;
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

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 80)
	public void aFullTreeStartsAtMidnightAndStopsAtDawn(TestContext c) {
		var tree = plant(c, new BlockPos(5, 2, 5));
		var pumpkin = c.getAbsolutePos(new BlockPos(7, 2, 5));
		c.getWorld().setBlockState(pumpkin, Blocks.CARVED_PUMPKIN.getDefaultState());
		tree.fill(null);
		c.getWorld().setTimeOfDay(HauntRules.MIDNIGHT);
		c.waitAndRun(5, () -> {
			c.assertTrue(tree.haunting(), "A full tree must start haunting at midnight");
			c.assertTrue(!c.getWorld().getEntitiesByClass(GhostHeadEntity.class, new Box(tree.getPos()).expand(20), e -> true).isEmpty(),
				"Ghost heads must leave the tree");
			c.getWorld().setTimeOfDay(HauntRules.DAWN);
			c.waitAndRun(5, () -> {
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

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 40)
	public void anArmorStandSurvivesItsOwnLeaps(TestContext c) {
		var tree = plant(c, new BlockPos(4, 2, 4));
		var world = c.getWorld();
		var stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
		var at = c.getAbsolutePos(new BlockPos(6, 2, 4));
		stand.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		world.spawnEntity(stand);
		var player = c.createMockPlayer(GameMode.SURVIVAL);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.RABBIT)));
		tree.hang(player, HeadType.RABBIT, player.getMainHandStack());
		world.setTimeOfDay(HauntRules.MIDNIGHT);
		tree.start(true);
		c.waitAndRun(5, () -> {
			c.assertTrue(stand.isAlive(), "Rabbit armor stand must stay after being possessed");
			c.assertTrue(!world.getEntitiesByClass(GhostHeadEntity.class, stand.getBoundingBox().expand(8), e -> true).isEmpty(),
				"A rabbit head must possess the armor stand");
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt", tickLimit = 10)
	public void townBuildPlacesEveryHostKind(TestContext c) {
		var origin = c.getAbsolutePos(new BlockPos(16, 3, 16));
		HauntTown.build(c.getWorld(), origin);
		c.assertTrue(c.getWorld().getBlockEntity(origin) instanceof HauntedTreeBlockEntity, "Town must plant a haunted tree");
		int pumpkins = 0, chests = 0, lanterns = 0;
		for (var pos : BlockPos.iterate(origin.add(-20, -1, -20), origin.add(20, 6, 20))) {
			var state = c.getWorld().getBlockState(pos);
			if (state.isOf(Blocks.CARVED_PUMPKIN) || state.isOf(Blocks.JACK_O_LANTERN)) pumpkins++;
			if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)) chests++;
			if (state.isOf(Blocks.LANTERN) || state.isOf(Blocks.SOUL_LANTERN)) lanterns++;
		}
		c.assertTrue(pumpkins >= 7 && chests >= 4 && lanterns >= 7, "Town must beat the default possession counts: " + pumpkins + "/" + chests + "/" + lanterns);
		c.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "haunt")
	public void treeFillCanLeaveOneKindOut(TestContext c) {
		var tree = plant(c, new BlockPos(4, 1, 4));
		tree.fill(HeadType.BAT);
		c.assertTrue(tree.hungCount() == 18 && !tree.has(HeadType.BAT), "Fill except bat must hang the other eighteen");
		c.complete();
	}
}
