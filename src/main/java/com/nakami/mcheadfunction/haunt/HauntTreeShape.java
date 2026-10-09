package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.rule.HauntRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Collision trunk, hang points and the bones the client draws. */
public final class HauntTreeShape {
	public static final int TRUNK = 6;
	public static final double RANGE = 48;
	public static final double RANGE_Y = 16;
	private HauntTreeShape() { }

	public static Vec3d core(BlockPos pos) {
		return Vec3d.ofCenter(pos);
	}

	public static Vec3d hangOffset(int slot) {
		double angle = slot * Math.PI * 2 / HauntRules.SLOTS + 0.35;
		double height = 3.4 + (slot % 5) * 1.2;
		double radius = 1.55 + (slot % 3) * 0.7;
		return new Vec3d(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
	}

	public static Vec3d hangWorld(BlockPos core, int slot) {
		return core(core).add(hangOffset(slot));
	}

	/** Hang point closest to the look ray, or -1. */
	public static int nearestHung(HeadTypeSlots slots, Vec3d origin, Vec3d look, BlockPos core) {
		int best = -1;
		double bestMiss = 0.55;
		for (int i = 0; i < HauntRules.SLOTS; i++) {
			if (!slots.occupied(i)) continue;
			Vec3d to = hangWorld(core, i).subtract(origin);
			double along = to.dotProduct(look);
			if (along < 0.2) continue;
			double miss = to.subtract(look.multiply(along)).length();
			if (miss < bestMiss) {
				bestMiss = miss;
				best = i;
			}
		}
		return best;
	}

	public record Bone(Vec3d from, Vec3d to, float radius) { }

	public static Bone[] bones() {
		Bone[] out = new Bone[14];
		out[0] = new Bone(new Vec3d(0, 0, 0), new Vec3d(0.08, 7.2, -0.04), 0.38F);
		out[1] = new Bone(new Vec3d(0.08, 7.2, -0.04), new Vec3d(0.2, 10.6, 0.1), 0.22F);
		int i = 2;
		for (int b = 0; b < 6; b++) {
			double a = b * Math.PI / 3 + 0.2;
			double h = 4.2 + b * 0.7;
			Vec3d start = new Vec3d(Math.cos(a) * 0.15, h, Math.sin(a) * 0.15);
			Vec3d end = new Vec3d(Math.cos(a) * (2.6 + b % 2), h + 1.4 + (b % 3) * 0.35, Math.sin(a) * (2.6 + b % 2));
			out[i++] = new Bone(start, end, 0.14F);
			out[i++] = new Bone(end, end.add(Math.cos(a + 0.7) * 1.1, 0.6, Math.sin(a + 0.7) * 1.1), 0.08F);
		}
		return out;
	}

	public interface HeadTypeSlots {
		boolean occupied(int slot);
	}
}
