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
	private HauntRules() { }

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
	public static <T> List<T> pick(List<T> candidates, int count, Random random) {
		List<T> shuffled = new ArrayList<>(candidates);
		Collections.shuffle(shuffled, random);
		return List.copyOf(shuffled.subList(0, Math.clamp(count, 0, shuffled.size())));
	}
}
