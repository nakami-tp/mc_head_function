package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.haunt.GhostHeadEntity;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Possessed furnace: a fireplace fire that has grown too large to stay in the hearth. */
public final class HauntFurnaceEffects {
	private static final RenderLayer GLOW = RenderLayer.getEntityTranslucentEmissive(
		Identifier.ofVanilla("textures/block/white_concrete.png"));
	private static final int FULL_BRIGHT = 0xF000F0;
	private static volatile Set<BlockPos> hearths = Set.of();
	private static World currentWorld;
	private static long lastTick = Long.MIN_VALUE;

	private HauntFurnaceEffects() { }

	public static boolean overflowing(BlockPos pos) {
		return hearths.contains(pos);
	}

	public static void tick(MinecraftClient client) {
		var world = client.world;
		if (world != currentWorld) {
			hearths = Set.of();
			lastTick = Long.MIN_VALUE;
			currentWorld = world;
		}
		if (world == null) return;
		long time = world.getTime();
		if (time == lastTick) return;
		lastTick = time;
		List<Hearth> found = scan(world, client.gameRenderer.getCamera().getPos());
		Set<BlockPos> next = new HashSet<>();
		for (Hearth hearth : found) next.add(hearth.pos);
		hearths = Set.copyOf(next);
		for (Hearth hearth : found) sparks(world, hearth, time);
	}

