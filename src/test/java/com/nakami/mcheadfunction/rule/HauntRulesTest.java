package com.nakami.mcheadfunction.rule;

import com.nakami.mcheadfunction.head.HeadType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HauntRulesTest {
	@Test void hauntingRunsFromMidnightUntilDawn() {
		assertFalse(HauntRules.isHauntTime(17999));
		assertTrue(HauntRules.isHauntTime(18000));
		assertTrue(HauntRules.isHauntTime(22999));
		assertFalse(HauntRules.isHauntTime(23000));
		assertFalse(HauntRules.isHauntTime(6000));
		assertTrue(HauntRules.isHauntTime(24000 * 5 + 18000));
		assertFalse(HauntRules.isHauntTime(24000 * 5 + 23000));
	}

	@Test void eachNightHasItsOwnNumber() {
		assertEquals(HauntRules.night(18000), HauntRules.night(22999));
		assertEquals(HauntRules.night(18000) + 1, HauntRules.night(24000 + 18000));
		assertEquals(HauntRules.night(18000) + 3, HauntRules.night(24000 * 3 + 20000));
	}

	@Test void eightKindsStartOncePerNight() {
		long midnight = 18000, laterThatNight = 21000, noon = 6000, nextMidnight = 24000 + 18000;
		assertEquals(8, HauntRules.READY);
		assertTrue(HauntRules.shouldStart(8, false, false, midnight, HauntRules.NEVER));
		assertTrue(HauntRules.shouldStart(8, false, false, laterThatNight, HauntRules.NEVER), "Crossing the threshold mid-night starts at once");
		assertTrue(HauntRules.shouldStart(19, false, false, midnight, HauntRules.NEVER));
		assertFalse(HauntRules.shouldStart(7, false, false, midnight, HauntRules.NEVER), "Seven kinds stay quiet");
		assertFalse(HauntRules.shouldStart(8, true, false, midnight, HauntRules.NEVER), "Already haunting");
		assertFalse(HauntRules.shouldStart(8, false, false, noon, HauntRules.NEVER), "Eight kinds wait for midnight");
		assertFalse(HauntRules.shouldStart(19, false, false, noon, HauntRules.NEVER), "A full tree does not restart every daytime tick");
		assertFalse(HauntRules.shouldStart(8, false, false, laterThatNight, HauntRules.night(midnight)), "Only once per night");
		assertTrue(HauntRules.shouldStart(8, false, false, nextMidnight, HauntRules.night(midnight)), "Again the next night");
		assertFalse(HauntRules.shouldStart(19, false, true, midnight, HauntRules.NEVER), "A closed haunting stays closed");
		assertFalse(HauntRules.shouldStart(8, false, true, midnight, HauntRules.NEVER));
	}

	@Test void hangingTheNineteenthStartsAtOnce() {
		assertTrue(HauntRules.shouldStartWhenFilled(19, false, false));
		assertFalse(HauntRules.shouldStartWhenFilled(18, false, false), "Eighteen kinds still wait for midnight");
		assertFalse(HauntRules.shouldStartWhenFilled(19, true, false), "Already haunting");
		assertFalse(HauntRules.shouldStartWhenFilled(19, false, true), "A closed haunting stays closed");
	}

	@Test void treeGrowsEerierInFourTiers() {
		assertEquals(0, HauntRules.tier(0));
		assertEquals(0, HauntRules.tier(3));
		assertEquals(1, HauntRules.tier(4));
		assertEquals(1, HauntRules.tier(5));
		assertEquals(2, HauntRules.tier(6));
		assertEquals(2, HauntRules.tier(9));
		assertEquals(3, HauntRules.tier(10));
		assertEquals(3, HauntRules.tier(19));
		assertFalse(HauntRules.headsGlow(9, false));
		assertTrue(HauntRules.headsGlow(10, false), "Ten heads glow at any hour");
		assertTrue(HauntRules.headsGlow(3, true), "A haunting tree glows even with few heads");
	}

	@Test void fullMeansAllNineteenKinds() {
		assertEquals(19, HauntRules.SLOTS);
		var all = java.util.EnumSet.allOf(HeadType.class);
		assertTrue(HauntRules.isFull(all));
		all.remove(HeadType.BAT);
		assertFalse(HauntRules.isFull(all));
		assertFalse(HauntRules.isFull(java.util.EnumSet.noneOf(HeadType.class)));
	}

	@Test void eachKindHangsOnceAndNothingChangesWhileHaunting() {
		var hung = java.util.EnumSet.of(HeadType.ZOMBIE);
		assertEquals(HauntRules.HangResult.OK, HauntRules.canHang(hung, HeadType.FROG, false));
		assertEquals(HauntRules.HangResult.ALREADY_HUNG, HauntRules.canHang(hung, HeadType.ZOMBIE, false));
		assertEquals(HauntRules.HangResult.HAUNTING, HauntRules.canHang(hung, HeadType.FROG, true));
		assertEquals(HauntRules.TakeResult.OK, HauntRules.canTake(1, false));
		assertEquals(HauntRules.TakeResult.EMPTY, HauntRules.canTake(0, false));
		assertEquals(HauntRules.TakeResult.HAUNTING, HauntRules.canTake(1, true));
	}

	@Test void headsTakeTheFirstFreeHangPoint() {
		var slots = new HeadType[HauntRules.SLOTS];
		assertEquals(0, HauntRules.nextSlot(slots));
		slots[0] = HeadType.ZOMBIE;
		slots[1] = HeadType.FROG;
		assertEquals(2, HauntRules.nextSlot(slots));
		slots[1] = null;
		assertEquals(1, HauntRules.nextSlot(slots), "A freed hang point is reused");
		java.util.Arrays.fill(slots, HeadType.PIG);
		assertEquals(-1, HauntRules.nextSlot(slots));
	}

	@Test void nineKindsPossessTheirOwnObjects() {
		assertEquals(HauntRules.HostKind.CHEST, HauntRules.host(HeadType.FROG));
		assertEquals(HauntRules.HostKind.PUMPKIN, HauntRules.host(HeadType.ZOMBIE));
		assertEquals(HauntRules.HostKind.DISPENSER, HauntRules.host(HeadType.LLAMA));
		assertEquals(HauntRules.HostKind.LIGHTNING_ROD, HauntRules.host(HeadType.CHARGED_CREEPER));
		assertEquals(HauntRules.HostKind.FURNACE, HauntRules.host(HeadType.BLAZE));
		assertEquals(HauntRules.HostKind.MINECART, HauntRules.host(HeadType.GOAT));
		assertEquals(HauntRules.HostKind.ARMOR_STAND, HauntRules.host(HeadType.RABBIT));
		assertEquals(HauntRules.HostKind.CARPET, HauntRules.host(HeadType.CHICKEN));
		assertEquals(HauntRules.HostKind.LANTERN, HauntRules.host(HeadType.BAT));
		int possessing = 0;
		int wandering = 0;
		for (HeadType type : HeadType.values()) {
			if (HauntRules.host(type) != null) possessing++;
			if (HauntRules.wanders(type)) wandering++;
		}
		assertEquals(9, possessing);
		assertEquals(10, wandering, "The other ten kinds wander instead of possessing");
		assertNull(HauntRules.host(HeadType.PIG));
		assertTrue(HauntRules.wanders(HeadType.PIG));
		assertTrue(HauntRules.wanders(HeadType.ENDERMAN));
		assertFalse(HauntRules.wanders(HeadType.FROG));
	}

	@Test void wanderingGhostsStayInsideTheAgreedDrift() {
		var seen = new java.util.HashSet<String>();
		for (long seed = 0; seed < 80; seed++) {
			var at = HauntRules.wanderOffset(new java.util.Random(seed));
			double radius = Math.hypot(at.x(), at.z());
			assertTrue(radius >= HauntRules.WANDER_MIN_RADIUS && radius <= HauntRules.WANDER_MAX_RADIUS,
				"Wander radius must stay 6-28; got " + radius);
			assertTrue(at.y() >= HauntRules.WANDER_MIN_Y && at.y() <= HauntRules.WANDER_MAX_Y,
				"Wander height must stay 2-10; got " + at.y());
			seen.add(Math.round(at.x()) + "," + Math.round(at.z()));
		}
		assertTrue(seen.size() > 20, "Wander points must spread around the tree, not stack");
	}

	@Test void defaultCountsMatchTheAgreedFirstVersion() {
		assertEquals(4, HauntRules.HostKind.CHEST.defaultCount);
		assertEquals(7, HauntRules.HostKind.PUMPKIN.defaultCount);
		assertEquals(4, HauntRules.HostKind.DISPENSER.defaultCount);
		assertEquals(3, HauntRules.HostKind.LIGHTNING_ROD.defaultCount);
		assertEquals(3, HauntRules.HostKind.FURNACE.defaultCount);
		assertEquals(3, HauntRules.HostKind.MINECART.defaultCount);
		assertEquals(4, HauntRules.HostKind.ARMOR_STAND.defaultCount);
		assertEquals(5, HauntRules.HostKind.CARPET.defaultCount);
		assertEquals(7, HauntRules.HostKind.LANTERN.defaultCount);
		assertEquals(HeadType.FROG, HauntRules.HostKind.CHEST.head);
	}

	@Test void countsStayBetweenZeroAndSixtyFour() {
		assertEquals(0, HauntRules.clampCount(-3));
		assertEquals(12, HauntRules.clampCount(12));
		assertEquals(64, HauntRules.clampCount(500));
	}

	@Test void pickingChoosesDistinctCandidatesUpToTheCount() {
		var candidates = java.util.List.of("a", "b", "c", "d", "e", "f");
		for (long seed = 0; seed < 20; seed++) {
			var picked = HauntRules.pick(candidates, 4, new java.util.Random(seed));
			assertEquals(4, picked.size());
			assertEquals(4, new java.util.HashSet<>(picked).size(), "No object is possessed twice");
			assertTrue(candidates.containsAll(picked));
		}
		assertEquals(java.util.Set.of("a", "b"), new java.util.HashSet<>(HauntRules.pick(java.util.List.of("a", "b"), 5, new java.util.Random(1))),
			"Too few candidates: possess all of them");
		assertTrue(HauntRules.pick(candidates, 0, new java.util.Random(1)).isEmpty());
		var spread = new java.util.HashSet<String>();
		for (long seed = 0; seed < 50; seed++) spread.addAll(HauntRules.pick(candidates, 1, new java.util.Random(seed)));
		assertTrue(spread.size() > 3, "Choice is random rather than always the first candidates");
	}

	@Test void aFrogChestEatsBreakablePathBlocksButNotHostsOrBedrock() {
		assertTrue(HauntRules.canFrogChestEat(false, false, false));
		assertFalse(HauntRules.canFrogChestEat(true, false, false), "Bedrock stays");
		assertFalse(HauntRules.canFrogChestEat(false, true, false), "The host chest and haunted tree stay");
		assertFalse(HauntRules.canFrogChestEat(false, false, true), "Inventories stay");
	}

	@Test void hauntingEndsAtTheNextDawn() {
		assertEquals(23000, HauntRules.hauntUntil(18000));
		assertEquals(23000, HauntRules.hauntUntil(6000), "A full tree that starts by day lasts until that day's dawn");
		assertEquals(24000 + 23000, HauntRules.hauntUntil(23000));
		assertFalse(HauntRules.shouldEnd(true, 22999, 23000));
		assertTrue(HauntRules.shouldEnd(true, 23000, 23000), "Dawn");
		assertTrue(HauntRules.shouldEnd(true, 25000, 23000), "Night skipped by sleeping or time change");
		assertFalse(HauntRules.shouldEnd(true, 12000, 23000), "A daytime start must not stop before dawn");
		assertFalse(HauntRules.shouldEnd(false, 23000, 23000), "Nothing to end");
	}
}
