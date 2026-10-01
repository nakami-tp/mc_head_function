package com.nakami.mcheadfunction.rule;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeadDepthRulesTest {
	@Test void chargeSteeringTakesShortestLimitedTurn() {
		assertEquals(1.8F, HeadDepthRules.steer(0, 90, 1.8F), 0.001);
		assertEquals(181, HeadDepthRules.steer(179, -179, 3), 0.001);
		assertEquals(-181, HeadDepthRules.steer(-179, 179, 3), 0.001);
	}
	@Test void rabbitProgressesAndNeverExceedsEightyBlocks() {
		assertEquals(1.25, HeadDepthRules.rabbitHeight(1));
		assertTrue(HeadDepthRules.rabbitHeight(15) < 80);
		assertEquals(80, HeadDepthRules.rabbitHeight(16));
		assertEquals(1, HeadDepthRules.rabbitSpeedMultiplier(0));
		assertTrue(HeadDepthRules.rabbitSpeedMultiplier(1) > 1);
		assertTrue(HeadDepthRules.rabbitHeight(8) < 80);
		assertEquals(80, HeadDepthRules.rabbitHeight(1000));
		assertEquals(1.15, HeadDepthRules.rabbitSpeedMultiplier(1), 0.001);
		assertEquals(1.99, HeadDepthRules.rabbitSpeedMultiplier(8), 0.001);
		assertEquals(2.95, HeadDepthRules.rabbitSpeedMultiplier(16), 0.001);
		assertEquals(2.95, HeadDepthRules.rabbitSpeedMultiplier(1000), 0.001);
		for (int chain = 1; chain <= 20; chain++) {
			if (chain > 1 && chain <= 16) {
				assertTrue(HeadDepthRules.rabbitHeight(chain) > HeadDepthRules.rabbitHeight(chain - 1));
				assertTrue(HeadDepthRules.rabbitSpeedMultiplier(chain) > HeadDepthRules.rabbitSpeedMultiplier(chain - 1));
			}
			double velocity = HeadDepthRules.rabbitJumpVelocity(chain);
			double height = 0;
			while (velocity > 0) { height += velocity; velocity = (velocity - 0.08) * 0.98; }
			assertEquals(HeadDepthRules.rabbitHeight(chain), height, 0.001);
		}
	}
}
