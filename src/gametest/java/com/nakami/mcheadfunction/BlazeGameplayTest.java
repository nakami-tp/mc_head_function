package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.wear.SkillHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Full-core casts use separate batches so ten-block blasts cannot disturb neighbouring fixtures. */
public class BlazeGameplayTest implements FabricGameTest {
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 130, batchId = "blaze-core-moltencorecoverseightblocksbutnotbeyondten")
	public void moltenCoreCoversEightBlocksButNotBeyondTen(TestContext c) {
		var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(95, () -> {
			var center = cow(c, 2, 9);
			var edge = cow(c, 2, 17);
			var outside = cow(c, 2, 20);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(12, () -> {
				c.assertTrue(center.getHealth() <= 70 && center.getHealth() >= 69, "Core center must deal thirty damage: " + center.getHealth());
				c.assertTrue(edge.getHealth() <= 84 && edge.getHealth() > 70, "Expanded blast must reach past the old five-block radius with falloff: " + edge.getHealth());
				c.assertTrue(outside.getHealth() == 100 && !outside.isOnFire(), "Blast must not reach beyond ten blocks");
				c.complete();
			});
		});
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 390, batchId = "blaze-core-corerecoversaftereightsecondsandcanimmediatelychargeagain")
	public void coreRecoversAfterEightSecondsAndCanImmediatelyChargeAgain(TestContext c) {
		var p = player(c);
		for (int x = -2; x <= 7; x++) for (int y = 1; y <= 7; y++) c.setBlockState(new BlockPos(x, y, 15), Blocks.STONE);
		SkillHandler.onSkill(p, true);
		c.waitAndRun(95, () -> {
			c.assertTrue(!p.isSneaking() && PlayerHeadAccess.state(p).blazeCoreCharge == 30, "Standing R hold must ready the core");
			SkillHandler.onSkill(p, false);
			var state = PlayerHeadAccess.state(p);
			c.assertTrue(state.blazeCooldown == 160 && state.blazeHeat == 0, "Firing must enter an eight-second stop immediately");
			var tag = new net.minecraft.nbt.NbtCompound(); state.write(tag);
			var restored = new PlayerHeadState(); restored.read(tag);
			c.assertTrue(restored.blazeCooldown == 160, "Reload must preserve all eight seconds of stop time");
			SkillHandler.onSkill(p, true);
			c.waitAndRun(120, () -> {
				c.assertTrue(state.blazeCooldown > 0 && !state.blazeSpraying && state.blazeHeat == 0, "Core must still be stopped after six seconds");
			});
			c.waitAndRun(161, () -> {
				c.assertTrue(state.blazeCooldown == 0 && !state.blazeSpraying && !state.blazeCoreCharging, "Holding through recovery must not restart fire");
				SkillHandler.onSkill(p, false); SkillHandler.onSkill(p, true);
				c.waitAndRun(95, () -> {
					c.assertTrue(state.blazeCoreCharging && state.blazeCoreCharge == 30, "Fresh R after eight seconds must ready another core without any separate cooldown");
					SkillHandler.onSkill(p, false);
					c.assertTrue(state.blazeCooldown == 160, "Second core must spend only the same eight-second stop");
					c.complete();
				});
			});
		});
	}
	private void chargeCore(ServerPlayerEntity player) {
		player.setSneaking(false); SkillHandler.onSkill(player, true);
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 125, batchId = "blaze-core-moltencorehitshardandpersistscooldown")
	public void moltenCoreHitsHardAndPersistsCooldown(TestContext c) {
		floor(c); var p = player(c); var target = cow(c, 2, 12); var outer = cow(c, 7, 12); chargeCore(p);
		c.waitAndRun(95, () -> {
			c.assertTrue(target.getHealth() == 100, "Normal spray must not reach this distant target before core release");
			SkillHandler.onSkill(p, false);
			c.waitAndRun(10, () -> {
				c.assertTrue(target.getHealth() <= 70 && target.getHealth() >= 69, "Core must deliver one heavy central hit: " + target.getHealth());
				c.assertTrue(outer.getHealth() < 84 && outer.getHealth() > target.getHealth(), "Blast must damage off-axis targets with distance falloff: " + outer.getHealth());
				var state = PlayerHeadAccess.state(p);
				c.assertTrue(state.blazeCooldown > 140, "Core must consume the shared eight-second stop");
				var nbt = new net.minecraft.nbt.NbtCompound(); state.write(nbt);
				nbt.getCompound("mhf").putInt("blazeCoreCooldown", 400); // Legacy save data is deliberately ignored.
				var restored = new PlayerHeadState(); restored.read(nbt);
				c.assertTrue(restored.blazeCooldown == state.blazeCooldown && !restored.blazeCoreCharging, "Reload must preserve cooldown without resuming a charge");
				p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
				p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BLAZE)));
				SkillHandler.onSkill(p, true);
				c.assertFalse(state.blazeCoreCharging, "Swapping heads must not bypass cooldown");
				SkillHandler.onSkill(p, false); c.complete();
			});
		});
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 105)
	public void partialCoreCompressionReleasesSurgeWithoutSpendingCooldown(TestContext c) {
		var p = player(c); chargeCore(p);
		c.waitAndRun(65, () -> {
			c.assertTrue(PlayerHeadAccess.state(p).blazeCoreCharging && !PlayerHeadAccess.state(p).blazeSpraying, "Long R hold must transition from spraying to compression");
			var target = cow(c, 2, 11);
			c.waitAndRun(10, () -> {
				c.assertTrue(target.getHealth() == 100, "Core compression must stop the spray");
				SkillHandler.onSkill(p, false);
				c.waitAndRun(8, () -> {
					c.assertTrue(target.getHealth() <= 94 && target.getHealth() >= 93, "Partial compression must release the ready surge, not a core: " + target.getHealth());
					c.assertTrue(PlayerHeadAccess.state(p).blazeCooldown == 0, "Surge must not spend core or overheat cooldown");
					c.complete();
				});
			});
		});
	}
	private void coreBlocked(TestContext c, net.minecraft.block.Block block) {
		floor(c); var p = player(c); var target = cow(c, 2, 10);
		chargeCore(p);
		c.waitAndRun(95, () -> {
			c.assertTrue(PlayerHeadAccess.state(p).blazeCoreCharge == 30, "Core must be ready before testing collision");
			for (int x = -3; x <= 7; x++) for (int y = 2; y <= 7; y++) c.setBlockState(new BlockPos(x, y, 7), block);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(15, () -> {
				c.assertTrue(target.getHealth() == 100 && !target.isOnFire(), "Wall/water must block core and blast damage");
				c.assertTrue(c.getBlockState(new BlockPos(2, 3, 7)).isOf(block), "Core must not destroy the barrier");
				c.complete();
			});
		});
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 125, batchId = "blaze-core-moltencorecannotblastthroughwall")
	public void moltenCoreCannotBlastThroughWall(TestContext c) { coreBlocked(c, Blocks.STONE); }
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 125, batchId = "blaze-core-moltencoreisextinguishedbywater")
	public void moltenCoreIsExtinguishedByWater(TestContext c) { coreBlocked(c, Blocks.WATER); }
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 125, batchId = "blaze-core-moltencoreunequipcancelsevenafterready")
	public void moltenCoreUnequipCancelsEvenAfterReady(TestContext c) {
		var p = player(c); chargeCore(p);
		c.waitAndRun(95, () -> {
			var target = cow(c, 2, 5);
			p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(15, () -> {
				c.assertTrue(target.getHealth() == 100, "Unequipped ready core must cancel without a blast");
				c.complete();
			});
		});
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 145, batchId = "blaze-core-moltencorechainsonlyownedtrailsanddeduplicatesblast")
	public void moltenCoreChainsOnlyOwnedTrailsAndDeduplicatesBlast(TestContext c) {
		floor(c); var p = player(c); var other = player(c);
		var start = net.minecraft.util.math.Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 8)));
		com.nakami.mcheadfunction.blaze.BlazeFire.trail(p.getServerWorld(), p, start, start.add(0, 0, 10));
		var foreign = net.minecraft.util.math.Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(5, 3, 8)));
		com.nakami.mcheadfunction.blaze.BlazeFire.trail(p.getServerWorld(), other, foreign, foreign.add(0, 0, 10));
		other.discard(); // Removes the caster from the flight lane; its trail is still foreign.
		var chained = cow(c, 2, 19); chained.setPosition(chained.getPos().add(0, 3, 0));
		var untouched = cow(c, 5, 19); untouched.setPosition(untouched.getPos().add(0, 3, 0));
		chargeCore(p);
		c.waitAndRun(95, () -> {
			var impact = cow(c, 2, 9);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(25, () -> {
				c.assertTrue(impact.getHealth() >= 68 && impact.getHealth() <= 70, "Blast and chain must not damage the same target twice: " + impact.getHealth());
				c.assertTrue(chained.getHealth() < 100, "Core blast must ignite the owned trail to its far end");
				c.assertTrue(untouched.getHealth() == 100, "Core must not propagate along a foreign trail");
				c.complete();
			});
		});
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 65)
	public void releaseReachesNineBlocksWithinSixTicks(TestContext c) {
		floor(c); var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(45, () -> {
			var target = cow(c, 2, 11);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(6, () -> {
				c.assertTrue(target.getHealth() <= 94, "Released fire must reach nine blocks in six ticks: " + target.getHealth());
				c.assertTrue(target.getVelocity().z > 0.35, "Release must push forward distinctly");
				c.complete();
			});
		});
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 95)
	public void nearOverheatReleaseReachesElevenBlocks(TestContext c) {
		floor(c); var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(59, () -> {
			var target = cow(c, 2, 13);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(6, () -> {
				c.assertTrue(target.getHealth() <= 94, "Extra heat must extend the released fire to eleven blocks");
				c.complete();
			});
		});
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 65)
	public void earlyHotReleaseDoesNotReachElevenBlocks(TestContext c) {
		floor(c); var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(45, () -> {
			var target = cow(c, 2, 13);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(8, () -> {
				c.assertTrue(target.getHealth() == 100, "Early release must not get full-charge range");
				c.complete();
			});
		});
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 70)
	public void releasedFireCannotCrossWallOrWater(TestContext c) {
		floor(c); var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(45, () -> {
			for (int x = -2; x <= 7; x++) for (int y = 2; y <= 6; y++) {
				c.setBlockState(new BlockPos(x, y, 6), x < 3 ? Blocks.STONE : Blocks.WATER);
			}
			var behindWall = cow(c, 2, 10);
			var behindWater = cow(c, 4, 10);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(8, () -> {
				c.assertTrue(behindWall.getHealth() == 100 && !behindWall.isOnFire(), "Wall must stop the release");
				c.assertTrue(behindWater.getHealth() == 100 && !behindWater.isOnFire(), "Water must stop the release");
				c.complete();
			});
		});
	}

	private ServerPlayerEntity player(TestContext c) {
		var p = c.createMockCreativeServerPlayerInWorld();
		var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
		p.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		p.setNoGravity(true);
		p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BLAZE)));
		return p;
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 110)
	public void waveIgnitesTwoSeparateTrailsIntoTallPillars(TestContext c) {
		floor(c); var p = player(c);
		for (int x : new int[]{0, 4}) {
			var a = c.getAbsolutePos(new BlockPos(x, 3, 6));
			com.nakami.mcheadfunction.blaze.BlazeFire.trail(p.getServerWorld(), p,
				net.minecraft.util.math.Vec3d.ofCenter(a), net.minecraft.util.math.Vec3d.ofCenter(a.add(0, 0, 9)));
		}
		var left = cow(c, 0, 13); left.setPosition(left.getPos().add(0, 3, 0));
		var right = cow(c, 4, 13); right.setPosition(right.getPos().add(0, 3, 0));
		SkillHandler.onSkill(p, true);
		c.waitAndRun(45, () -> {
			SkillHandler.onSkill(p, false);
			c.waitAndRun(40, () -> {
				c.assertTrue(left.getHealth() < 100, "Left disconnected trail must erupt above normal wave height");
				c.assertTrue(right.getHealth() < 100, "Right disconnected trail must also erupt");
				c.complete();
			});
		});
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 40)
	public void sprayDealsDirectDamageAndCannotBurnThroughWall(TestContext c) {
		var p = player(c);
		var visible = c.spawnMob(EntityType.COW, 2, 2, 5);
		visible.setAiDisabled(true); visible.setNoGravity(true);
		var hidden = c.spawnMob(EntityType.COW, 2, 2, 7);
		hidden.setAiDisabled(true); hidden.setNoGravity(true);
		for (int x = 0; x < 5; x++) for (int y = 1; y < 6; y++) c.setBlockState(new BlockPos(x, y, 6), Blocks.STONE);
		SkillHandler.onSkill(p, true);
		c.waitAndRun(5, () -> {
			c.assertTrue(visible.getHealth() <= 8, "Spray must immediately deal at least 2 direct damage");
			c.assertFalse(hidden.isOnFire(), "Wall must block ignition as well as particles");
			c.assertTrue(hidden.getHealth() == 10, "Wall must block damage");
			SkillHandler.onSkill(p, false);
			c.complete();
		});
	}

	private net.minecraft.entity.passive.CowEntity cow(TestContext c, int x, int z) {
		var cow = c.spawnMob(EntityType.COW, x, 2, z);
		cow.setAiDisabled(true); cow.setNoGravity(true);
		cow.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
		cow.setHealth(100);
		return cow;
	}
	private void floor(TestContext c) {
		for (int x = 0; x <= 5; x++) for (int z = 0; z <= 19; z++) {
			c.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
			for (int y = 2; y <= 6; y++) c.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
		}
	}
	private void throwHead(ServerPlayerEntity p) {
		p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(HeadItems.item(HeadType.BLAZE)));
		com.nakami.mcheadfunction.throwing.ThrowHandler.throwHeldHead(p);
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 290)
	public void horizontalThrowLeavesDamagingGroundThenExpires(TestContext c) {
		floor(c); var p = player(c); throwHead(p);
		c.waitAndRun(20, () -> {
			var target = cow(c, 2, 10);
			c.waitAndRun(15, () -> {
				c.assertTrue(target.getHealth() < 100, "Recovered horizontal throw must leave damaging ground");
				target.discard();
			});
		});
		c.waitAndRun(145, () -> {
			var stillBurning = cow(c, 2, 10);
			c.waitAndRun(15, () -> {
				c.assertTrue(stillBurning.getHealth() < 100, "Fire trail must still damage after six seconds");
				stillBurning.discard();
			});
		});
		c.waitAndRun(265, () -> {
			var target = cow(c, 2, 10);
			c.waitAndRun(15, () -> {
				c.assertTrue(target.getHealth() == 100 && !target.isOnFire(), "Temporary fire must expire after twelve seconds");
				c.assertTrue(c.getBlockState(new BlockPos(2, 2, 10)).isAir(), "Fire trail must not ignite non-flammable stone");
				c.complete();
			});
		});
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 40)
	public void sprayIgnitesFlammableWall(TestContext c) {
		var p = player(c);
		for (int y = 2; y <= 4; y++) c.setBlockState(new BlockPos(2, y, 5), Blocks.OAK_PLANKS);
		SkillHandler.onSkill(p, true);
		c.waitAndRun(8, () -> {
			c.assertTrue(hasFireNear(c, new BlockPos(2, 3, 5)), "Spray must ignite flammable oak");
			SkillHandler.onSkill(p, false);
			c.complete();
		});
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 40)
	public void throwIgnitesFlammableTrail(TestContext c) {
		for (int x = 0; x <= 5; x++) for (int z = 0; z <= 19; z++) {
			c.setBlockState(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
			for (int y = 2; y <= 6; y++) c.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
		}
		var p = player(c);
		throwHead(p);
		c.waitAndRun(20, () -> {
			boolean fire = false;
			for (int z = 3; z <= 14; z++) {
				if (hasFireNear(c, new BlockPos(2, 2, z))) fire = true;
			}
			c.assertTrue(fire, "Thrown fire trail must ignite oak planks");
			c.complete();
		});
	}

	private static boolean hasFireNear(TestContext c, BlockPos origin) {
		for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
			var state = c.getBlockState(origin.add(dx, dy, dz));
			if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) return true;
		}
		return false;
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 85)
	public void hotReleaseHitsWideTargetOnlyOnce(TestContext c) {
		var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(45, () -> {
			var target = cow(c, 4, 7);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(15, () -> {
				c.assertTrue(target.getHealth() <= 94 && target.getHealth() >= 93, "Hot release must hit a wide target once: " + target.getHealth());
				c.assertTrue(target.getVelocity().horizontalLength() > 0, "Wave should push the target");
				c.complete();
			});
		});
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 110)
	public void hotWaveTravelsBeyondBreathAlongOwnTrail(TestContext c) {
		floor(c); var p = player(c); throwHead(p); SkillHandler.onSkill(p, true);
		c.waitAndRun(45, () -> {
			// Raised above the low ground fire, but inside the propagated wall.
			var target = cow(c, 2, 13); target.setPosition(target.getPos().add(0, 1, 0));
			SkillHandler.onSkill(p, false);
			c.waitAndRun(35, () -> {
				c.assertTrue(target.getHealth() <= 94 && target.getHealth() >= 92, "Wave must propagate to distant target once: " + target.getHealth());
				c.complete();
			});
		});
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 105)
	public void coldReleaseAndUnequipCannotReleaseWave(TestContext c) {
		var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(15, () -> {
			var target = cow(c, 4, 7);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(10, () -> {
				c.assertTrue(target.getHealth() == 100, "Cold release must not emit a wide wave");
				target.discard(); SkillHandler.onSkill(p, true);
			});
		});
		c.waitAndRun(70, () -> {
			var target = cow(c, 4, 7);
			p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			SkillHandler.onSkill(p, false);
			c.waitAndRun(10, () -> {
				c.assertTrue(target.getHealth() == 100, "Unequipped hot head cannot emit a wave");
				c.complete();
			});
		});
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 110)
	public void waveCannotPropagateAlongAnotherPlayersTrail(TestContext c) {
		floor(c); var owner = player(c); throwHead(owner);
		var caster = player(c);
		SkillHandler.onSkill(caster, true);
		c.waitAndRun(45, () -> {
			var target = cow(c, 2, 13); target.setPosition(target.getPos().add(0, 1, 0));
			SkillHandler.onSkill(caster, false);
			c.waitAndRun(35, () -> {
				c.assertTrue(target.getHealth() == 100, "Fire wave must not propagate along another player's ground");
				c.complete();
			});
		});
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 55)
	public void trailStopsAtWallAndDoesNotReachFromHighAir(TestContext c) {
		floor(c); var p = player(c);
		for (int x = 0; x <= 5; x++) for (int y = 2; y <= 6; y++) c.setBlockState(new BlockPos(x, y, 6), Blocks.STONE);
		throwHead(p);
		c.waitAndRun(15, () -> {
			var behind = cow(c, 2, 8);
			p.setPosition(p.getPos().add(0, 5, 0)); throwHead(p);
			c.waitAndRun(25, () -> {
				c.assertTrue(behind.getHealth() == 100 && !behind.isOnFire(), "Wall and high throws must not place damaging ground behind wall");
				c.complete();
			});
		});
	}

	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 30)
	public void thrownFireCannotHitTargetTouchingThinWall(TestContext c) {
		floor(c); var p = player(c);
		for (int x = 0; x <= 5; x++) for (int y = 2; y <= 5; y++) c.setBlockState(new BlockPos(x, y, 6), Blocks.GLASS_PANE.getDefaultState().with(net.minecraft.state.property.Properties.EAST, true).with(net.minecraft.state.property.Properties.WEST, true));
		var target = cow(c, 2, 6);
		var pos = c.getAbsolutePos(new BlockPos(2, 2, 6));
		target.setPosition(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.95);
		throwHead(p);
		c.waitAndRun(12, () -> {
			c.assertTrue(target.getHealth() == 100 && !target.isOnFire(), "Thin wall must block thrown ignition of touching target");
			c.complete();
		});
	}
	private void cancelsHotSpray(TestContext c, java.util.function.Consumer<ServerPlayerEntity> interrupt) {
		var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(45, () -> {
			var target = cow(c, 2, 5);
			interrupt.accept(p);
			c.waitAndRun(5, () -> {
				SkillHandler.onSkill(p, false);
				c.waitAndRun(10, () -> {
					c.assertTrue(target.getHealth() == 100 && !target.isOnFire(), "Interruption must cancel spray and suppress release wave: health=" + target.getHealth() + " water=" + p.isTouchingWater() + " spectator=" + p.isSpectator() + " mode=" + p.interactionManager.getGameMode());
					c.complete();
				});
			});
		});
	}	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 80)
	public void stunCancelsHotSpray(TestContext c) {
		cancelsHotSpray(c, p -> p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(com.nakami.mcheadfunction.wear.GoatCharge.STUN, 40)));
	}	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 80)
	public void deathCancelsHotSpray(TestContext c) {
		cancelsHotSpray(c, p -> p.setHealth(0));
	}
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 210, batchId = "blaze-core-changingheadsdoesnotresetoverheatcooldown")
	public void changingHeadsDoesNotResetOverheatCooldown(TestContext c) {
		var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(95, () -> {
			SkillHandler.onSkill(p, false); p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			c.waitAndRun(5, () -> {
				p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BLAZE)));
				var target = cow(c, 2, 5); SkillHandler.onSkill(p, true);
				c.waitAndRun(90, () -> {
					c.assertTrue(target.getHealth() == 100 && PlayerHeadAccess.state(p).blazeCooldown > 0, "Another head must not bypass the eight-second stop");
					SkillHandler.onSkill(p, false); c.complete();
				});
			});
		});
	}
}