	public static void afterEntities(WorldRenderContext context) {
		var world = context.world();
		var matrices = context.matrixStack();
		var vertices = context.consumers();
		if (world == null || matrices == null || vertices == null || hearths.isEmpty()) return;
		long time = world.getTime();
		float delta = context.tickCounter().getTickDelta(false);
		Vec3d camera = context.camera().getPos();
		var buffer = vertices.getBuffer(GLOW);
		for (BlockPos pos : hearths) {
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof AbstractFurnaceBlock)) continue;
			Direction facing = state.contains(AbstractFurnaceBlock.FACING) ? state.get(AbstractFurnaceBlock.FACING) : Direction.NORTH;
			float overflow = HauntRules.furnaceOverflow(true, time);
			matrices.push();
			matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
			drawHearth(matrices, buffer, facing, overflow, time + delta, pos.asLong());
			matrices.pop();
		}
	}

	private static List<Hearth> scan(World world, Vec3d camera) {
		List<Hearth> found = new ArrayList<>();
		Box view = new Box(camera, camera).expand(HauntRules.RESTLESS_VIEW);
		for (GhostHeadEntity ghost : world.getEntitiesByClass(GhostHeadEntity.class, view,
			g -> g.head() == HeadType.BLAZE && g.phase() != GhostHeadEntity.WANDER && g.phase() != GhostHeadEntity.FLY
				&& g.phase() != GhostHeadEntity.WAIT && g.phase() != GhostHeadEntity.SPLIT)) {
			BlockPos furnace = hearthUnder(world, ghost);
			if (furnace == null) continue;
			found.add(new Hearth(furnace, world.getBlockState(furnace)));
		}
		return found;
	}

	private static BlockPos hearthUnder(World world, GhostHeadEntity ghost) {
		BlockPos around = ghost.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(around.add(-1, -2, -1), around.add(1, 0, 1))) {
			if (world.getBlockState(pos).getBlock() instanceof AbstractFurnaceBlock) return pos.toImmutable();
		}
		return null;
	}

	private static void sparks(World world, Hearth hearth, long time) {
		Direction facing = hearth.state.contains(AbstractFurnaceBlock.FACING)
			? hearth.state.get(AbstractFurnaceBlock.FACING) : Direction.NORTH;
		Vec3d mouth = mouth(hearth.pos, facing);
		Vec3d out = Vec3d.of(facing.getVector());
		double flicker = HauntRules.furnaceOverflow(true, time);
		if (time % 2 == 0) {
			world.addParticle(ParticleTypes.FLAME,
				mouth.x + (world.random.nextDouble() - 0.5) * 0.35,
				mouth.y + world.random.nextDouble() * 0.2,
				mouth.z + (world.random.nextDouble() - 0.5) * 0.35,
				out.x * 0.03 * flicker, 0.05 + world.random.nextDouble() * 0.04, out.z * 0.03 * flicker);
		}
		if (time % 5 == 0) {
			world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
				hearth.pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 0.25,
				hearth.pos.getY() + 1.05,
				hearth.pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 0.25,
				0, 0.02, 0);
		}
		if (Math.floorMod(time + hearth.pos.asLong(), 9) == 0) {
			world.addParticle(ParticleTypes.LAVA, mouth.x, mouth.y + 0.1, mouth.z, 0, 0, 0);
		}
	}

	private static void drawHearth(MatrixStack matrices, VertexConsumer buffer, Direction facing,
			float overflow, double time, long seed) {
		double height = HauntRules.furnaceFireHeight(overflow);
		double spill = HauntRules.furnaceFireSpill(overflow);
		Vec3d mouth = localMouth(facing);
		Vec3d out = Vec3d.of(facing.getVector());
		Vec3d across = Vec3d.of(facing.rotateYClockwise().getVector());
		// Several tongues share the mouth, then climb and lean out like a fireplace that will not stay put.
		for (int i = 0; i < 7; i++) {
			double along = (i - 3) * 0.09;
			double clock = time * 0.19 + i * 1.73 + (seed & 31);
			double surge = 0.62 + 0.38 * (0.5 + 0.5 * Math.sin(clock));
			if (Math.sin(time * 0.07 + i * 2.1) > 0.88) surge *= 1.28;
			double h = height * surge * (0.72 + 0.08 * Math.abs(i - 3));
			double lean = spill * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(clock * 0.55)));
			Vec3d base = mouth.add(across.multiply(along));
			tongue(matrices, buffer, base, out, across, h, lean, 0.22 + 0.04 * (3 - Math.abs(i - 3)), clock, 0.92F);
		}
		// A shorter plume out the top sells "too big for the box".
		for (int i = 0; i < 3; i++) {
			double clock = time * 0.15 + i * 2.4 + 11;
			double h = height * 0.45 * (0.7 + 0.3 * Math.sin(clock));
			Vec3d base = new Vec3d(0.5 + (i - 1) * 0.12, 1.02, 0.5);
			tongue(matrices, buffer, base, out.multiply(0.25), across, h, spill * 0.2, 0.16, clock, 0.7F);
		}
	}

	private static void tongue(MatrixStack matrices, VertexConsumer buffer, Vec3d base, Vec3d out, Vec3d across,
			double height, double lean, double width, double time, float alpha) {
		for (int band = 0; band < 8; band++) {
			double lower = band / 8.0, upper = (band + 1) / 8.0;
			double sway0 = Math.sin(time + lower * 3.2) * lower * 0.16;
			double sway1 = Math.sin(time + upper * 3.2) * upper * 0.16;
			Vec3d p0 = base.add(0, height * lower, 0).add(out.multiply(lean * lower)).add(across.multiply(sway0));
			Vec3d p1 = base.add(0, height * upper, 0).add(out.multiply(lean * upper)).add(across.multiply(sway1));
			double w0 = width * Math.sin((0.18 + 0.82 * (1 - lower)) * Math.PI);
			double w1 = width * Math.sin((0.18 + 0.82 * (1 - upper)) * Math.PI);
			float a0 = alpha * (float) (1 - lower * 0.72);
			float a1 = alpha * (float) (1 - upper * 0.72);
			float[] c0 = tint(lower), c1 = tint(upper);
			quad(buffer, matrices, p1.add(across.multiply(-w1)), p1.add(across.multiply(w1)),
				p0.add(across.multiply(w0)), p0.add(across.multiply(-w0)), c1, a1, c0, a0);
			// A second, thinner core keeps the middle bright without turning into a pillar.
			if (band < 5) {
				quad(buffer, matrices, p1.add(across.multiply(-w1 * 0.35)), p1.add(across.multiply(w1 * 0.35)),
					p0.add(across.multiply(w0 * 0.35)), p0.add(across.multiply(-w0 * 0.35)),
					new float[] {1F, 0.93F, 0.62F}, a1, new float[] {1F, 0.78F, 0.28F}, a0);
			}
		}
	}

	private static float[] tint(double along) {
		if (along < 0.35) return new float[] {1F, 0.38F, 0.05F};
		if (along < 0.7) return new float[] {1F, 0.62F, 0.12F};
		return new float[] {1F, 0.88F, 0.42F};
	}

	private static void quad(VertexConsumer buffer, MatrixStack matrices,
			Vec3d a, Vec3d b, Vec3d c, Vec3d d, float[] top, float ta, float[] bot, float ba) {
		vertex(buffer, matrices, a, top[0], top[1], top[2], ta);
		vertex(buffer, matrices, b, top[0], top[1], top[2], ta);
		vertex(buffer, matrices, c, bot[0], bot[1], bot[2], ba);
		vertex(buffer, matrices, d, bot[0], bot[1], bot[2], ba);
	}

	private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d p, float r, float g, float b, float a) {
		buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
			.color(r, g, b, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(0, 1, 0);
	}

	private static Vec3d mouth(BlockPos pos, Direction facing) {
		return Vec3d.of(pos).add(localMouth(facing));
	}

	private static Vec3d localMouth(Direction facing) {
		return switch (facing) {
			case SOUTH -> new Vec3d(0.5, 0.36, 1.02);
			case NORTH -> new Vec3d(0.5, 0.36, -0.02);
			case EAST -> new Vec3d(1.02, 0.36, 0.5);
			case WEST -> new Vec3d(-0.02, 0.36, 0.5);
			default -> new Vec3d(0.5, 0.36, 0.5);
		};
	}

	private record Hearth(BlockPos pos, BlockState state) { }
}
