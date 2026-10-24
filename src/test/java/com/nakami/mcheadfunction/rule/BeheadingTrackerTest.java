package com.nakami.mcheadfunction.rule;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeheadingTrackerTest {
	private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void thirdHitInsideWindowBeheads() {
		BeheadingTracker tracker = new BeheadingTracker();
		assertEquals(1, tracker.registerHit(PLAYER, TARGET, 0));
		assertEquals(2, tracker.registerHit(PLAYER, TARGET, 400));
		assertEquals(3, tracker.registerHit(PLAYER, TARGET, 800));
	}

	@Test
	void windowResetAfterTwoSeconds() {
		BeheadingTracker tracker = new BeheadingTracker();
		assertEquals(1, tracker.registerHit(PLAYER, TARGET, 0));
		assertEquals(2, tracker.registerHit(PLAYER, TARGET, 400));
		assertEquals(1, tracker.registerHit(PLAYER, TARGET, 2100));
		assertEquals(2, tracker.registerHit(PLAYER, TARGET, 2200));
		assertEquals(3, tracker.registerHit(PLAYER, TARGET, 2300));
	}
}
