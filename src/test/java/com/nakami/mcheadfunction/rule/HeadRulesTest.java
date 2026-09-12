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
