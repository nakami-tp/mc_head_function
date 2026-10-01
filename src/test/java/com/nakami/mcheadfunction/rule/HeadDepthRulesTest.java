package com.nakami.mcheadfunction.rule;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeadDepthRulesTest {
	@Test void chargeSteeringTakesShortestLimitedTurn() {
		assertEquals(1.8F, HeadDepthRules.steer(0, 90, 1.8F), 0.001);
		assertEquals(181, HeadDepthRules.steer(179, -179, 3), 0.001);
		assertEquals(-181, HeadDepthRules.steer(-179, 179, 3), 0.001);
	}
	@Test void rabbitProgressesAndNeverExceedsOneHundredBlocks() {
		assertEquals(1.25, HeadDepthRules.rabbitHeight(1));
		assertEquals(10, HeadDepthRules.rabbitHeight(4));
		assertEquals(100, HeadDepthRules.rabbitHeight(8));
		assertEquals(100, HeadDepthRules.rabbitHeight(1000));
		assertEquals(2.05, HeadDepthRules.rabbitSpeedMultiplier(1000), 0.001);
		for (int chain = 1; chain <= 12; chain++) {
			double velocity = HeadDepthRules.rabbitJumpVelocity(chain);
			double height = 0;
			while (velocity > 0) { height += velocity; velocity = (velocity - 0.08) * 0.98; }
			assertEquals(HeadDepthRules.rabbitHeight(chain), height, 0.001);
		}
	}
}
