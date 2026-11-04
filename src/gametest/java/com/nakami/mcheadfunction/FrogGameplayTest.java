package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.entity.FrogTongueEntity;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.wear.FrogMouth;
import com.nakami.mcheadfunction.wear.SkillHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
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
	public void toolRequiredBlockIsOnlyPoked(TestContext c) {
		c.setBlockState(2, 1, 3, Blocks.STONE);
		var p = aim(c);
		p.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
		SkillHandler.onSkill(p, true);
		c.waitAndRun(7, () -> {
			var tongues = c.getWorld().getEntitiesByClass(FrogTongueEntity.class, p.getBoundingBox().expand(10), e -> e.owner() == p);
			c.assertTrue(tongues.size() == 1 && tongues.getFirst().carriedBlock().isAir(),
				"Unharvestable block must not ride the tongue");
			c.expectBlock(Blocks.STONE, new BlockPos(2, 1, 3));
		});
		c.waitAndRun(14, () -> {
			c.expectBlock(Blocks.STONE, new BlockPos(2, 1, 3));
			c.assertTrue(p.getInventory().count(Items.STONE) + p.getInventory().count(Items.COBBLESTONE) == 0,
				"Poke must not pull a tool-required block");
			c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void handHarvestableBlockIsPulled(TestContext c) {
		c.setBlockState(2, 1, 3, Blocks.DIRT);
		var p = aim(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(7, () -> c.expectBlock(Blocks.AIR, new BlockPos(2, 1, 3)));
		c.waitAndRun(14, () -> {
			c.assertTrue(p.getInventory().count(Items.DIRT) == 1, "Hand-breakable block must be delivered");
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
	private MobEntity mouthful(TestContext c, net.minecraft.server.network.ServerPlayerEntity p, EntityType<? extends MobEntity> type) {
		p.setPitch(0); p.setHeadYaw(0); p.setBodyYaw(0);
		var mob = c.spawnMob(type, 2, 1, 5);
		mob.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ() + 3, 0, 0);
		// Aim at the body centre so short mobs are not missed by an eye-level ray.
		p.setPitch((float) Math.toDegrees(Math.atan2(p.getEyeY() - mob.getBoundingBox().getCenter().y, 3)));
		SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
		return mob;
	}
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void heldMobIsDigestedIntoInventory(TestContext c) {
		c.getWorld().getGameRules().get(net.minecraft.world.GameRules.DO_MOB_LOOT).set(true, c.getWorld().getServer());
		var p = aim(c);
		var mob = mouthful(c, p, EntityType.CHICKEN);
		c.waitAndRun(FrogTongueEntity.DURATION + 2, () -> c.assertTrue(FrogMouth.held(p) == mob, "Tongued mob must end up held in the mouth"));
		c.waitAndRun(FrogTongueEntity.DURATION + 5 * 10 + 5, () -> {
			c.assertTrue(mob.isRemoved() && FrogMouth.held(p) == null, "Four-health chicken must be digested within two bites");
			c.assertTrue(p.getInventory().count(Items.FEATHER) + p.getInventory().count(Items.CHICKEN) > 0, "Digested loot must go to inventory");
			c.assertTrue(c.getWorld().getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(8), e -> true).isEmpty(), "Digestion must not spill loot");
			c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void secondPressSpitsHeldMobFar(TestContext c) {
		var p = aim(c);
		var mob = mouthful(c, p, EntityType.ZOMBIE);
		c.waitAndRun(FrogTongueEntity.DURATION + 2, () -> {
			c.assertTrue(FrogMouth.held(p) == mob, "Zombie must be held");
			p.setPitch(0);
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
			c.assertTrue(FrogMouth.held(p) == null && !FrogMouth.isHeld(mob), "R must spit the held mob");
		});
		c.waitAndRun(FrogTongueEntity.DURATION + 22, () -> {
			c.assertTrue(mob.isAlive() && mob.getZ() > p.getZ() + 6, "Spat mob must fly well past the wearer; mob=" + mob.getPos() + " player=" + p.getPos());
			c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void heldCreeperIgnites(TestContext c) {
		var p = aim(c);
		var mob = mouthful(c, p, EntityType.CREEPER);
		c.waitAndRun(FrogTongueEntity.DURATION + 2, () -> {
			c.assertTrue(FrogMouth.held(p) == mob && ((net.minecraft.entity.mob.CreeperEntity) mob).isIgnited(), "Held creeper must be lit");
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void heldMobDoesNotShoveOrShowFire(TestContext c) {
		var p = aim(c);
		p.setNoGravity(true);
		p.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
		var mob = mouthful(c, p, EntityType.ZOMBIE);
		c.waitAndRun(FrogTongueEntity.DURATION + 2, () -> {
			c.assertTrue(FrogMouth.held(p) == mob, "Zombie must be held");
			c.assertTrue(mob.noClip, "Held mob must pass through the wearer");
			c.assertTrue(!mob.isPushable(), "Held mob must not be pushable");
			c.assertTrue(!mob.isCollidable(), "Held mob must not be a solid wall");
			mob.setOnFireFor(8);
			c.assertTrue(!mob.doesRenderOnFire(), "Swallowed fire must not draw in front of the wearer");
			c.assertTrue(mob.squaredDistanceTo(p) < 0.25, "Held mob must sit inside the wearer, not in front of the camera; mob=" + mob.getPos() + " player=" + p.getPos());
			var start = p.getPos();
			var startVel = p.getVelocity();
			mob.pushAwayFrom(p);
			p.pushAwayFrom(mob);
			c.assertTrue(p.getPos().squaredDistanceTo(start) < 1.0E-6, "Push must not move the wearer; at " + p.getPos());
			c.assertTrue(p.getVelocity().squaredDistanceTo(startVel) < 1.0E-6, "Push must not add wearer velocity");
			c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void tongueInterceptsAndReturnsArrow(TestContext c) {
		var p = aim(c); p.setPitch(0); p.setNoGravity(true);
		var arrow = new net.minecraft.entity.projectile.ArrowEntity(EntityType.ARROW, c.getWorld());
		arrow.setPosition(p.getEyePos().add(0, -0.28, 4));
		arrow.setVelocity(0, 0, -0.4); arrow.setNoGravity(true);
		c.getWorld().spawnEntity(arrow);
		SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
		c.waitAndRun(4, () -> {
			c.assertTrue(arrow.isRemoved(), "Tongue must intercept an incoming arrow in open air");
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
			var arrows = c.getWorld().getEntitiesByClass(net.minecraft.entity.projectile.ArrowEntity.class,
				p.getBoundingBox().expand(16), a -> a.getOwner() == p);
			c.assertTrue(arrows.size() == 1, "Second R must return exactly one player-owned arrow");
			var returned = arrows.getFirst();
			c.assertTrue(Math.abs(returned.getVelocity().length() - arrow.getVelocity().length() * 1.5) < .001, "Return initial speed must be 1.5 times the captured speed");
			c.assertTrue(returned.getVelocity().z > 0 && returned.getDamage() == 8,
				"Returned arrow must face forward and have eight base damage");
			c.assertTrue(returned.pickupType == net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.DISALLOWED,
				"Returned arrow must not be collectible");
			c.complete();
		});
	}

	private net.minecraft.entity.projectile.ProjectileEntity incoming(TestContext c,
		net.minecraft.server.network.ServerPlayerEntity p, EntityType<? extends net.minecraft.entity.projectile.ProjectileEntity> type) {
		p.setPitch(0); p.setNoGravity(true);
		var projectile = type.create(c.getWorld());
		projectile.setPosition(p.getEyePos().add(0, -0.28, 4));
		projectile.setNoGravity(true); projectile.setVelocity(0, 0, -.4);
		c.getWorld().spawnEntity(projectile);
		SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
		return projectile;
	}
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void fireballBurnsSixTimesThenSneezes(TestContext c) {
		var p = aim(c); p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
		p.getHungerManager().setFoodLevel(6);
		for (int i = 0; i < 80; i++) { p.tick(); p.playerTick(); }
		p.setHealth(20);
		incoming(c, p, EntityType.FIREBALL);
		c.waitAndRun(4, () -> c.assertTrue(com.nakami.mcheadfunction.wear.FrogProjectile.occupied(p), "Fireball must be held"));
		c.waitAndRun(9, () -> c.assertTrue(p.getHealth() == 20, "No burn before ten held ticks"));
		c.waitAndRun(54, () -> {
			c.assertTrue(com.nakami.mcheadfunction.wear.FrogProjectile.occupied(p), "Must remain held before sixty ticks");
			c.assertTrue(p.getHealth() == 15, "Five half-second burns must cost five health, got " + p.getHealth());
		});
		c.waitAndRun(64, () -> {
			c.assertTrue(!com.nakami.mcheadfunction.wear.FrogProjectile.occupied(p), "Sixty ticks must force a sneeze");
			c.assertTrue(p.getHealth() == 14, "Six burns must cost six health, got " + p.getHealth());
			c.assertTrue(c.getWorld().getEntitiesByClass(net.minecraft.entity.projectile.FireballEntity.class,
				p.getBoundingBox().expand(20), e -> e.getOwner() == p && e.getVelocity().z > 0).size() == 1,
				"Sneeze must fire one player-owned fireball forward"); c.complete();
		});
	}
	private void parryCooldownBoundary(TestContext c, boolean mastered) {
		var p = aim(c);
		if (mastered) PlayerHeadAccess.state(p).mastery.put(HeadType.FROG, 1000);
		incoming(c, p, EntityType.ARROW);
		c.waitAndRun(4, () -> {
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
			c.assertTrue(!com.nakami.mcheadfunction.wear.FrogProjectile.occupied(p), "R must return captured arrow");
		});
		c.waitAndRun(mastered ? 7 : 13, () -> {
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
			c.assertTrue(c.getWorld().getEntitiesByClass(FrogTongueEntity.class, p.getBoundingBox().expand(20), t -> t.owner() == p).isEmpty(),
				"Cannot cast again one tick before cooldown ends");
		});
		c.waitAndRun(mastered ? 8 : 14, () -> {
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
			c.assertTrue(c.getWorld().getEntitiesByClass(FrogTongueEntity.class, p.getBoundingBox().expand(20), t -> t.owner() == p).size() == 1,
				"Can cast again exactly at cooldown boundary"); c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void normalParryCooldownExpiresAtTenTicks(TestContext c) { parryCooldownBoundary(c, false); }
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void masteredParryCooldownExpiresAtFourTicks(TestContext c) { parryCooldownBoundary(c, true); }
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnedTippedArrowKeepsEffectAndAwardsParryKill(TestContext c) {
		var p = aim(c);
		var victim = new net.minecraft.entity.mob.SkeletonEntity[1];
		var shot = new net.minecraft.entity.projectile.ArrowEntity[1];
		var arrow = (net.minecraft.entity.projectile.ArrowEntity) incoming(c, p, EntityType.ARROW);
		arrow.addEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS, 200));
		c.waitAndRun(4, () -> {
			var skeleton = c.spawnMob(EntityType.SKELETON, 2, 1, 5);
			skeleton.setAiDisabled(true); skeleton.setNoGravity(true);
			victim[0] = skeleton;
			skeleton.setHealth(1); skeleton.setPosition(p.getPos().add(0, 0, 4));
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
			var returned = c.getWorld().getEntitiesByClass(net.minecraft.entity.projectile.ArrowEntity.class,
				p.getBoundingBox().expand(20), e -> e.getOwner() == p).getFirst();
			shot[0] = returned;
			c.assertTrue(returned.getItemStack().get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS).hasEffects(),
				"Returned arrow must preserve potion effects");
		});
		c.waitAndRun(18, () -> {
			var advancement = c.getWorld().getServer().getAdvancementLoader().get(net.minecraft.util.Identifier.of("mc_head_function", "frog_projectile_parry"));
			c.assertTrue(p.getAdvancementTracker().getProgress(advancement).isDone(), "Returned projectile killing ranged mob must award parry challenge; victim=" + victim[0].getHealth() + "/" + victim[0].getPos() + " arrow=" + shot[0].getPos() + " owner=" + shot[0].getOwner() + " tags=" + shot[0].getCommandTags()); c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void removingFrogHeadExpelsAmmo(TestContext c) {
		var p = aim(c); incoming(c, p, EntityType.SMALL_FIREBALL);
		c.waitAndRun(4, () -> p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY));
		c.waitAndRun(7, () -> {
			c.assertTrue(!com.nakami.mcheadfunction.wear.FrogProjectile.occupied(p), "Removing head must empty mouth");
			c.assertTrue(c.getWorld().getEntitiesByClass(net.minecraft.entity.projectile.SmallFireballEntity.class,
				p.getBoundingBox().expand(20), e -> e.getOwner() == p).size() == 1, "Interruption must expel exactly one projectile"); c.complete();
		});
	}

	private void returnsSpecial(TestContext c, EntityType<? extends net.minecraft.entity.projectile.ProjectileEntity> type) {
		var p = aim(c); incoming(c, p, type);
		c.waitAndRun(4, () -> {
			c.assertTrue(com.nakami.mcheadfunction.wear.FrogProjectile.occupied(p), "Must intercept " + type);
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
			var returned = c.getWorld().getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class,
				p.getBoundingBox().expand(16), e -> e.getOwner() == p && e.getType() == type);
			c.assertTrue(returned.size() == 1 && returned.getFirst().getVelocity().z > 0, "Must return the same projectile type toward crosshair: " + type);
			c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnsWitherSkull(TestContext c) { returnsSpecial(c, EntityType.WITHER_SKULL); }
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnsBreezeCharge(TestContext c) { returnsSpecial(c, EntityType.BREEZE_WIND_CHARGE); }
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnsTrident(TestContext c) { returnsSpecial(c, EntityType.TRIDENT); }
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnsShulkerBullet(TestContext c) { returnsSpecial(c, EntityType.SHULKER_BULLET); }
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnsPotion(TestContext c) { returnsSpecial(c, EntityType.POTION); }

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnedPotionBreaksAndAppliesOriginalEffect(TestContext c) {
		var p = aim(c);
		var potion = (net.minecraft.entity.projectile.thrown.PotionEntity) incoming(c, p, EntityType.POTION);
		ItemStack bottle = new ItemStack(Items.SPLASH_POTION);
		bottle.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS,
			new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.SLOWNESS));
		potion.setItem(bottle);
		var victim = new net.minecraft.entity.mob.ZombieEntity[1];
		c.waitAndRun(4, () -> {
			var zombie = c.spawnMob(EntityType.ZOMBIE, 2, 1, 5);
			zombie.setAiDisabled(true); zombie.setNoGravity(true); zombie.setPosition(p.getPos().add(0, 0, 3));
			victim[0] = zombie;
			p.setPitch(15); // Downward aim makes a straight projectile hit the ground.
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
		});
		c.waitAndRun(24, () -> {
			c.assertTrue(victim[0].hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS), "Returned splash potion must break and apply its stored effect"); c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140)
	public void returnedShulkerBulletFindsHostileAndLevitates(TestContext c) {
		var p = aim(c);
		// Keep a wide homing search clear of adjacent concurrently running test structures.
		p.setPosition(p.getPos().add(0, 16, 0));
		incoming(c, p, EntityType.SHULKER_BULLET);
		var victim = new net.minecraft.entity.mob.GhastEntity[1];
		c.waitAndRun(4, () -> {
			var zombie = c.spawnMob(EntityType.GHAST, 2, 1, 5);
			zombie.setAiDisabled(true); zombie.setNoGravity(true); zombie.setPosition(p.getPos().add(1, 0, 6));
			victim[0] = zombie;
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
		});
		c.waitAndRun(100, () -> {
			c.assertTrue(victim[0].hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION), "Returned shulker bullet must home onto visible hostile and apply levitation; health=" + victim[0].getHealth() + " pos=" + victim[0].getPos()); c.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnedFireballKillsGhastAndGrantsVanillaAdvancement(TestContext c) {
		var p = aim(c); incoming(c, p, EntityType.FIREBALL);
		var victim = new net.minecraft.entity.mob.GhastEntity[1];
		c.waitAndRun(4, () -> {
			var ghast = c.spawnMob(EntityType.GHAST, 2, 1, 5);
			ghast.setAiDisabled(true); ghast.setNoGravity(true); ghast.setPosition(p.getPos().add(0, 0, 7));
			victim[0] = ghast;
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
		});
		c.waitAndRun(30, () -> {
			c.assertTrue(!victim[0].isAlive(), "Returned fireball must kill its ghast target");
			for (String id : new String[]{"mc_head_function:frog_projectile_parry", "minecraft:nether/return_to_sender"}) {
				var advancement = c.getWorld().getServer().getAdvancementLoader().get(net.minecraft.util.Identifier.of(id));
				c.assertTrue(p.getAdvancementTracker().getProgress(advancement).isDone(), "Return must grant " + id);
			}
			c.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void returnedArrowDoesNotAwardChallengeForFriendlyRangedMob(TestContext c) {
		var p = aim(c); incoming(c, p, EntityType.ARROW);
		var victim = new net.minecraft.entity.passive.SnowGolemEntity[1];
		c.waitAndRun(4, () -> {
			var golem = c.spawnMob(EntityType.SNOW_GOLEM, 2, 1, 5);
			golem.setAiDisabled(true); golem.setNoGravity(true); golem.setHealth(1); golem.setPosition(p.getPos().add(0, 0, 3));
			victim[0] = golem;
			SkillHandler.onSkill(p, true); SkillHandler.onSkill(p, false);
		});
		c.waitAndRun(18, () -> {
			c.assertTrue(!victim[0].isAlive(), "Returned arrow must hit the friendly ranged target");
			var advancement = c.getWorld().getServer().getAdvancementLoader().get(net.minecraft.util.Identifier.of("mc_head_function", "frog_projectile_parry"));
			c.assertTrue(!p.getAdvancementTracker().getProgress(advancement).isDone(), "Friendly ranged mob must not count as a ranged monster"); c.complete();
		});
	}

}
