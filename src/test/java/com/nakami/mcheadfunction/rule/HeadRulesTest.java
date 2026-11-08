package com.nakami.mcheadfunction.rule;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadRulesTest {
	@Test
	void headRegionIsUpperThird() {
		assertFalse(HeadRules.isHeadRegion(1.0, 0.0, 2.0));
		assertTrue(HeadRules.isHeadRegion(1.7, 0.0, 2.0));
	}

	@Test
	void endermanDoesNotEatProtectedBlocks() {
		assertFalse(HeadRules.canEndermanEat(true, false, false));
		assertFalse(HeadRules.canEndermanEat(false, true, false));
		assertFalse(HeadRules.canEndermanEat(false, false, true));
		assertTrue(HeadRules.canEndermanEat(false, false, false));
	}

	@Test
	void thunderstormHalvesLightningCharge() {
		assertEquals(50, HeadRules.lightningChargeNeeded(true));
		assertEquals(100, HeadRules.lightningChargeNeeded(false));
	}

	@Test
	void throwerIsExcluded() {
		UUID id = UUID.fromString("00000000-0000-0000-0000-000000000009");
		assertTrue(HeadRules.isThrower(id, id));
		assertFalse(HeadRules.isThrower(id, UUID.fromString("00000000-0000-0000-0000-000000000008")));
	}

	@Test
	void pigSaturationBonusUsesTheFoodsSaturation() {
		assertEquals(6.0F, HeadRules.extraPigSaturation(6.0F));
		assertEquals(0.0F, HeadRules.extraPigSaturation(0.0F));
	}

	@Test
	void pigStuffedLastsTenSeconds() {
		assertEquals(200, HeadRules.PIG_STUFFED_TICKS);
	}

	@Test
	void pigSwallowStartsAtTheHandAndEndsInTheMouth() {
		assertEquals(0.0F, HeadRules.pigSwallowProgress(HeadRules.PIG_STUFFED_TICKS, 0));
		assertEquals(0.5F, HeadRules.pigSwallowProgress(HeadRules.PIG_STUFFED_TICKS - HeadRules.PIG_SWALLOW_TICKS / 2, 0), 0.001F);
		assertEquals(1.0F, HeadRules.pigSwallowProgress(HeadRules.PIG_STUFFED_TICKS - HeadRules.PIG_SWALLOW_TICKS, 0));
		assertEquals(1.0F, HeadRules.pigSwallowProgress(1, 0));
	}

	@Test
	void pigFatnessInflatesThenHoldsThenDeflates() {
		assertEquals(0.0F, HeadRules.pigFatness(0, 0));
		assertEquals(0.0F, HeadRules.pigFatness(HeadRules.PIG_STUFFED_TICKS, 0));
		assertEquals(1.0F, HeadRules.pigFatness(HeadRules.PIG_STUFFED_TICKS - HeadRules.PIG_SWALLOW_TICKS, 0));
		assertEquals(1.0F, HeadRules.pigFatness(50, 0));
		assertEquals(0.5F, HeadRules.pigFatness(3, 0), 0.001F);
	}

	@Test
	void sheepQuietsWhenFarFromSensor() {
		assertFalse(HeadRules.sheepQuiets(1.0));
		assertTrue(HeadRules.sheepQuiets(4.01));
	}

	@Test
	void meleeHitIgnoresProjectiles() {
		assertTrue(HeadRules.isMeleeHit(true, false));
		assertFalse(HeadRules.isMeleeHit(false, false));
		assertFalse(HeadRules.isMeleeHit(true, true));
	}

	@Test
	void endermanEatsFourTimesPerSecond() {
		assertFalse(HeadRules.shouldEatThisTick(1));
		assertTrue(HeadRules.shouldEatThisTick(5));
		assertFalse(HeadRules.shouldEatThisTick(6));
		assertTrue(HeadRules.shouldEatThisTick(10));
	}

	@Test
	void frogChewsEveryHalfSecond() {
		assertFalse(HeadRules.frogChewsThisTick(0));
		assertFalse(HeadRules.frogChewsThisTick(9));
		assertTrue(HeadRules.frogChewsThisTick(10));
		assertFalse(HeadRules.frogChewsThisTick(11));
		assertTrue(HeadRules.frogChewsThisTick(20));
	}

	@Test
	void frogMouthShrinksAsTheMealIsDigested() {
		float full = HeadRules.frogMouthSize(0.6F, 1.95F, 1);
		float empty = HeadRules.frogMouthSize(0.6F, 1.95F, 0);
		assertTrue(full > empty);
		assertEquals(full * 0.78F, empty, 0.001F);
		assertTrue(HeadRules.frogMouthSize(2.0F, 2.0F, 1) > HeadRules.frogMouthSize(0.4F, 0.7F, 1));
	}

	@Test
	void frogChewSnapsHarderOnABite() {
		float idle = HeadRules.frogChew(1, 0, false);
		float bite = HeadRules.frogChew(1, 10, false);
		assertTrue(bite > idle);
		assertEquals(idle + 0.12F, bite, 0.001F);
		assertTrue(Math.abs(HeadRules.frogChew(1, 0, true)) > Math.abs(idle));
	}

	@Test
	void frogChewCameraNodsOnABite() {
		assertTrue(HeadRules.frogChewPitch(0, 10) > HeadRules.frogChewPitch(0, 0));
		assertTrue(HeadRules.frogChewPitch(0, 10) < 4);
	}

	@Test
	void creeperPrimingFinishesBeforeTheFuseFlashes() {
		assertEquals(0.0F, HeadRules.creeperPrimeProgress(HeadRules.CREEPER_FUSE_TICKS, true));
		assertEquals(0.5F, HeadRules.creeperPrimeProgress(HeadRules.CREEPER_FUSE_TICKS - HeadRules.CREEPER_PRIME_TICKS / 2, true), 0.001F);
		assertEquals(1.0F, HeadRules.creeperPrimeProgress(HeadRules.CREEPER_FUSE_TICKS - HeadRules.CREEPER_PRIME_TICKS, true));
		assertEquals(1.0F, HeadRules.creeperPrimeProgress(1, true));
		assertEquals(1.0F, HeadRules.creeperPrimeProgress(HeadRules.CREEPER_FUSE_TICKS, false));
		assertFalse(HeadRules.creeperFuseLit(HeadRules.CREEPER_FUSE_TICKS, true));
		assertFalse(HeadRules.creeperFuseLit(HeadRules.CREEPER_FUSE_TICKS - HeadRules.CREEPER_PRIME_TICKS + 1, true));
		assertTrue(HeadRules.creeperFuseLit(HeadRules.CREEPER_FUSE_TICKS - HeadRules.CREEPER_PRIME_TICKS, true));
		assertTrue(HeadRules.creeperFuseLit(HeadRules.CREEPER_FUSE_TICKS, false));
	}

	@Test
	void connectedCollectIgnoresVisitedAirWhenCapping() {
		record Cell(int x, int y) {
		}
		java.util.Set<Cell> stone = new java.util.HashSet<>();
		for (int x = 0; x < 20; x++) {
			stone.add(new Cell(x, 0));
		}
		var found = HeadRules.collectConnected(
			new Cell(0, 0),
			HeadRules.ENDERMAN_EAT_MAX,
			stone::contains,
			cell -> java.util.List.of(
				new Cell(cell.x + 1, cell.y),
				new Cell(cell.x - 1, cell.y),
				new Cell(cell.x, cell.y + 1),
				new Cell(cell.x, cell.y - 1)
			)
		);
		assertEquals(16, found.size());
		assertTrue(found.contains(new Cell(15, 0)));
	}
}
