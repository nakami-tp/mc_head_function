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
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void goatMiningUsesHardnessAndResetsOnStop(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.GOAT)));
        var wall = context.getAbsolutePos(new BlockPos(2, 2, 3));
        player.refreshPositionAndAngles(wall.getX() + 0.5, wall.getY(), wall.getZ() - 0.31, 0, 0);
        var world = context.getWorld();
        var state = PlayerHeadAccess.state(player);
        world.setBlockState(wall, Blocks.DIRT.getDefaultState());
        GoatCharge.toggle(player);
        for (int i = 0; i < 4; i++) GoatCharge.tick(player);
        context.assertTrue(world.getBlockState(wall).isOf(Blocks.DIRT), "Dirt should show progress before breaking");
        GoatCharge.tick(player);
        context.assertTrue(world.getBlockState(wall).isAir(), "Dirt should break after five contact ticks");
        world.setBlockState(wall, Blocks.STONE.getDefaultState());
        for (int i = 0; i < 5; i++) GoatCharge.tick(player);
        context.assertTrue(world.getBlockState(wall).isOf(Blocks.STONE), "Stone must take longer than dirt");
        GoatCharge.stop(player);
        state.goatCooldown = 0;
        GoatCharge.toggle(player);
        for (int i = 0; i < 10; i++) GoatCharge.tick(player);
        context.assertTrue(world.getBlockState(wall).isOf(Blocks.STONE), "Stopping must discard old progress");
        for (int i = 0; i < 5; i++) GoatCharge.tick(player);
        context.assertTrue(world.getBlockState(wall).isAir(), "Stone should break after fifteen uninterrupted ticks");
        world.setBlockState(wall, Blocks.BEDROCK.getDefaultState());
        GoatCharge.tick(player);
        context.assertTrue(world.getBlockState(wall).isOf(Blocks.BEDROCK) && state.goatDashTicks == 0, "Bedrock must stop charge without breaking");
        context.complete();
    }

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
		player.setYaw(180);
		GoatCharge.tick(player);
		context.assertTrue(Math.abs(player.getVelocity().x) < 0.03 && player.getVelocity().z > 0.3, "Looking right must not instantly turn the charge");
		var state = PlayerHeadAccess.state(player);
		context.assertTrue(state.goatHeading == 0 && player.getYaw() == 0, "Mouse yaw must not steer charge");
		GoatCharge.steer(player, 1);
		GoatCharge.tick(player);
		context.assertTrue(Math.abs(state.goatHeading - 1.8F) < 0.001, "Right input must turn by 1.8 degrees per tick");
		GoatCharge.steer(player, 0);
		GoatCharge.tick(player);
		context.assertTrue(Math.abs(state.goatHeading - 1.8F) < 0.001, "Released input must keep heading");
		GoatCharge.steer(player, -1);
		GoatCharge.tick(player);
		context.assertTrue(Math.abs(state.goatHeading) < 0.001, "Left input must reverse the turn");
		GoatCharge.steer(player, 100);
		context.assertTrue(state.goatSteering == -1, "Invalid turn values must be rejected");
		state.goatSteeringExpires = player.getServerWorld().getTime();
		GoatCharge.tick(player);
		context.assertTrue(state.goatSteering == 0, "Stale input must expire");
		state.mastery.put(HeadType.GOAT, 1000);
		float before = state.goatHeading;
		GoatCharge.steer(player, 1);
		GoatCharge.tick(player);
		context.assertTrue(Math.abs(state.goatHeading - before - 3) < 0.001, "Mastery must preserve faster keyboard steering");
		double speedBeforeStop = player.getVelocity().horizontalLength();
		SkillHandler.onSkill(player, false);
		SkillHandler.onSkill(player, true);
		context.assertTrue(state.goatDashTicks == 0 && Math.abs(player.getVelocity().horizontalLength() - speedBeforeStop * 0.2) < 0.0001, "Second R must brake the charge");
		SkillHandler.onSkill(player, false);
		SkillHandler.onSkill(player, true);
		context.assertTrue(state.goatDashTicks == 0 && Math.abs(player.getVelocity().horizontalLength() - speedBeforeStop * 0.2) < 0.0001, "Cooldown must prevent restarting immediately");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void rabbitCeilingPreservesPositionAndHeading(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.RABBIT)));
		var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
		player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 81, pos.getZ() + 0.5, 73, 12);
		player.setOnGround(false);
		player.setVelocity(0.4, 1, 0.6);
		var state = PlayerHeadAccess.state(player);
		state.rabbitAirborne = true;
		state.rabbitLaunchY = pos.getY();
		double x = player.getX(), z = player.getZ();
		RabbitMovement.tick(player);
		context.assertTrue(player.getX() == x && player.getZ() == z, "Height correction must preserve horizontal position");
		context.assertTrue(player.getYaw() == 73 && player.getPitch() == 12, "Height correction must preserve view direction");
		context.assertTrue(player.getY() == state.rabbitLaunchY + 80, "Ceiling must remain at eighty blocks");
		context.assertTrue(player.getVelocity().x == 0.4 && player.getVelocity().z == 0.6, "Height correction must preserve horizontal velocity");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void rabbitJumpsGrowRejectAirSpamAndResetOnR(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.RABBIT)));
		player.setOnGround(true);
		RabbitMovement.jump(player);
		double first = player.getVelocity().y;
		context.assertTrue(player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED) > 0.1, "First jump must already grant a speed bonus");
		RabbitMovement.jump(player);
		context.assertTrue(player.getVelocity().y == first, "Airborne requests cannot add another impulse");
		player.setOnGround(true);
		RabbitMovement.tick(player);
		RabbitMovement.jump(player);
		context.assertTrue(player.getVelocity().y > first, "Second timely jump must be higher");
		context.assertTrue(player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED) > 0.1, "Repeated jumps must increase movement speed");
		var sky = player.getServer().getAdvancementLoader().get(net.minecraft.util.Identifier.of("mc_head_function", "rabbit_sky"));
		for (int jump = 3; jump <= 17; jump++) {
			player.setOnGround(true);
			RabbitMovement.tick(player);
			RabbitMovement.jump(player);
			context.assertTrue(PlayerHeadAccess.state(player).rabbitChain == Math.min(jump, 16), "Chain must cap at sixteen");
			context.assertTrue(player.getAdvancementTracker().getProgress(sky).isDone() == (jump >= 16), "Skybound must unlock at sixteen, not eight");
		}
		context.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED) - 0.295) < 0.0001, "Maximum rabbit speed must reach 2.95x");
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
