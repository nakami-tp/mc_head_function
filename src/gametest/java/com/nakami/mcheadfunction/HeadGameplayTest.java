package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.entity.RecycledHeadItemEntity;
import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.entity.OwnerSafeExplosionBehavior;
import com.nakami.mcheadfunction.throwing.ThrowHandler;
import com.nakami.mcheadfunction.wear.SkillHandler;
import com.nakami.mcheadfunction.wear.WearHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HeadGameplayTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void threeShakesBeheadWithoutDamage(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var target = context.spawnMob(EntityType.ZOMBIE, 2, 1, 3);
		target.setAiDisabled(true);
		player.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ() - 2, 0, 0);
		float health = target.getHealth();
		player.attack(target);
		player.attack(target);
		context.assertFalse(HeadlessAccess.isHeadless(target), "Two shakes must not behead");
		player.attack(target);
		context.assertTrue(HeadlessAccess.isHeadless(target), "Third shake must behead");
		context.assertTrue(target.getHealth() == health, "Shakes must not deal melee damage");
		var heads = context.getWorld().getEntitiesByClass(ThrownHeadEntity.class,
			player.getBoundingBox().expand(4), head -> head.getHeadType() == HeadType.ZOMBIE);
		context.assertTrue(heads.size() == 1, "Beheading must produce one flying zombie head");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void heldHeadsSpawnAndConsumeOneItem(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.getAbilities().creativeMode = false;
		for (HeadType type : HeadType.values()) {
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(type), 2));
			ThrowHandler.throwHeldHead(player);
			context.assertTrue(player.getMainHandStack().getCount() == 1, "Throw must consume one " + type);
			var heads = context.getWorld().getEntitiesByClass(ThrownHeadEntity.class,
				player.getBoundingBox().expand(8), head -> head.getHeadType() == type);
			if (type == HeadType.BAT) {
				context.assertTrue(heads.isEmpty(), "Bat head must recycle immediately");
				var items = context.getWorld().getEntitiesByClass(ItemEntity.class,
					player.getBoundingBox().expand(8), item -> item.getStack().isOf(HeadItems.item(HeadType.BAT)));
				context.assertTrue(!items.isEmpty(), "Bat head must recycle into an item");
				items.forEach(ItemEntity::discard);
			} else {
				context.assertTrue(heads.size() == 1, "Throw must spawn one " + type);
				context.assertTrue(heads.getFirst().getStack().isOf(HeadItems.item(type)), "Wrong thrown item: " + type);
				heads.getFirst().discard();
			}
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void knockedOffHeadsKeepTheirItem(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		for (HeadType type : HeadType.values()) {
			ThrowHandler.knockOff(player, type);
			var heads = context.getWorld().getEntitiesByClass(ThrownHeadEntity.class,
				player.getBoundingBox().expand(4), head -> head.getHeadType() == type);
			context.assertTrue(heads.size() == 1, "Knocking off " + type + " must spawn one head");
			context.assertTrue(heads.getFirst().getStack().isOf(HeadItems.item(type)),
				"Flying head must retain the correct item: " + type);
			heads.getFirst().discard();
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void pigHeadAddsSaturation(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.PIG)));
		player.getHungerManager().setFoodLevel(10);
		player.getHungerManager().setSaturationLevel(0);
		ItemStack bread = new ItemStack(Items.BREAD);
		var food = bread.get(DataComponentTypes.FOOD);
		player.eatFood(player.getWorld(), bread, food);
		context.assertTrue(player.getHungerManager().getSaturationLevel() > food.saturation(),
			"Pig head must add extra saturation on top of the meal");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void recycledHeadSurvivesExplosion(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.PIG)));
		player.getAbilities().creativeMode = false;
		ThrowHandler.throwHeldHead(player);
		var heads = context.getWorld().getEntitiesByClass(ThrownHeadEntity.class,
			player.getBoundingBox().expand(8), head -> true);
		context.assertTrue(!heads.isEmpty(), "Thrown pig head must exist");
		heads.getFirst().recycle();
		var items = context.getWorld().getEntitiesByClass(RecycledHeadItemEntity.class,
			player.getBoundingBox().expand(8), item -> item.getStack().isOf(HeadItems.item(HeadType.PIG)));
		context.assertTrue(items.size() == 1, "Recycle must drop an explosion-proof head");
		var item = items.getFirst();
		context.getWorld().createExplosion(null, item.getX(), item.getY(), item.getZ(), 4.0F, World.ExplosionSourceType.TNT);
		context.assertFalse(item.isRemoved(), "Recycled head must survive explosion");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void explosionDoesNotHurtThrower(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.refreshPositionAndAngles(1.5, 2, 1.5, 0, 0);
		float health = player.getHealth();
		context.getWorld().createExplosion(
			null,
			context.getWorld().getDamageSources().explosion(null, player),
			new OwnerSafeExplosionBehavior(player.getUuid(), false),
			player.getX(),
			player.getY(),
			player.getZ(),
			4.0F,
			false,
			World.ExplosionSourceType.NONE
		);
		context.assertTrue(player.getHealth() == health, "Thrower must not take explosion damage");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void endermanEatsSixteenSameBlocks(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		for (int x = 0; x < 20; x++) {
			context.setBlockState(new BlockPos(x, 1, 2), Blocks.STONE);
		}
		BlockPos first = context.getAbsolutePos(new BlockPos(0, 1, 2));
		player.refreshPositionAndAngles(first.getX() + 0.5, first.getY() + 2.0, first.getZ() + 0.5, 0, 90);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.ENDERMAN)));
		player.getAbilities().creativeMode = false;
		ThrowHandler.throwHeldHead(player);
		context.waitAndRun(120, () -> {
			int remaining = 0;
			for (int x = 0; x < 20; x++) {
				if (context.getWorld().getBlockState(context.getAbsolutePos(new BlockPos(x, 1, 2))).isOf(Blocks.STONE)) {
					remaining++;
				}
			}
			context.assertTrue(remaining <= 4, "Enderman head must eat up to 16 connected stone, remaining=" + remaining);
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void headlessPigDropsCombatTarget(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var pig = context.spawnMob(EntityType.PIG, 3, 1, 3);
		HeadlessAccess.setHeadless(pig, true);
		pig.setTarget(player);
		context.waitAndRun(5, () -> {
			context.assertTrue(pig.getTarget() == null, "Headless body must not keep a combat target");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void frogGrabPutsChestContentsInInventory(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		BlockPos chestPos = new BlockPos(2, 1, 3);
		context.setBlockState(chestPos, Blocks.CHEST);
		BlockPos chestAbs = context.getAbsolutePos(chestPos);
		var chest = context.getWorld().getBlockEntity(chestAbs);
		if (chest instanceof net.minecraft.inventory.Inventory inventory) {
			inventory.setStack(0, new ItemStack(Items.DIAMOND, 5));
		}
		player.refreshPositionAndAngles(chestAbs.getX() + 0.5, chestAbs.getY(), chestAbs.getZ() - 1.4, 0, 35);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.FROG)));
		SkillHandler.onSkill(player, true);
		context.assertTrue(player.getInventory().count(Items.DIAMOND) >= 5, "Frog grab must put chest contents into the backpack");
		context.expectBlock(Blocks.AIR, chestPos);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void wolfKeepsOnlyLatestMark(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.WOLF)));
		var first = context.spawnMob(EntityType.ZOMBIE, 2, 1, 3);
		var second = context.spawnMob(EntityType.ZOMBIE, 3, 1, 3);
		first.setAiDisabled(true);
		second.setAiDisabled(true);
		WearHandler.onDealtDamage(player, first, 2, false, true);
		WearHandler.onDealtDamage(player, second, 2, false, true);
		context.assertFalse(first.hasStatusEffect(StatusEffects.GLOWING), "Old wolf mark must clear");
		context.assertTrue(second.hasStatusEffect(StatusEffects.GLOWING), "Newest wolf mark must glow");
		context.assertTrue(second.getUuid().equals(PlayerHeadAccess.state(player).markedTarget), "Marked target must be the latest hit");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void goatDashStopsAfterUnequip(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.GOAT)));
		SkillHandler.onSkill(player, true);
		context.assertTrue(PlayerHeadAccess.state(player).goatDashTicks > 0, "Goat skill must start a dash");
		player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
		context.waitAndRun(2, () -> {
			context.assertTrue(HeadLookups.worn(player) == null, "Goat head must be unequipped");
			context.assertTrue(PlayerHeadAccess.state(player).goatDashTicks == 0, "Dash must stop after unequipping the goat head");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void llamaSkillSpawnsSpit(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.LLAMA)));
		SkillHandler.onSkill(player, true);
		var spit = context.getWorld().getEntitiesByType(EntityType.LLAMA_SPIT, player.getBoundingBox().expand(8), entity -> true);
		context.assertTrue(!spit.isEmpty(), "Llama skill must spawn a spit projectile");
		context.complete();
	}
}
