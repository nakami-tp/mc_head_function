package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.wear.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;

public class HeadDepthTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 30)
	public void stunBlocksRabbitJumpAndHeldFlame(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
		player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 10);
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(GoatCharge.STUN, 40));
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.RABBIT)));
		player.setOnGround(true);
		player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
		RabbitMovement.jump(player);
		context.assertTrue(player.getVelocity().y == 0, "Stunned player cannot use rabbit jump packet");
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BLAZE)));
		var cow = context.spawnMob(EntityType.COW, 2, 2, 5);
		cow.setNoGravity(true);
		SkillHandler.onSkill(player, true);
		context.waitAndRun(5, () -> {
			context.assertFalse(cow.isOnFire(), "Stunned player cannot start sustained flame");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void goatTurnIsLimitedAndSecondPressStops(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.GOAT)));
		player.setYaw(0);
		SkillHandler.onSkill(player, true);
		player.setYaw(90);
		GoatCharge.tick(player);
		context.assertTrue(Math.abs(player.getVelocity().x) < 0.03 && player.getVelocity().z > 0.3, "Looking right must not instantly turn the charge");
		SkillHandler.onSkill(player, false);
		SkillHandler.onSkill(player, true);
		context.assertTrue(player.getVelocity().horizontalLength() < 0.1, "Second R must brake the charge");
		SkillHandler.onSkill(player, false);
		SkillHandler.onSkill(player, true);
		context.assertTrue(player.getVelocity().horizontalLength() < 0.1, "Cooldown must prevent restarting immediately");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void rabbitJumpsGrowRejectAirSpamAndResetOnR(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.RABBIT)));
		player.setOnGround(true);
		RabbitMovement.jump(player);
		double first = player.getVelocity().y;
		RabbitMovement.jump(player);
		context.assertTrue(player.getVelocity().y == first, "Airborne requests cannot add another impulse");
		player.setOnGround(true);
		RabbitMovement.tick(player);
		RabbitMovement.jump(player);
		context.assertTrue(player.getVelocity().y > first, "Second timely jump must be higher");
		context.assertTrue(player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED) > 0.1, "Repeated jumps must increase movement speed");
		SkillHandler.onSkill(player, true);
		player.setOnGround(true);
		RabbitMovement.tick(player);
		RabbitMovement.jump(player);
		context.assertTrue(Math.abs(player.getVelocity().y - first) < 0.0001, "R must reset jump height");
		player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
		RabbitMovement.tick(player);
		context.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED) - 0.1) < 0.0001, "Unequip must remove speed bonus");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void masteryUnlocksPersistsAndCannotBeAwardedTwiceInSameTick(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var state = PlayerHeadAccess.state(player);
		state.mastery.put(HeadType.PIG, 990);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.PIG)));
		var food = new ItemStack(net.minecraft.item.Items.APPLE);
		var component = food.get(net.minecraft.component.DataComponentTypes.FOOD);
		WearHandler.onEat(player, food, component);
		WearHandler.onEat(player, food, component);
		context.assertTrue(com.nakami.mcheadfunction.progression.HeadMastery.points(player, HeadType.PIG) == 995, "Practice must be limited to once per second");
		context.assertFalse(com.nakami.mcheadfunction.progression.HeadMastery.mastered(player, HeadType.PIG), "995 must not unlock perk");
		context.waitAndRun(21, () -> {
			WearHandler.onEat(player, food, component);
			context.assertTrue(com.nakami.mcheadfunction.progression.HeadMastery.mastered(player, HeadType.PIG), "Effective use must unlock perk at 1000");
			var saved = new net.minecraft.nbt.NbtCompound();
			player.writeNbt(saved);
			var loaded = context.createMockCreativeServerPlayerInWorld();
			loaded.readNbt(saved);
			context.assertTrue(com.nakami.mcheadfunction.progression.HeadMastery.mastered(loaded, HeadType.PIG), "Mastery must survive save and load");
			var respawned = context.createMockCreativeServerPlayerInWorld();
			respawned.copyFrom(player, false);
			context.assertTrue(com.nakami.mcheadfunction.progression.HeadMastery.mastered(respawned, HeadType.PIG), "Mastery must survive death");
			var entry = player.getServer().getAdvancementLoader().get(net.minecraft.util.Identifier.of("mc_head_function", "master_pig_head"));
			context.assertTrue(entry != null && player.getAdvancementTracker().getProgress(entry).isDone(), "Mastery achievement must unlock");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void beeCommandChoosesVisibleTargetAndRecalls(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
		player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 10);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BEE)));
		var target = context.spawnMob(EntityType.COW, 2, 2, 6);
		target.setAiDisabled(true);
		target.setNoGravity(true);
		var bee = context.spawnMob(EntityType.BEE, 2, 3, 3);
		bee.addCommandTag("mhf_owner:" + player.getUuid());
		SkillHandler.onSkill(player, true);
		context.assertTrue(bee.getTarget() == target, "R must command existing owned bees");
		context.assertFalse(target.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.GLOWING), "Bee target must not use outline marking");
		SkillHandler.onSkill(player, false);
		player.setPitch(-90);
		SkillHandler.onSkill(player, true);
		context.assertTrue(bee.getTarget() == null, "R into empty sky must recall bees");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void goatLaunchesThenStunsOnlyAfterLanding(TestContext context) {
		for (int x = -3; x < 9; x++) for (int z = -3; z < 12; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
		var player = context.createMockCreativeServerPlayerInWorld();
		var pos = context.getAbsolutePos(new BlockPos(2, 1, 2));
		player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 10);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.GOAT)));
		var cow = context.spawnMob(EntityType.COW, 2, 1, 3);
		cow.setAiDisabled(false);
		SkillHandler.onSkill(player, true);
		context.waitAndRun(5, () -> {
			context.assertTrue(cow.getY() > pos.getY() + 0.2, "Charge must launch target: y=" + cow.getY() + " base=" + pos.getY() + " health=" + cow.getHealth() + " dash=" + PlayerHeadAccess.state(player).goatDashTicks + " player=" + player.getPos() + " wall=" + player.horizontalCollision);
			context.assertFalse(GoatCharge.isStunned(cow), "Stun must wait for landing");
		});
		context.waitAndRun(35, () -> {
			context.assertTrue(GoatCharge.isStunned(cow), "Landed target must be stunned");
		});
		context.waitAndRun(95, () -> {
			context.assertFalse(GoatCharge.isStunned(cow), "Stun must expire");
			context.complete();
		});
	}
}
