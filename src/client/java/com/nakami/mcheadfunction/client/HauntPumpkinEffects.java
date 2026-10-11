package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.HauntIndex;
import com.nakami.mcheadfunction.haunt.HauntedTreeBlockEntity;
import com.nakami.mcheadfunction.rule.HauntRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarvedPumpkinBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** In-place restless pumpkin faces while a tree is haunting. */
public final class HauntPumpkinEffects {
	private static final RenderLayer GLOW = RenderLayer.getEntityTranslucentEmissive(
		Identifier.ofVanilla("textures/block/white_concrete.png"));
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final Map<BlockPos, Boolean> sawHaunting = new HashMap<>();
	private static final Map<BlockPos, Long> chorusAt = new HashMap<>();
	private static final List<Face> faces = new ArrayList<>();
	// Immutable snapshot: chunk rebuild workers also read this set.
	private static volatile Set<BlockPos> animated = Set.of();
	private static World currentWorld;
	private static long lastTick = Long.MIN_VALUE;

	private HauntPumpkinEffects() { }

	public static void tick(MinecraftClient client) {
		var world = client.world;
		if (world != currentWorld) {
			sawHaunting.clear();
			chorusAt.clear();
			faces.clear();
			animated = Set.of();
			lastTick = Long.MIN_VALUE;
			currentWorld = world;
		}
		if (world == null) return;
		long time = world.getTime();
		if (time == lastTick) return;
		lastTick = time;
		boolean changed = noteTrees(world, time);
		if (changed || time % 5 == 0) scan(world, client.gameRenderer.getCamera().getPos());
		sigh(world, time);
	}

	/** Suppress only the static chunk model; the actual block and collision stay in place. */
	public static boolean replacesBlock(BlockPos pos, BlockState state) {
		return animated.contains(pos) && HauntRules.restlessFace(state.isOf(Blocks.CARVED_PUMPKIN), state.isOf(Blocks.JACK_O_LANTERN));
	}

