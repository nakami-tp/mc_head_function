package com.nakami.mcheadfunction.rule;

import com.nakami.mcheadfunction.head.HeadType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Haunted-tree timing, hanging and possession rules, free of the Minecraft runtime. */
public final class HauntRules {
	public static final long DAY = 24000;
	public static final long MIDNIGHT = 18000;
	public static final long DAWN = 23000;
	/** One hang point per head kind, so a full tree holds every kind. */
	public static final int SLOTS = HeadType.values().length;
	/** Kinds needed before midnight can start a haunting on its own. */
	public static final int READY = 8;
	/** Heads that glow at any hour, not only at night. */
	public static final int GLOW = 10;
	/** Last-haunted night of a tree that has never haunted. */
	public static final long NEVER = Long.MIN_VALUE;
	/** Rise from the hang point to the gather above the hosts. */
	public static final int FLY_TICKS = 50;
	/** After the split, dive from the gather to a hover above one host. */
	public static final int SPLIT_TICKS = 45;
	/** Sink from that hover into the host before it comes alive. */
	public static final int ENTER_TICKS = 35;
	/** Rise back out and sit above the host after entering. */
	public static final int RISE_TICKS = 12;
	/** Gap between each hung kind taking off. */
	public static final int WAVE_GAP = 30;
	public static final int RETURN_TICKS = 40;
	public static final int WANDER_TICKS = 80;
	private HauntRules() { }

	/** Tick when a lead ghost finishes sinking in, if it left on the first wave. */
	public static int possessionReadyAt() {
		return FLY_TICKS + SPLIT_TICKS + ENTER_TICKS;
	}

	public static boolean isHauntTime(long timeOfDay) {
		long time = Math.floorMod(timeOfDay, DAY);
		return time >= MIDNIGHT && time < DAWN;
	}
	public static long night(long timeOfDay) {
		return Math.floorDiv(timeOfDay, DAY);
	}
	public static boolean shouldStart(int hung, boolean haunting, boolean disabled, long timeOfDay, long lastNight) {
		return !disabled && !haunting && hung >= READY && isHauntTime(timeOfDay) && night(timeOfDay) != lastNight;
	}
	/** Hanging the last kind starts at once, even during the day. */
	public static boolean shouldStartWhenFilled(int hung, boolean haunting, boolean disabled) {
		return !disabled && !haunting && hung >= SLOTS;
	}
	public static long hauntUntil(long timeOfDay) {
		long time = Math.floorMod(timeOfDay, DAY);
		return time < DAWN ? timeOfDay - time + DAWN : timeOfDay - time + DAY + DAWN;
	}
	public static boolean shouldEnd(boolean haunting, long timeOfDay, long until) {
		return haunting && timeOfDay >= until;
	}

	public static int tier(int hung) {
		if (hung >= GLOW) return 3;
		if (hung >= 6) return 2;
		if (hung >= 4) return 1;
		return 0;
	}
	public static boolean headsGlow(int hung, boolean haunting) {
		return haunting || hung >= GLOW;
	}
	public static boolean isFull(Set<HeadType> hung) {
		return hung.containsAll(EnumSet.allOf(HeadType.class));
	}

	public enum HangResult { OK, ALREADY_HUNG, HAUNTING }
	public enum TakeResult { OK, EMPTY, HAUNTING }

	public static HangResult canHang(Set<HeadType> hung, HeadType type, boolean haunting) {
		if (haunting) return HangResult.HAUNTING;
		return hung.contains(type) ? HangResult.ALREADY_HUNG : HangResult.OK;
	}
	public static TakeResult canTake(int hungCount, boolean haunting) {
		if (haunting) return TakeResult.HAUNTING;
		return hungCount > 0 ? TakeResult.OK : TakeResult.EMPTY;
	}
	/** First empty hang point, or -1 when every point holds a head. */
	public static int nextSlot(HeadType[] slots) {
		for (int i = 0; i < slots.length; i++) if (slots[i] == null) return i;
		return -1;
	}

