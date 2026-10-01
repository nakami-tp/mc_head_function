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
	public void zombieRottenFleshBonusesIncludeLastBite(TestContext context) {
		var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
		var flesh = new ItemStack(Items.ROTTEN_FLESH, 2);
		for (int bite = 1; bite <= 2; bite++) {
			player.clearStatusEffects();
			player.setHealth(10);
			player.getHungerManager().setFoodLevel(10);
			flesh.finishUsing(context.getWorld(), player);
			context.assertTrue(flesh.getCount() == 2 - bite, "Each bite must consume one item");
			context.assertTrue(player.hasStatusEffect(StatusEffects.STRENGTH), "Bite " + bite + " must grant strength");
			context.assertTrue(player.hasStatusEffect(StatusEffects.HASTE), "Bite " + bite + " must grant haste");
			context.assertTrue(player.getHealth() == 12, "Bite " + bite + " must heal two health");
			context.assertFalse(player.hasStatusEffect(StatusEffects.POISON), "Rotten flesh must not count as ordinary food");
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void zombieRottenFleshPreventsRepeatedHunger(TestContext context) {
		var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
		var flesh = new ItemStack(Items.ROTTEN_FLESH, 2);
		// Guarantee the vanilla penalty so this regression cannot pass by chance.
		flesh.set(DataComponentTypes.FOOD, new net.minecraft.component.type.FoodComponent.Builder()
			.nutrition(4).saturationModifier(0.1F)
			.statusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.HUNGER, 600), 1.0F).build());
		for (int bite = 1; bite <= 2; bite++) {
			player.setHealth(10);
			player.getHungerManager().setFoodLevel(10);
			flesh.finishUsing(context.getWorld(), player);
			context.assertFalse(player.hasStatusEffect(StatusEffects.HUNGER), "Bite " + bite + " must not apply vanilla hunger");
			context.assertFalse(player.hasStatusEffect(StatusEffects.POISON), "Repeated rotten flesh must not poison");
			context.assertTrue(player.getHealth() == 12, "Every consecutive bite must heal");
			context.assertTrue(player.getStatusEffect(StatusEffects.STRENGTH).getDuration() == 200, "Strength must refresh");
			context.assertTrue(player.getStatusEffect(StatusEffects.HASTE).getDuration() == 200, "Haste must refresh");
			player.getStatusEffect(StatusEffects.STRENGTH).update(player, () -> { });
			player.getStatusEffect(StatusEffects.HASTE).update(player, () -> { });
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void zombieFoodFilteringPreservesOtherEffects(TestContext context) {
		var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		var flesh = new ItemStack(Items.ROTTEN_FLESH, 4);
		var food = new net.minecraft.component.type.FoodComponent.Builder()
			.nutrition(4).saturationModifier(0.1F)
			.statusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.HUNGER, 600), 1.0F)
			.statusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.SPEED, 100), 1.0F).build();
		flesh.set(DataComponentTypes.FOOD, food);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
		player.getHungerManager().setFoodLevel(10);
		player.getHungerManager().setSaturationLevel(0);
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.HUNGER, 80, 1));
		flesh.finishUsing(context.getWorld(), player);
		context.assertTrue(player.getHungerManager().getFoodLevel() == 14, "Rotten flesh must retain nutrition");
		context.assertTrue(Math.abs(player.getHungerManager().getSaturationLevel() - food.saturation()) < 0.001F,
			"Rotten flesh must retain saturation");
		context.assertTrue(player.getStatusEffect(StatusEffects.HUNGER).getDuration() == 80,
			"Eating must preserve pre-existing hunger without extending it");
		context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Other food effects must remain");
		context.assertTrue(flesh.get(DataComponentTypes.FOOD).equals(food), "Item food component must remain unchanged");
		player.clearStatusEffects();
		new ItemStack(Items.BREAD).finishUsing(context.getWorld(), player);
		context.assertTrue(player.hasStatusEffect(StatusEffects.POISON), "Ordinary food must still poison a zombie wearer");
		player.clearStatusEffects();
		player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
		flesh.finishUsing(context.getWorld(), player);
		context.assertTrue(player.hasStatusEffect(StatusEffects.HUNGER), "Unequipping must restore vanilla hunger");
		context.assertFalse(player.hasStatusEffect(StatusEffects.STRENGTH), "Unequipping must stop zombie bonuses");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void stackedHeadsEquipExactlyOne(TestContext context) {
		var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		for (HeadType type : HeadType.values()) {
			player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(type), 64));
			var result = player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
			player.setStackInHand(Hand.MAIN_HAND, result.getValue());
			context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).getCount() == 1,
				"Must equip exactly one " + type);
			context.assertTrue(player.getMainHandStack().getCount() == 63, "Must retain 63 " + type);
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void allHeadsCanReplaceEachOther(TestContext context) {
		var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		for (HeadType worn : HeadType.values()) {
			for (HeadType held : HeadType.values()) {
				if (worn == held) continue;
				player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(worn)));
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(held)));
				var result = player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
				player.setStackInHand(Hand.MAIN_HAND, result.getValue());
				context.assertTrue(HeadLookups.worn(player) == held, "Cannot replace " + worn + " with " + held);
				context.assertTrue(player.getMainHandStack().isOf(HeadItems.item(worn)), "Old head must return to hand");
			}
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void stackedSwapPreservesOldHeadAndComponents(TestContext context) {
		var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		for (Hand hand : Hand.values()) for (HeadType type : HeadType.values()) {
			player.getInventory().clear();
			ItemStack previous = new ItemStack(Items.DIAMOND_HELMET);
			previous.set(DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Old helmet"));
			player.equipStack(EquipmentSlot.HEAD, previous);
			ItemStack stack = new ItemStack(HeadItems.item(type), 64);
			stack.set(DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Named head"));
			player.setStackInHand(hand, stack);
			var result = stack.use(context.getWorld(), player, hand);
			player.setStackInHand(hand, result.getValue());
			context.assertTrue(player.getStackInHand(hand).getCount() == 63, "Stack swap must retain 63: " + type + " " + hand);
			context.assertTrue(ItemStack.areEqual(player.getEquippedStack(EquipmentSlot.HEAD), stack.copyWithCount(1)), "Equipped head must preserve components");
			context.assertTrue(player.getInventory().count(Items.DIAMOND_HELMET) == 1, "Must return previous helmet");
			context.assertTrue(player.getInventory().contains(previous), "Previous helmet components must survive");
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void creativeHeadsStillEquipOneAndKeepHeldStack(TestContext context) {
		var player = context.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
		for (HeadType type : HeadType.values()) {
			player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(type), 64));
			var result = player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
			player.setStackInHand(Hand.MAIN_HAND, result.getValue());
			context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).getCount() == 1, "Creative must equip one " + type);
			context.assertTrue(player.getMainHandStack().getCount() == 64, "Creative must keep held stack");
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bindingCursePreventsHeadReplacement(TestContext context) {
		var player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
		helmet.addEnchantment(context.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
			.getEntry(net.minecraft.enchantment.Enchantments.BINDING_CURSE).orElseThrow(), 1);
		for (HeadType type : HeadType.values()) {
			player.equipStack(EquipmentSlot.HEAD, helmet.copy());
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(HeadItems.item(type), 64));
			var result = player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
			context.assertFalse(result.getResult().isAccepted(), "Binding must block " + type);
			context.assertTrue(ItemStack.areEqual(player.getEquippedStack(EquipmentSlot.HEAD), helmet), "Bound helmet must remain");
			context.assertTrue(player.getMainHandStack().getCount() == 64, "Failed swap must not consume heads");
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void fullInventoryDropsOnlyTheReplacedHelmet(TestContext context) {
		var world = context.getWorld();
		var data = net.minecraft.server.network.ConnectedClientData.createDefault(
			new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "head-swap-test"), false);
		var player = new net.minecraft.server.network.ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		var connection = new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
		var origin = context.getAbsolutePos(new BlockPos(2, 2, 2));
		player.refreshPositionAndAngles(origin.getX(), origin.getY(), origin.getZ(), 0, 0);
		for (int slot = 0; slot < 36; slot++) player.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ZOMBIE_HEAD, 64));
		var result = player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
		player.setStackInHand(Hand.MAIN_HAND, result.getValue());
		context.assertTrue(player.getMainHandStack().getCount() == 63, "Full inventory must keep remaining heads");
		context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).getCount() == 1, "Full inventory must equip one");
		var drops = context.getWorld().getEntitiesByClass(ItemEntity.class, player.getBoundingBox().expand(3), e -> e.getStack().isOf(Items.DIAMOND_HELMET));
		context.assertTrue(drops.size() == 1 && drops.getFirst().getStack().getCount() == 1, "Full inventory must drop previous helmet exactly once");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void creeperRecyclesAboveImpactSurface(TestContext context) {
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.OBSIDIAN);
		var player = context.createMockCreativeServerPlayerInWorld();
		var origin = context.getAbsolutePos(new BlockPos(2, 1, 2));
		player.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY() + 2, origin.getZ() + 0.5, 0, 90);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CREEPER_HEAD));
		ThrowHandler.throwHeldHead(player);
		context.waitAndRun(10, () -> {
			var items = context.getWorld().getEntitiesByClass(RecycledHeadItemEntity.class,
				new net.minecraft.util.math.Box(origin).expand(5), e -> e.getStack().isOf(Items.CREEPER_HEAD));
			context.assertTrue(items.size() == 1, "Creeper must recycle once");
			context.assertTrue(items.getFirst().getY() >= origin.getY(), "Creeper item must stay above floor");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void zombieHopsStayAboveFloor(TestContext context) {
		for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) {
			context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
		}
		var player = context.createMockCreativeServerPlayerInWorld();
		var origin = context.getAbsolutePos(new BlockPos(2, 1, 2));
		player.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 90);
		var target = context.spawnMob(EntityType.COW, 5, 1, 2);
		target.setAiDisabled(true);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ZOMBIE_HEAD));
		ThrowHandler.throwHeldHead(player);
		var head = context.getWorld().getEntitiesByClass(ThrownHeadEntity.class, player.getBoundingBox().expand(4), e -> true).getFirst();
		for (int tick = 5; tick <= 60; tick++) {
			context.waitAndRun(tick, () -> {
				context.assertFalse(head.isRemoved(), "Zombie should still be chasing");
				context.assertTrue(head.getY() >= origin.getY() - 0.001, "Zombie penetrated floor: " + head.getY());
			});
		}
		context.waitAndRun(61, () -> {
			context.assertTrue(target.getHealth() < target.getMaxHealth(), "Zombie must reach and bite target");
			head.discard();
			context.complete();
		});
	}

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
