package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.entity.FrogTongueEntity;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.wear.SkillHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;

public final class FrogGameplayTest implements FabricGameTest {
	private net.minecraft.server.network.ServerPlayerEntity aim(TestContext c) {
		var p = c.createMockCreativeServerPlayerInWorld();
		var pos = c.getAbsolutePos(new BlockPos(2, 1, 3));
		p.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.4, 0, 35);
		p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.FROG)));
		return p;
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bedrockSurvivesTongue(TestContext c) {
		c.setBlockState(2, 1, 3, Blocks.BEDROCK);
		var p = aim(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(14, () -> { c.expectBlock(Blocks.BEDROCK, new BlockPos(2, 1, 3)); c.complete(); });
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void changedTargetIsNotBroken(TestContext c) {
		c.setBlockState(2, 1, 3, Blocks.DIRT);
		var p = aim(c); SkillHandler.onSkill(p, true);
		c.setBlockState(2, 1, 3, Blocks.GOLD_BLOCK);
		c.waitAndRun(14, () -> { c.expectBlock(Blocks.GOLD_BLOCK, new BlockPos(2, 1, 3)); c.complete(); });
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void interruptionReleasesCargoExactlyOnce(TestContext c) {
		c.setBlockState(2, 1, 3, Blocks.CHEST);
		var pos = c.getAbsolutePos(new BlockPos(2, 1, 3));
		((net.minecraft.inventory.Inventory) c.getWorld().getBlockEntity(pos)).setStack(0, new ItemStack(Items.DIAMOND, 5));
		var p = aim(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(7, () -> p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY));
		c.waitAndRun(14, () -> {
			int loose = c.getWorld().getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(pos).expand(5), e -> e.getStack().isOf(Items.DIAMOND))
				.stream().mapToInt(e -> e.getStack().getCount()).sum();
			c.assertTrue(loose + p.getInventory().count(Items.DIAMOND) == 5, "Interrupted tongue must preserve exactly five diamonds");
			c.assertTrue(c.getWorld().getEntitiesByClass(FrogTongueEntity.class, p.getBoundingBox().expand(10), e -> e.owner() == p).isEmpty(), "Interrupted tongue must expire");
			c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void toolRequiredBlockReturnsNoLoot(TestContext c) {
		c.setBlockState(2, 1, 3, Blocks.STONE);
		var p = aim(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(14, () -> {
			c.expectBlock(Blocks.AIR, new BlockPos(2, 1, 3));
			c.assertTrue(p.getInventory().count(Items.STONE) + p.getInventory().count(Items.COBBLESTONE) == 0, "Tongue must use empty-hand drops");
			c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void droppedItemTravelsBeforeDelivery(TestContext c) {
		var p = aim(c); p.setPitch(0); p.setHeadYaw(0); p.setBodyYaw(0); p.setNoGravity(true);
		var item = new ItemEntity(c.getWorld(), p.getX(), p.getEyeY(), p.getZ() + 4, new ItemStack(Items.EMERALD, 3));
		item.setNoGravity(true); item.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
		c.getWorld().spawnEntity(item);
		SkillHandler.onSkill(p, true);
		c.assertTrue(!item.isRemoved(), "Item must remain until tongue contact");
		c.waitAndRun(7, () -> c.assertTrue(p.getInventory().count(Items.EMERALD) == 0, "Item must not arrive mid-return"));
		c.waitAndRun(14, () -> {
			c.assertTrue(p.getInventory().count(Items.EMERALD) == 3 && item.isRemoved(), "Tongue must deliver item stack once; count=" + p.getInventory().count(Items.EMERALD) + " item=" + item.getPos() + " player=" + p.getPos()); c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void livingTargetIsPulledAfterLatch(TestContext c) {
		var p = aim(c); p.setPitch(0); p.setHeadYaw(0); p.setBodyYaw(0); p.setNoGravity(true);
		var mob = c.spawnMob(EntityType.ZOMBIE, 2, 1, 5);
		mob.setAiDisabled(false); mob.setNoGravity(true);
		mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
		mob.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ() + 4, 0, 0);
		double startZ = mob.getZ();
		SkillHandler.onSkill(p, true);
		c.waitAndRun(3, () -> c.assertTrue(Math.abs(mob.getZ() - startZ) < 0.1, "Mob must not move before tongue arrives"));
		c.waitAndRun(12, () -> {
			c.assertTrue(mob.getZ() < startZ - 1, "Tongue must continuously pull living target toward wearer; start=" + startZ + " end=" + mob.getPos() + " player=" + p.getPos()); c.complete();
		});
	}

}