	/** Objects a head kind possesses while haunting, with how many of them by default. */
	public enum HostKind {
		CHEST(HeadType.FROG, 4),
		PUMPKIN(HeadType.ZOMBIE, 7),
		DISPENSER(HeadType.LLAMA, 4),
		LIGHTNING_ROD(HeadType.CHARGED_CREEPER, 3),
		FURNACE(HeadType.BLAZE, 3),
		MINECART(HeadType.GOAT, 3),
		ARMOR_STAND(HeadType.RABBIT, 4),
		CARPET(HeadType.CHICKEN, 5),
		LANTERN(HeadType.BAT, 7);

		public final HeadType head;
		public final int defaultCount;

		HostKind(HeadType head, int defaultCount) {
			this.head = head;
			this.defaultCount = defaultCount;
		}
	}

	public static final int MAX_COUNT = 64;

	/** Horizontal distance from the tree while an unpaired ghost wanders. */
	public static final double WANDER_MIN_RADIUS = 6;
	public static final double WANDER_MAX_RADIUS = 28;
	/** Height above the tree core while an unpaired ghost wanders. */
	public static final double WANDER_MIN_Y = 2;
	public static final double WANDER_MAX_Y = 10;
	/** Opening chorus: every restless face lights together, no blink. */
	public static final int CHORUS_TICKS = 60;
	/** Shared blink: shut this many ticks every BLINK_PERIOD ticks. */
	public static final int BLINK_PERIOD = 80;
	public static final int BLINK_SHUT = 8;
	/** Near faces that also sigh. */
	public static final int RESTLESS_FULL = 16;
	public static final int RESTLESS_VIEW = 28;
	public static final int SIGH_PERIOD = 40;
	public static final int RANGE = 48;
	public static final int RANGE_Y = 16;
	private static final float EYE_IDLE = 0.55F;
	private static final float EYE_CHORUS = 0.45F;
	private static final float LANTERN_EYE = 0.65F;
	private static final double EYE_FULL_DISTANCE = 16;
	private static final double EYE_FADE_DISTANCE = 40;

	public record Offset(double x, double y, double z) {}

