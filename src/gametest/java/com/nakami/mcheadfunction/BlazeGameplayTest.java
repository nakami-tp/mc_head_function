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

public class BlazeGameplayTest implements FabricGameTest {
	private ServerPlayerEntity player(TestContext c) {
		var p = c.createMockCreativeServerPlayerInWorld();
		var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
		p.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		p.setNoGravity(true);
		p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BLAZE)));
		return p;
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
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 170)
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
			var target = cow(c, 2, 10);
			c.waitAndRun(15, () -> {
				c.assertTrue(target.getHealth() == 100 && !target.isOnFire(), "Temporary fire must expire after six seconds");
				c.assertTrue(c.getBlockState(new BlockPos(2, 2, 10)).isAir(), "Fire trail must not place fire blocks");
				c.complete();
			});
		});
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
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 175)
	public void overheatRequiresReleaseAndFreshPress(TestContext c) {
		var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(85, () -> {
			var target = cow(c, 2, 5);
			c.waitAndRun(45, () -> {
				c.assertTrue(target.getHealth() == 100, "Holding R through cooldown must not restart fire");
				SkillHandler.onSkill(p, false);
				c.waitAndRun(8, () -> {
					c.assertTrue(target.getHealth() == 100, "Overheated release must not emit a wave");
					SkillHandler.onSkill(p, true);
					c.waitAndRun(5, () -> {
						c.assertTrue(target.getHealth() < 100, "Fresh press after cooling must work");
						SkillHandler.onSkill(p, false); c.complete();
					});
				});
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
	@GameTest(templateName = "mc_head_function:blaze_arena", tickLimit = 120)
	public void changingHeadsDoesNotResetOverheatCooldown(TestContext c) {
		var p = player(c); SkillHandler.onSkill(p, true);
		c.waitAndRun(85, () -> {
			SkillHandler.onSkill(p, false); p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			c.waitAndRun(5, () -> {
				p.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.BLAZE)));
				var target = cow(c, 2, 5); SkillHandler.onSkill(p, true);
				c.waitAndRun(15, () -> {
					c.assertTrue(target.getHealth() == 100, "Another blaze head cannot bypass overheat cooldown");
					SkillHandler.onSkill(p, false); c.complete();
				});
			});
		});
	}
}
