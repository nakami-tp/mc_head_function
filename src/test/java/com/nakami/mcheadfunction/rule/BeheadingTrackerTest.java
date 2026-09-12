package com.nakami.mcheadfunction.rule;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeheadingTrackerTest {
	private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void thirdHitInsideWindowBeheads() {
		BeheadingTracker tracker = new BeheadingTracker();
		assertFalse(tracker.registerHit(PLAYER, TARGET, 0));
		assertFalse(tracker.registerHit(PLAYER, TARGET, 400));
		assertTrue(tracker.registerHit(PLAYER, TARGET, 800));
	}

	@Test
	void windowResetAfterTwoSeconds() {
		BeheadingTracker tracker = new BeheadingTracker();
		assertFalse(tracker.registerHit(PLAYER, TARGET, 0));
		assertFalse(tracker.registerHit(PLAYER, TARGET, 400));
		assertFalse(tracker.registerHit(PLAYER, TARGET, 2100));
		assertFalse(tracker.registerHit(PLAYER, TARGET, 2200));
		assertTrue(tracker.registerHit(PLAYER, TARGET, 2300));
	}
}