	/** The kind of object this head possesses, or null when it wanders instead. */
	public static HostKind host(HeadType type) {
		for (HostKind kind : HostKind.values()) if (kind.head == type) return kind;
		return null;
	}
	/** Unpaired hung heads split a ghost that drifts through walls. */
	public static boolean wanders(HeadType type) {
		return host(type) == null;
	}
	/** A point inside the haunt range, away from the trunk, for a wandering ghost. */
	public static Offset wanderOffset(Random random) {
		double angle = random.nextDouble() * Math.PI * 2;
		double radius = WANDER_MIN_RADIUS + random.nextDouble() * (WANDER_MAX_RADIUS - WANDER_MIN_RADIUS);
		double y = WANDER_MIN_Y + random.nextDouble() * (WANDER_MAX_Y - WANDER_MIN_Y);
		return new Offset(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
	}
	public static int clampCount(int count) {
		return Math.clamp(count, 0, MAX_COUNT);
	}

	/** A frog chest eats blocking blocks on the way to prey, not hosts, trees, inventories or bedrock. */
	public static boolean canFrogChestEat(boolean unbreakable, boolean hostOrTree, boolean hasBlockEntity) {
		return !unbreakable && !hostOrTree && !hasBlockEntity;
	}
	/** Restless faces wake for the haunting itself, not only the zombie pairing. */
	public static boolean restless(boolean haunting) {
		return haunting;
	}
	public static boolean restlessFace(boolean carved, boolean lantern) {
		return carved || lantern;
	}
	public static boolean inHauntRange(double dx, double dy, double dz) {
		return Math.abs(dx) <= RANGE && Math.abs(dz) <= RANGE && Math.abs(dy) <= RANGE_Y;
	}
	/** 1 at the first seen haunt tick, 0 after the chorus or when the client did not see the start. */
	public static float chorus(int seenTicks) {
		if (seenTicks < 0 || seenTicks >= CHORUS_TICKS) return 0;
		return 1 - seenTicks / (float) CHORUS_TICKS;
	}
	public static boolean blinkShut(long worldTime, float chorus) {
		return chorus <= 0 && Math.floorMod(worldTime, BLINK_PERIOD) < BLINK_SHUT;
	}
	public static float eyeFalloff(double distance) {
		if (distance <= EYE_FULL_DISTANCE) return 1;
		if (distance >= EYE_FADE_DISTANCE) return 0;
		return (float) ((EYE_FADE_DISTANCE - distance) / (EYE_FADE_DISTANCE - EYE_FULL_DISTANCE));
	}
	public static float eyeFire(boolean haunting, int seenTicks, long worldTime, double distance, boolean lantern) {
		if (!haunting) return 0;
		float chorus = chorus(seenTicks);
		if (blinkShut(worldTime, chorus)) return 0;
		float strength = (EYE_IDLE + chorus * EYE_CHORUS) * eyeFalloff(distance);
		return lantern ? strength * LANTERN_EYE : strength;
	}
	public static boolean sigh(boolean haunting, int seenTicks, long worldTime, int index, boolean full) {
		if (!haunting || !full) return false;
		return Math.floorMod(worldTime - index * 7L, SIGH_PERIOD) == 0;
	}

	/** Possessed furnaces overflow like a fireplace fire. Visual only: no spray, no harm, no turning. */
	public static final double FURNACE_OVERFLOW_MAX_HEIGHT = 2.65;
	public static final double FURNACE_OVERFLOW_MAX_SPILL = 1.05;
	public static boolean furnaceHarms() {
		return false;
	}
	public static boolean furnaceTurns() {
		return false;
	}
	/** 0 when not possessed. Otherwise a restless 0.55–1.25 pulse, never a directed pillar. */
	public static float furnaceOverflow(boolean possessed, long worldTime) {
		if (!possessed) return 0;
		double wave = 0.82 + 0.28 * Math.sin(worldTime * 0.13) + 0.15 * Math.sin(worldTime * 0.41);
		return (float) Math.clamp(wave, 0.55, 1.25);
	}
	public static double furnaceFireHeight(float overflow) {
		return overflow <= 0 ? 0 : 1.1 + overflow * 1.25;
	}
	public static double furnaceFireSpill(float overflow) {
		return overflow <= 0 ? 0 : 0.25 + overflow * 0.64;
	}

	/**
	 * Goat cart: wander in the haunt range, never lock a creature as a target.
	 * Heading is wander noise + a run-to point + a home fence. Rideable creatures
	 * only add a weak forward-only field, and slightly weight the next point.
	 * Pickup happens on contact, not by seeking.
	 */
	public static final double CART_MAX_SPEED = 0.62;
	public static final double CART_ACCEL = 0.035;
	public static final double CART_WANDER_TURN = 0.14;
	public static final double CART_WAYPOINT_PULL = 0.28;
	public static final double CART_WAYPOINT_RIDER = 1.6;
	public static final int CART_WAYPOINT_TICKS = 28;
	public static final double CART_WAYPOINT_MIN = 8;
	public static final double CART_WAYPOINT_MAX = 18;
	public static final int CART_WAYPOINT_SAMPLES = 8;
	public static final double CART_RIDER_BIAS = 0.10;
	public static final double CART_RIDER_RANGE = 12;
	public static final double CART_HOME_START = 0.68;
	public static final double CART_HOME_STRENGTH = 0.65;
	public static final double CART_ARRIVE = 1.6;
	public static final float CART_HIT = 4;

	public static double wrapAngle(double angle) {
		angle = (angle + Math.PI) % (Math.PI * 2);
		if (angle < 0) angle += Math.PI * 2;
		return angle - Math.PI;
	}

	public static double lerpAngle(double from, double to, double t) {
		return from + wrapAngle(to - from) * Math.clamp(t, 0, 1);
	}

	public static boolean cartArrived(double cartX, double cartZ, double wayX, double wayZ) {
		return Math.hypot(wayX - cartX, wayZ - cartZ) <= CART_ARRIVE;
	}

	public static double cartWaypointScore(double x, double z, double[] riderX, double[] riderZ) {
		double score = 1;
		for (int i = 0; i < riderX.length; i++) {
			score += CART_WAYPOINT_RIDER / (1 + Math.hypot(x - riderX[i], z - riderZ[i]));
		}
		return score;
	}

	/** A run-to point near the cart, inside the haunt range. Occupied carts pass empty rider lists. */
	public static Offset cartWaypoint(Random random, double cartX, double cartZ, double[] riderX, double[] riderZ) {
		double[] xs = new double[CART_WAYPOINT_SAMPLES];
		double[] zs = new double[CART_WAYPOINT_SAMPLES];
		double[] scores = new double[CART_WAYPOINT_SAMPLES];
		double total = 0;
		for (int i = 0; i < CART_WAYPOINT_SAMPLES; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = CART_WAYPOINT_MIN + random.nextDouble() * (CART_WAYPOINT_MAX - CART_WAYPOINT_MIN);
			double x = cartX + Math.cos(angle) * radius;
			double z = cartZ + Math.sin(angle) * radius;
			if (!inHauntRange(x, 0, z)) {
				double farthest = Math.max(Math.abs(x), Math.abs(z));
				double scale = RANGE * 0.9 / Math.max(farthest, 0.01);
				x *= scale;
				z *= scale;
			}
			xs[i] = x;
			zs[i] = z;
			scores[i] = cartWaypointScore(x, z, riderX, riderZ);
			total += scores[i];
		}
		double pick = random.nextDouble() * total;
		double seen = 0;
		for (int i = 0; i < CART_WAYPOINT_SAMPLES; i++) {
			seen += scores[i];
			if (pick <= seen) return new Offset(xs[i], 0, zs[i]);
		}
		return new Offset(xs[CART_WAYPOINT_SAMPLES - 1], 0, zs[CART_WAYPOINT_SAMPLES - 1]);
	}

	public static double cartHeading(
		double heading,
		double wanderDelta,
		double cartX,
		double cartZ,
		double wayX,
		double wayZ,
		boolean occupied,
		double[] riderX,
		double[] riderZ
	) {
		double h = heading + wanderDelta;
		if (Math.hypot(wayX - cartX, wayZ - cartZ) > 0.01) {
			h = lerpAngle(h, Math.atan2(wayZ - cartZ, wayX - cartX), CART_WAYPOINT_PULL);
		}
		double range = Math.hypot(cartX, cartZ);
		double homePull = Math.max(0, (range / RANGE - CART_HOME_START) / (1 - CART_HOME_START));
		if (homePull > 0) {
			h = lerpAngle(h, Math.atan2(-cartZ, -cartX), homePull * CART_HOME_STRENGTH);
		}
		if (!occupied) {
			double biasX = 0;
			double biasZ = 0;
			double weight = 0;
			for (int i = 0; i < riderX.length; i++) {
				double dx = riderX[i] - cartX;
				double dz = riderZ[i] - cartZ;
				double dist = Math.hypot(dx, dz);
				if (dist < 0.01 || dist > CART_RIDER_RANGE) continue;
				double to = Math.atan2(dz, dx);
				double ahead = Math.cos(wrapAngle(to - h));
				double w = (1 - dist / CART_RIDER_RANGE) * Math.max(0, ahead);
				biasX += Math.cos(to) * w;
				biasZ += Math.sin(to) * w;
				weight += w;
			}
			if (weight > 0) h = lerpAngle(h, Math.atan2(biasZ, biasX), Math.min(1, weight) * CART_RIDER_BIAS);
		}
		return wrapAngle(h);
	}

	public static double cartBounce(double heading, double random) {
		return wrapAngle(heading + Math.PI * 0.55 + (random - 0.5) * 1.4);
	}

	public static boolean cartBoards(boolean occupied, boolean canRide) {
		return !occupied && canRide;
	}

	public static boolean cartRams(boolean boarded) {
		return !boarded;
	}

	public static <T> List<T> pick(List<T> candidates, int count, Random random) {
		List<T> shuffled = new ArrayList<>(candidates);
		Collections.shuffle(shuffled, random);
		return List.copyOf(shuffled.subList(0, Math.clamp(count, 0, shuffled.size())));
	}
}