	public static void afterEntities(WorldRenderContext context) {
		var world = context.world();
		var matrices = context.matrixStack();
		var vertices = context.consumers();
		if (world == null || matrices == null || vertices == null || faces.isEmpty()) return;
		long time = world.getTime();
		float delta = context.tickCounter().getTickDelta(false);
		Vec3d camera = context.camera().getPos();
		// Flush opaque moving blocks before emissive faces. The emissive layer writes color only;
		// leaving solid models buffered until later would paint straight over the eye fire.
		for (Face face : faces) {
			var state = world.getBlockState(face.pos);
			if (!replacesBlock(face.pos, state) || !haunting(world, face.tree)) continue;
			matrices.push();
			matrices.translate(face.pos.getX() - camera.x, face.pos.getY() - camera.y, face.pos.getZ() - camera.z);
			pose(matrices, face, time + delta);
			int light = WorldRenderer.getLightmapCoordinates(world, face.pos.offset(face.facing));
			MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, vertices, light, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
		if (vertices instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
		for (Face face : faces) {
			var state = world.getBlockState(face.pos);
			if (!HauntRules.restlessFace(state.isOf(Blocks.CARVED_PUMPKIN), state.isOf(Blocks.JACK_O_LANTERN)) || !haunting(world, face.tree)) continue;
			matrices.push();
			matrices.translate(face.pos.getX() - camera.x, face.pos.getY() - camera.y, face.pos.getZ() - camera.z);
			if (animated.contains(face.pos)) pose(matrices, face, time + delta);
			float fire = HauntRules.eyeFire(true, seen(face.tree, time), time, face.cameraDistance, face.lantern);
			renderLocalFace(matrices, vertices, face.facing, fire, time + delta, face.pos.asLong());
			matrices.pop();
		}
	}

	private static boolean haunting(World world, BlockPos treePos) {
		return world.getBlockEntity(treePos) instanceof HauntedTreeBlockEntity tree && tree.haunting();
	}

	private static void pose(MatrixStack matrices, Face face, double time) {
		// Long dead stillness, slow lift/tilt, frozen stare, then a six-tick snap home.
		double phase = (time + Math.floorMod(face.pos.asLong() * 31, 120)) % 120;
		double amount = phase < 28 ? 0 : phase < 50 ? (phase - 28) / 22 : phase < 78 ? 1 : phase < 84 ? (84 - phase) / 6 : 0;
		double sign = (face.pos.getX() + face.pos.getZ()) % 3 == 0 ? -1 : 1;
		matrices.translate(0.5, 0.5 + amount * 0.24, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (sign * amount * 13)));
		matrices.multiply((face.facing.getAxis() == Direction.Axis.Z ? RotationAxis.POSITIVE_Z : RotationAxis.POSITIVE_X)
			.rotationDegrees((float) (sign * amount * 16)));
		matrices.translate(-0.5, -0.5, -0.5);
	}

	private static void scan(World world, Vec3d camera) {
		faces.clear();
		Map<BlockPos, Face> unique = new HashMap<>();
		int view = HauntRules.RESTLESS_VIEW;
		BlockPos cam = BlockPos.ofFloored(camera);
		for (BlockPos treePos : HauntIndex.trees(world)) {
			if (!(world.getBlockEntity(treePos) instanceof HauntedTreeBlockEntity tree) || !tree.haunting()) continue;
			int minX = Math.max(cam.getX() - view, treePos.getX() - HauntRules.RANGE);
			int maxX = Math.min(cam.getX() + view, treePos.getX() + HauntRules.RANGE);
			int minZ = Math.max(cam.getZ() - view, treePos.getZ() - HauntRules.RANGE);
			int maxZ = Math.min(cam.getZ() + view, treePos.getZ() + HauntRules.RANGE);
			int minY = Math.max(cam.getY() - 8, treePos.getY() - HauntRules.RANGE_Y);
			int maxY = Math.min(cam.getY() + 8, treePos.getY() + HauntRules.RANGE_Y);
			if (minX > maxX || minY > maxY || minZ > maxZ) continue;
			for (BlockPos pos : BlockPos.iterate(minX, minY, minZ, maxX, maxY, maxZ)) {
				var state = world.getBlockState(pos);
				boolean lantern = state.isOf(Blocks.JACK_O_LANTERN);
				if (!HauntRules.restlessFace(state.isOf(Blocks.CARVED_PUMPKIN), lantern)) continue;
				int dx = pos.getX() - treePos.getX();
				int dy = pos.getY() - treePos.getY();
				int dz = pos.getZ() - treePos.getZ();
				if (!HauntRules.inHauntRange(dx, dy, dz)) continue;
				Direction facing = state.contains(CarvedPumpkinBlock.FACING) ? state.get(CarvedPumpkinBlock.FACING) : Direction.SOUTH;
				unique.putIfAbsent(pos.toImmutable(), new Face(pos.toImmutable(), facing, lantern, treePos.toImmutable(),
					Vec3d.ofCenter(pos).distanceTo(camera)));
			}
		}
		faces.addAll(unique.values());
		faces.sort(Comparator.comparingDouble((Face face) -> face.cameraDistance).thenComparingLong(face -> face.pos.asLong()));
		Set<BlockPos> next = new HashSet<>();
		for (int i = 0; i < Math.min(faces.size(), HauntRules.RESTLESS_FULL); i++) next.add(faces.get(i).pos);
		Set<BlockPos> changed = new HashSet<>(animated);
		changed.addAll(next);
		Set<BlockPos> same = new HashSet<>(animated);
		same.retainAll(next);
		changed.removeAll(same);
		animated = Set.copyOf(next);
		// Rebuild only on membership edges, never once per animation frame.
		for (BlockPos pos : changed) MinecraftClient.getInstance().worldRenderer.scheduleBlockRenders(
			pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
	}

	private static boolean noteTrees(World world, long time) {
		boolean changed = false;
		for (BlockPos pos : List.copyOf(sawHaunting.keySet())) {
			if (!(world.getBlockEntity(pos) instanceof HauntedTreeBlockEntity)) {
				changed |= Boolean.TRUE.equals(sawHaunting.remove(pos));
				chorusAt.remove(pos);
			}
		}
		for (BlockPos pos : HauntIndex.trees(world)) {
			if (!(world.getBlockEntity(pos) instanceof HauntedTreeBlockEntity tree)) continue;
			boolean haunting = tree.haunting();
			Boolean was = sawHaunting.put(pos.toImmutable(), haunting);
			changed |= was == null || was != haunting;
			if (haunting && !Boolean.TRUE.equals(was)) {
				// Only a watched off→on edge plays the chorus; reload or walking in does not.
				chorusAt.put(pos.toImmutable(), was == null ? time - HauntRules.CHORUS_TICKS : time);
			}
			if (!haunting) chorusAt.remove(pos);
		}
		return changed;
	}

	private static void sigh(World world, long time) {
		int n = Math.min(faces.size(), HauntRules.RESTLESS_FULL);
		for (int i = 0; i < n; i++) {
			Face face = faces.get(i);
			if (!HauntRules.sigh(true, seen(face.tree, time), time, i, true)) continue;
			Vec3d smoke = Vec3d.of(face.pos).add(0.5, 0.08, 0.5);
			world.addParticle(i % 2 == 0 ? ParticleTypes.ASH : ParticleTypes.SOUL,
				smoke.x, smoke.y, smoke.z, 0, 0.02, 0);
		}
	}

	private static int seen(BlockPos tree, long time) {
		Long start = chorusAt.get(tree);
		return start == null ? HauntRules.CHORUS_TICKS : (int) (time - start);
	}

	private static void renderLocalFace(MatrixStack matrices, VertexConsumerProvider vertices, Direction facing,
			float fire, double time, long seed) {
		if (fire <= 0) return;
		var buffer = vertices.getBuffer(GLOW);
		// The sockets have depth: black recess, a cold flame, then a pin-bright slit.
		// All layers are face-aligned quads. No camera-facing discs or triangle/quad mismatch.
		for (int eye = 0; eye < 2; eye++) {
			double u = eye == 0 ? 5.1 : 11.2;
			double clock = time + Math.floorMod(seed, 71) + eye * 19;
			float flicker = (float) (0.84 + 0.10 * Math.sin(clock * .73) + .06 * Math.sin(clock * 1.91));
			float glow = Math.min(1, fire * 1.5F) * flicker;
			// Follow the original stepped carving instead of stamping two black rectangles.
			for (int row = 2; row <= 7; row++) {
				double left = eye == 0 ? (row == 2 || row == 3 || row == 7 ? 4 : 3) : (row == 2 || row == 7 ? 10 : 9);
				double right = eye == 0 ? (row == 2 || row == 7 ? 6 : 7) : (row == 2 ? 11 : row == 3 ? 12 : row >= 6 ? 14 : 13);
				patch(matrices, buffer, facing, left, row, right, row + 1, .006, .008F, .012F, .014F, .96F);
			}
			double tip = 1.0 + .65 * Math.sin(clock * .41);
			flame(matrices, buffer, facing, u, tip, 1.5, clock, .014, .07F, .55F, .43F, .40F * glow);
			flame(matrices, buffer, facing, u, tip + .5, 1.0, clock, .022, .12F, .95F, .73F, .88F * glow);
			flame(matrices, buffer, facing, u, 3.9, .44, clock, .029, .77F, 1F, .87F, glow);
			// Tiny dark slit inside the bright core gives a stare rather than a lamp filament.
			patch(matrices, buffer, facing, u - .10, 5.0, u + .10, 6.4, .035, .01F, .12F, .09F, .82F * glow);
		}

		// Only a dim glimmer behind the teeth: eyes carry the expression.
		float mouth = fire * (float) (.14 + .07 * Math.sin(time * .09 + Math.floorMod(seed, 17)));
		patch(matrices, buffer, facing, 4, 10.3, 12, 11.7, .009, .08F, .42F, .34F, mouth);
	}

	private static void flame(MatrixStack matrices, VertexConsumer buffer, Direction facing,
			double u, double tip, double width, double time, double out, float r, float g, float b, float alpha) {
		// A tapered ribbon with a swaying tip; each band is a proper quad with fading edges.
		for (int band = 0; band < 6; band++) {
			double lower = band / 6.0, upper = (band + 1) / 6.0;
			double y0 = 7.2 - (7.2 - tip) * lower, y1 = 7.2 - (7.2 - tip) * upper;
			double w0 = width * Math.sin((.12 + .88 * lower) * Math.PI);
			double w1 = width * Math.sin((.12 + .88 * upper) * Math.PI);
			double x0 = u + Math.sin(time * .27 + lower * 4) * lower * .65;
			double x1 = u + Math.sin(time * .27 + upper * 4) * upper * .65;
			float a0 = alpha * (float) (1 - lower * .65), a1 = alpha * (float) (1 - upper * .65);
			vertex(buffer, matrices, facePoint(facing, x1 - w1, y1, out), r, g, b, a1);
			vertex(buffer, matrices, facePoint(facing, x1 + w1, y1, out), r, g, b, a1);
			vertex(buffer, matrices, facePoint(facing, x0 + w0, y0, out), r, g, b, a0);
			vertex(buffer, matrices, facePoint(facing, x0 - w0, y0, out), r, g, b, a0);
		}
	}

	private static void patch(MatrixStack matrices, VertexConsumer buffer, Direction facing,
							double u0, double v0, double u1, double v1, double out,
							float r, float g, float b, float alpha) {
		if (alpha <= 0.01F) return;
		vertex(buffer, matrices, facePoint(facing, u0, v0, out), r, g, b, alpha);
		vertex(buffer, matrices, facePoint(facing, u1, v0, out), r, g, b, alpha);
		vertex(buffer, matrices, facePoint(facing, u1, v1, out), r, g, b, alpha);
		vertex(buffer, matrices, facePoint(facing, u0, v1, out), r, g, b, alpha);
	}

	static Vec3d facePoint(Direction facing, double u, double v, double out) {
		double x = u / 16.0;
		double y = 1 - v / 16.0;
		return switch (facing) {
			case SOUTH -> new Vec3d(x, y, 1 + out);
			case NORTH -> new Vec3d(1 - x, y, -out);
			case EAST -> new Vec3d(1 + out, y, 1 - x);
			case WEST -> new Vec3d(-out, y, x);
			default -> new Vec3d(0.5, y, 0.5);
		};
	}

	private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d p, float r, float g, float b, float alpha) {
		buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
			.color(r, g, b, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(0, 1, 0);
	}

	private record Face(BlockPos pos, Direction facing, boolean lantern, BlockPos tree, double cameraDistance) { }
}
