package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.haunt.HauntTongueEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public final class ChestMealGameplayTest implements FabricGameTest {
	private BlockPos chest(TestContext c) {
		var pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
		for (var floor : BlockPos.iterate(pos.add(-2, -1, -2), pos.add(2, -1, 16))) c.getWorld().setBlockState(floor, Blocks.STONE.getDefaultState());
		c.getWorld().setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
		return pos;
	}
	private int count(Inventory inventory, Item item) {
		int count = 0;
		for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(item)) count += inventory.getStack(i).getCount();
		return count;
	}

	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "chest-meal", tickLimit = 120)
	public void aDistantSheepIsPulledThenSwallowedForItsLoot(TestContext c) {
		var pos = chest(c);
		var inventory = (Inventory) c.getWorld().getBlockEntity(pos);
		var sheep = EntityType.SHEEP.create(c.getWorld());
		sheep.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
		sheep.setPosition(Vec3d.ofBottomCenter(pos).add(0, 0, 12));
		c.getWorld().spawnEntity(sheep);
		// A recent player hit would normally allow XP; swallowing must keep only the items.
		sheep.damage(c.getWorld().getDamageSources().playerAttack(c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL)), 1);
		sheep.setVelocity(Vec3d.ZERO);
		var tongue = new HauntTongueEntity(c.getWorld(), pos, inventory, sheep);
		c.getWorld().spawnEntity(tongue);
		c.waitAndRun(16, () -> {
			c.assertTrue(sheep.isAlive() && !tongue.isRemoved(), "Distant prey must stay latched past the old short return");
			c.assertTrue(sheep.getZ() < pos.getZ() + 10, "Prey must keep approaching the mouth; pos=" + sheep.getPos() + " chest=" + pos + " velocity=" + sheep.getVelocity());
			c.assertTrue(inventory.isEmpty(), "Loot must wait until the prey is swallowed");
		});
		c.waitAndRun(100, () -> {
			c.assertTrue(sheep.isRemoved() && tongue.isRemoved(), "Meal must remove prey and finish chewing");
			c.assertTrue(count(inventory, Items.WHITE_WOOL) == 1, "Vanilla sheep wool must enter chest exactly once");
			c.assertTrue(count(inventory, Items.MUTTON) >= 1, "Vanilla meat must enter chest");
			c.assertTrue(c.getWorld().getEntitiesByClass(ExperienceOrbEntity.class, new Box(pos).expand(16), e -> true).isEmpty(), "Only items remain after swallowing");
			c.complete();
		});
	}

	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "chest-meal", tickLimit = 90)
	public void aFullChestSpitsOutMealLoot(TestContext c) {
		var pos = chest(c);
		var inventory = (Inventory) c.getWorld().getBlockEntity(pos);
		for (int i = 0; i < inventory.size(); i++) inventory.setStack(i, new ItemStack(Items.STONE, 64));
		var sheep = EntityType.SHEEP.create(c.getWorld());
		sheep.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
		sheep.setPosition(Vec3d.ofBottomCenter(pos).add(0, 0, 2));
		c.getWorld().spawnEntity(sheep);
		c.getWorld().spawnEntity(new HauntTongueEntity(c.getWorld(), pos, inventory, sheep));
		c.waitAndRun(70, () -> {
			c.assertTrue(sheep.isRemoved(), "A full chest still eats prey");
			int wool = c.getWorld().getEntitiesByClass(ItemEntity.class, new Box(pos).expand(8), e -> e.getStack().isOf(Items.WHITE_WOOL)).stream().mapToInt(e -> e.getStack().getCount()).sum();
			c.assertTrue(wool == 1 && count(inventory, Items.STONE) == 27 * 64, "Overflow must preserve loot and existing contents");
			c.complete();
		});
	}

	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "chest-meal", tickLimit = 30)
	public void itemReturnMergesStacksWithoutLoss(TestContext c) {
		var pos = chest(c);
		var inventory = (Inventory) c.getWorld().getBlockEntity(pos);
		inventory.setStack(0, new ItemStack(Items.DIAMOND, 62));
		var item = new ItemEntity(c.getWorld(), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 4, new ItemStack(Items.DIAMOND, 5));
		item.setNoGravity(true);
		c.getWorld().spawnEntity(item);
		c.getWorld().spawnEntity(new HauntTongueEntity(c.getWorld(), pos, inventory, item));
		c.waitAndRun(10, () -> c.assertTrue(count(inventory, Items.DIAMOND) == 62, "Captured items must travel back before delivery"));
		c.waitAndRun(20, () -> {
			c.assertTrue(count(inventory, Items.DIAMOND) == 67 && inventory.getStack(0).getCount() == 64, "Item return must merge and preserve all five diamonds");
			c.complete();
		});
	}

	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "chest-meal", tickLimit = 35)
	public void removingChestReleasesCapturedItems(TestContext c) {
		var pos = chest(c);
		var inventory = (Inventory) c.getWorld().getBlockEntity(pos);
		var item = new ItemEntity(c.getWorld(), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 4, new ItemStack(Items.DIAMOND, 5));
		item.setNoGravity(true);
		c.getWorld().spawnEntity(item);
		var tongue = new HauntTongueEntity(c.getWorld(), pos, inventory, item);
		c.getWorld().spawnEntity(tongue);
		c.waitAndRun(9, () -> c.getWorld().removeBlock(pos, false));
		c.waitAndRun(20, () -> {
			int diamonds = c.getWorld().getEntitiesByClass(ItemEntity.class, new Box(pos).expand(8), e -> e.getStack().isOf(Items.DIAMOND)).stream().mapToInt(e -> e.getStack().getCount()).sum();
			c.assertTrue(diamonds == 5 && tongue.isRemoved(), "Removing host must release captured items exactly once");
			c.complete();
		});
	}
	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "chest-meal", tickLimit = 45)
	public void reloadingMidSwallowReleasesLivingPrey(TestContext c) {
		var pos = chest(c);
		var inventory = (Inventory) c.getWorld().getBlockEntity(pos);
		var sheep = EntityType.SHEEP.create(c.getWorld());
		sheep.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
		sheep.setHealth(1);
		sheep.setPosition(Vec3d.ofBottomCenter(pos).add(0, 0, 2));
		c.getWorld().spawnEntity(sheep);
		var tongue = new HauntTongueEntity(c.getWorld(), pos, inventory, sheep);
		c.getWorld().spawnEntity(tongue);
		c.waitAndRun(20, () -> {
			c.assertTrue(tongue.mealTime() >= 0 && tongue.mealTime() < HauntTongueEntity.SWALLOW, "Save during the visible swallowing phase");
			c.assertTrue(sheep.getHealth() == 1, "Swallow animation must not suffocate low-health prey");
			var saved = new net.minecraft.nbt.NbtCompound();
			tongue.writeNbt(saved);
			tongue.discard();
			var restored = new HauntTongueEntity(com.nakami.mcheadfunction.entity.ModEntities.HAUNT_TONGUE, c.getWorld());
			restored.readNbt(saved);
			restored.tick();
			c.assertTrue(restored.isRemoved() && sheep.isAlive() && !sheep.noClip, "Reload must safely release living prey and restore collision");
			c.assertTrue(!sheep.getBoundingBox().intersects(new Box(pos)), "Released prey must be outside the chest");
			c.assertTrue(inventory.isEmpty(), "Interrupted swallowing must not generate loot");
			c.complete();
		});
	}

	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "chest-meal", tickLimit = 30)
	public void reloadingCapturedItemsPreservesCargo(TestContext c) {
		var pos = chest(c);
		var inventory = (Inventory) c.getWorld().getBlockEntity(pos);
		var item = new ItemEntity(c.getWorld(), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 4, new ItemStack(Items.DIAMOND, 5));
		item.setNoGravity(true);
		c.getWorld().spawnEntity(item);
		var tongue = new HauntTongueEntity(c.getWorld(), pos, inventory, item);
		c.getWorld().spawnEntity(tongue);
		c.waitAndRun(10, () -> {
			c.assertTrue(item.isRemoved() && inventory.isEmpty(), "Save with cargo traveling on the tongue");
			var saved = new net.minecraft.nbt.NbtCompound();
			tongue.writeNbt(saved);
			tongue.discard();
			var restored = new HauntTongueEntity(com.nakami.mcheadfunction.entity.ModEntities.HAUNT_TONGUE, c.getWorld());
			restored.readNbt(saved);
			c.getWorld().spawnEntity(restored);
		});
		c.waitAndRun(20, () -> {
			c.assertTrue(count(inventory, Items.DIAMOND) == 5, "Reload must preserve captured items exactly once");
			c.complete();
		});
	}

	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "chest-meal", tickLimit = 160)
	public void aFrogChestEatsPathBlocksBeforePullingPrey(TestContext c) {
		var pos = chest(c);
		var inventory = (Inventory) c.getWorld().getBlockEntity(pos);
		var first = pos.add(0, 0, 4);
		var second = pos.add(0, 0, 8);
		c.getWorld().setBlockState(first, Blocks.OAK_FENCE.getDefaultState());
		c.getWorld().setBlockState(second, Blocks.OAK_FENCE.getDefaultState());
		var sheep = EntityType.SHEEP.create(c.getWorld());
		sheep.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
		sheep.setPosition(Vec3d.ofBottomCenter(pos).add(0, 0, 12));
		c.getWorld().spawnEntity(sheep);
		var start = sheep.getPos();
		c.getWorld().spawnEntity(new HauntTongueEntity(c.getWorld(), pos, inventory, sheep));
		c.waitAndRun(9, () -> {
			c.assertTrue(c.getWorld().getBlockState(first).isAir(), "Nearest path block is eaten first; first=" + c.getWorld().getBlockState(first));
			c.assertTrue(c.getWorld().getBlockState(second).isOf(Blocks.OAK_FENCE), "Farther path block waits its turn");
			c.assertTrue(sheep.getPos().squaredDistanceTo(start) < 0.25,
				"Prey stays put until the path is clear; pos=" + sheep.getPos() + " start=" + start);
		});
		c.waitAndRun(150, () -> {
			c.assertTrue(c.getWorld().getBlockState(first).isAir() && c.getWorld().getBlockState(second).isAir(), "The whole path is eaten");
			c.assertTrue(sheep.isRemoved(), "After the path is clear the prey is pulled in and swallowed");
			c.assertTrue(count(inventory, Items.OAK_FENCE) == 2, "Eaten fences enter the chest");
			c.assertTrue(count(inventory, Items.WHITE_WOOL) == 1, "Prey loot still enters afterwards");
			c.complete();
		});
	}

	@GameTest(templateName = "mc_head_function:chest_meal_arena", batchId = "chest-meal", tickLimit = 40)
	public void aFrogChestDoesNotEatUnbreakablePathBlocks(TestContext c) {
		var pos = chest(c);
		var wall = pos.add(0, 0, 3);
		c.getWorld().setBlockState(wall, Blocks.BEDROCK.getDefaultState());
		var sheep = EntityType.SHEEP.create(c.getWorld());
		sheep.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
		sheep.setPosition(Vec3d.ofBottomCenter(pos).add(0, 0, 8));
		c.getWorld().spawnEntity(sheep);
		c.getWorld().spawnEntity(new HauntTongueEntity(c.getWorld(), pos, (Inventory) c.getWorld().getBlockEntity(pos), sheep));
		c.waitAndRun(24, () -> {
			c.assertTrue(c.getWorld().getBlockState(wall).isOf(Blocks.BEDROCK), "Bedrock stays");
			c.assertTrue(sheep.isAlive() && sheep.getZ() > wall.getZ() + 0.4, "Prey is not dragged through bedrock; pos=" + sheep.getPos());
			c.complete();
		});
	}

}
