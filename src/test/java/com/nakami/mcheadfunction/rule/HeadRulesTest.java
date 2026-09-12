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
}
