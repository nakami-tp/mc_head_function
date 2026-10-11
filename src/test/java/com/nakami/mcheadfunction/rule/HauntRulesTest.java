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

	@Test void possessionFlightIsSlowEnoughToWatch() {
		assertEquals(50, HauntRules.FLY_TICKS);
		assertEquals(45, HauntRules.SPLIT_TICKS);
		assertEquals(35, HauntRules.ENTER_TICKS);
		assertEquals(30, HauntRules.WAVE_GAP);
		assertEquals(130, HauntRules.possessionReadyAt(), "A lead ghost takes 6.5 seconds to finish entering");
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

	@Test void restlessPumpkinsOnlyWakeWhileHaunting() {
		assertFalse(HauntRules.restless(false));
		assertTrue(HauntRules.restless(true), "No zombie head required");
		assertTrue(HauntRules.restlessFace(true, false));
		assertTrue(HauntRules.restlessFace(false, true));
		assertFalse(HauntRules.restlessFace(false, false), "An uncarved pumpkin stays asleep");
	}

	@Test void restlessStaysInsideTheHauntRange() {
		assertTrue(HauntRules.inHauntRange(0, 0, 0));
		assertTrue(HauntRules.inHauntRange(48, 16, 48));
		assertFalse(HauntRules.inHauntRange(49, 0, 0));
		assertFalse(HauntRules.inHauntRange(0, 17, 0));
		assertFalse(HauntRules.inHauntRange(0, 0, -49));
	}

	@Test void openingChorusKeepsEveryEyeOpenAndBrighter() {
		assertEquals(60, HauntRules.CHORUS_TICKS);
		assertEquals(1.0F, HauntRules.chorus(0), 0.001F);
		assertEquals(0.5F, HauntRules.chorus(30), 0.001F);
		assertEquals(0.0F, HauntRules.chorus(60), 0.001F);
		assertEquals(0.0F, HauntRules.chorus(-1), 0.001F, "A reloaded haunt does not replay the chorus");
		assertFalse(HauntRules.blinkShut(0, HauntRules.chorus(0)));
		assertFalse(HauntRules.blinkShut(1, HauntRules.chorus(10)), "Chorus does not blink");
		assertEquals(1.0F, HauntRules.eyeFire(true, 0, 1, 0, false), 0.001F);
		assertTrue(HauntRules.eyeFire(true, 0, 1, 0, false) > HauntRules.eyeFire(true, 60, 20, 0, false));
	}

	@Test void leftoverPumpkinsBlinkOnOneClock() {
		assertEquals(80, HauntRules.BLINK_PERIOD);
		assertEquals(8, HauntRules.BLINK_SHUT);
		assertTrue(HauntRules.blinkShut(0, 0));
		assertTrue(HauntRules.blinkShut(7, 0));
		assertFalse(HauntRules.blinkShut(8, 0));
		assertTrue(HauntRules.blinkShut(80, 0), "The next cycle closes every face together");
		assertEquals(0.0F, HauntRules.eyeFire(true, 80, 0, 0, false), 0.001F);
		assertEquals(0.55F, HauntRules.eyeFire(true, 80, 20, 0, false), 0.001F);
	}

	@Test void eyeFireFallsOffAndJackOLanternsStayDimmer() {
		assertEquals(0.0F, HauntRules.eyeFire(false, 0, 20, 0, false), 0.001F);
		assertEquals(0.55F, HauntRules.eyeFire(true, 80, 20, 16, false), 0.001F);
		assertEquals(0.0F, HauntRules.eyeFire(true, 80, 20, 40, false), 0.001F);
		assertEquals(0.275F, HauntRules.eyeFire(true, 80, 20, 28, false), 0.001F);
		assertTrue(HauntRules.eyeFire(true, 80, 20, 0, true) < HauntRules.eyeFire(true, 80, 20, 0, false));
		assertEquals(0.3575F, HauntRules.eyeFire(true, 80, 20, 0, true), 0.001F);
	}

	@Test void onlyTheNearestFacesSigh() {
		assertEquals(16, HauntRules.RESTLESS_FULL);
		assertEquals(40, HauntRules.SIGH_PERIOD);
		assertTrue(HauntRules.sigh(true, 80, 0, 0, true));
		assertFalse(HauntRules.sigh(true, 80, 0, 0, false), "Faces past the budget keep only eye fire");
		assertFalse(HauntRules.sigh(false, 80, 0, 0, true));
		assertTrue(HauntRules.sigh(true, 80, 7, 1, true), "Staggered mouths, same period");
	}

	@Test void possessedFurnacesOverflowLikeAFireplaceAndDoNotHarm() {
		assertFalse(HauntRules.furnaceHarms());
		assertFalse(HauntRules.furnaceTurns());
		assertEquals(0.0F, HauntRules.furnaceOverflow(false, 0), 0.001F);
		assertEquals(0.0, HauntRules.furnaceFireHeight(0), 0.001);
		assertEquals(0.0, HauntRules.furnaceFireSpill(0), 0.001);
		assertTrue(HauntRules.furnaceOverflow(true, 0) > 0);
		double high = HauntRules.furnaceFireHeight(1.25F);
		double spill = HauntRules.furnaceFireSpill(1.25F);
		assertEquals(2.6625, high, 0.001);
		assertEquals(1.05, spill, 0.001);
		assertTrue(high <= HauntRules.FURNACE_OVERFLOW_MAX_HEIGHT + 0.02);
		assertTrue(spill <= HauntRules.FURNACE_OVERFLOW_MAX_SPILL + 0.001);
		assertTrue(high < 6, "Overflow stays on the hearth, not a 6-block spray");
		assertTrue(spill < 1.5, "Fire spills from the mouth, it does not stream at a target");
		var seen = new java.util.HashSet<Integer>();
		for (int t = 0; t < 80; t++) seen.add(Math.round(HauntRules.furnaceOverflow(true, t) * 100));
		assertTrue(seen.size() > 8, "The fire should look hard to control");
	}

	@Test void aGoatCartWandersInsteadOfLockingACreature() {
		double[] none = new double[0];
		double aligned = HauntRules.cartHeading(0, 0, 0, 0, 10, 0, false, none, none);
		assertEquals(0.0, aligned, 0.001, "Already aimed at the run-to point, keep that heading");
		double wandered = HauntRules.cartHeading(0, 0.1, 0, 0, 10, 0, false, none, none);
		assertTrue(wandered > 0 && wandered < 0.1, "Wander weaves around the line instead of replacing it");

		double left = HauntRules.cartHeading(0, 0, 0, 0, 0, 12, false, none, none);
		assertTrue(left > 0.2, "A point to the side must curve the heading, not charge a line");

		double edge = HauntRules.cartHeading(0, 0, 40, 0, 45, 0, false, none, none);
		assertTrue(Math.cos(edge) < 0.85, "Near the haunt edge the cart turns back toward the tree");

		double behind = HauntRules.cartHeading(0, 0, 0, 0, 10, 0, false, new double[]{-6}, new double[]{0});
		assertEquals(0.0, behind, 0.02, "A creature behind must not make it hunt by turning around");

		double empty = HauntRules.cartHeading(0, 0, 0, 0, 10, 0, false, none, none);
		double aheadLeft = HauntRules.cartHeading(0, 0, 0, 0, 10, 0, false, new double[]{6}, new double[]{6});
		assertTrue(aheadLeft > empty, "A rideable creature already ahead only tugs the heading");
		assertTrue(Math.abs(HauntRules.wrapAngle(aheadLeft - Math.atan2(6, 6))) > 0.4,
			"The tug must stay a tendency, not a lock onto that creature");

		double occupied = HauntRules.cartHeading(0, 0, 0, 0, 10, 0, true, new double[]{6}, new double[]{6});
		assertEquals(empty, occupied, 0.001, "A loaded cart just keeps running around");

		double split = HauntRules.cartHeading(0, 0, 0, 0, 10, 0, false, new double[]{8, 8}, new double[]{6, -6});
		assertEquals(empty, split, 0.03, "Creatures on both sides must not pick a target");
	}

	@Test void cartWaypointsStayInRangeAndOnlyFavorBusyGround() {
		assertTrue(HauntRules.cartWaypointScore(5, 0, new double[]{5}, new double[]{0})
			> HauntRules.cartWaypointScore(20, 0, new double[]{5}, new double[]{0}));
		assertEquals(1.0, HauntRules.cartWaypointScore(3, 4, new double[0], new double[0]), 0.001);
		assertTrue(HauntRules.cartArrived(0, 0, 1, 0));
		assertFalse(HauntRules.cartArrived(0, 0, 8, 0));
		var seen = new java.util.HashSet<String>();
		for (long seed = 0; seed < 40; seed++) {
			var at = HauntRules.cartWaypoint(new java.util.Random(seed), 0, 0, new double[0], new double[0]);
			assertTrue(HauntRules.inHauntRange(at.x(), 0, at.z()), "A run-to point must stay in haunt range");
			seen.add(Math.round(at.x()) + "," + Math.round(at.z()));
		}
		assertTrue(seen.size() > 15, "Run-to points must spread, not stack");
	}

	@Test void aGoatCartBoardsOnContactAndOtherwiseRams() {
		assertTrue(HauntRules.cartBoards(false, true));
		assertFalse(HauntRules.cartBoards(true, true), "One rider at a time");
		assertFalse(HauntRules.cartBoards(false, false), "If it cannot sit in a minecart, ram it");
		assertTrue(HauntRules.cartRams(false));
		assertFalse(HauntRules.cartRams(true));
		assertEquals(4.0F, HauntRules.CART_HIT, 0.001F);
		double bounced = HauntRules.cartBounce(0, 0.5);
		assertTrue(Math.abs(HauntRules.wrapAngle(bounced)) > 0.8, "A hard block must break the line, not nudge it");
	}

	@Test void villagersStayOutOnlyInsideAnActiveHaunt() {
		assertTrue(HauntRules.villagersStayOut(true, 0, 0, 0));
		assertTrue(HauntRules.villagersStayOut(true, 48, 16, 48));
		assertFalse(HauntRules.villagersStayOut(false, 0, 0, 0), "A quiet night is ordinary bedtime");
		assertFalse(HauntRules.villagersStayOut(true, 49, 0, 0), "Outside the haunt they still go home");
		assertFalse(HauntRules.villagersStayOut(true, 0, 17, 0));
	}

	@Test void tenRiseWithFiveOfEachRoleAndKind() {
		assertEquals(10, HauntRules.RISING);
		assertEquals(5, HauntRules.HUNTERS);
		assertEquals(5, HauntRules.DANCERS);
		int hunters = 0, dancers = 0, villagers = 0;
		for (int i = 0; i < HauntRules.RISING; i++) {
			if (HauntRules.risingHunter(i)) hunters++;
			if (HauntRules.risingDancer(i)) dancers++;
			if (HauntRules.risingZombieVillager(i)) villagers++;
		}
		assertEquals(5, hunters);
		assertEquals(5, dancers);
		assertEquals(5, villagers);
		assertFalse(HauntRules.risingHunter(-1));
		assertFalse(HauntRules.risingDancer(10));
		assertEquals(0, HauntRules.riseStagger(0));
		assertEquals(16, HauntRules.riseStagger(4));
		assertEquals(HauntRules.RISE_DEPTH, HauntRules.riseBuried(HauntRules.CLIMB_TICKS), 0.001F);
		assertEquals(HauntRules.RISE_DEPTH / 2, HauntRules.riseBuried(HauntRules.CLIMB_TICKS / 2.0F), 0.001F);
		assertEquals(0, HauntRules.riseBuried(0), 0.001F);
		assertEquals(HauntRules.RISE_DEPTH, HauntRules.riseBuried(44), 0.001F, "Staggered extras stay buried until they start");
	}

	@Test void lingeringVillagersStayOnTheLastHalfHeart() {
		assertEquals(4, HauntRules.lingerAmount(false, 5, 4), 0.001F);
		assertEquals(4, HauntRules.lingerAmount(true, 20, 4), 0.001F);
		assertEquals(4, HauntRules.lingerAmount(true, 5, 4), 0.001F);
		assertEquals(2, HauntRules.lingerAmount(true, 3, 10), 0.001F);
		assertEquals(0, HauntRules.lingerAmount(true, 1, 4), 0.001F);
	}

	@Test void onlyALingeringZombieLastHitConverts() {
		assertTrue(HauntRules.convertVillager(true, true, 1));
		assertFalse(HauntRules.convertVillager(true, true, 3), "A nibble that leaves more than one heart is just damage");
		assertFalse(HauntRules.convertVillager(true, false, 1), "Props leave them lingering");
		assertFalse(HauntRules.convertVillager(false, true, 1), "A quiet night is ordinary death");
	}

	@Test void thrillerKeepsFourBeatsOnOneClock() {
		assertEquals(80, HauntRules.DANCE_PERIOD);
		assertEquals(HauntRules.DancePose.LEAN, HauntRules.thrillerPose(0));
		assertEquals(HauntRules.DancePose.SNAP_LEFT, HauntRules.thrillerPose(20));
		assertEquals(HauntRules.DancePose.SNAP_RIGHT, HauntRules.thrillerPose(40));
		assertEquals(HauntRules.DancePose.LEAN, HauntRules.thrillerPose(80), "The line shares one clock");
		assertTrue(HauntRules.thrillerPose(0).bodyPitch() < -0.2F, "Beat 0 leans back");
		assertTrue(HauntRules.thrillerPose(20).headYaw() < -0.4F, "Beat 1 snaps left");
		assertTrue(HauntRules.thrillerPose(40).headYaw() > 0.4F, "Beat 2 snaps right");
		assertTrue(HauntRules.thrillerPose(67).leftArmRoll() > HauntRules.DancePose.LEAN.leftArmRoll(), "Beat 3 opens the arms");
		assertTrue(HauntRules.thrillerPose(79).leftArmRoll() < HauntRules.thrillerPose(67).leftArmRoll(), "Then the arms slam down");
		assertEquals(0, HauntRules.danceSway(0), 0.001);
		assertTrue(HauntRules.danceSway(70) > 0);
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
