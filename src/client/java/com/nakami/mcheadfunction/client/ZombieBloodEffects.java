package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.net.ZombieBiteS2CPayload;
import com.nakami.mcheadfunction.wear.ZombieBlood;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Nonverbal feedback: heart pulse, claw impact, returning blood and a short fist wake. */
public final class ZombieBloodEffects {
    private static final RenderLayer GLOW = RenderLayer.getEntityTranslucentEmissive(Identifier.ofVanilla("textures/block/white_concrete.png"));
    private static final List<Bite> bites = new ArrayList<>();
    private static Object currentWorld;
    private static int healFlash, hungerFlash, previousFood = -1;
    private static long lastHudFrame;
    private ZombieBloodEffects() { }

    public static void receive(MinecraftClient client, ZombieBiteS2CPayload payload) {
        if (client.world == null) return;
        if (currentWorld != client.world) reset(client);
        var attacker = client.world.getEntityById(payload.attacker());
        if (attacker == null) return;
        Vec3d origin = new Vec3d(payload.x(), payload.y(), payload.z());
        Vec3d toward = attacker.getEyePos().subtract(origin).normalize();
        if (bites.size() >= 32) bites.removeFirst();
        bites.add(new Bite(payload, origin.add(toward.multiply(0.6))));
    }
    private static void reset(MinecraftClient client) {
        bites.clear(); healFlash = hungerFlash = 0; previousFood = -1;
        currentWorld = client.world;
    }
    public static void tick(MinecraftClient client) {
        if (currentWorld != client.world) reset(client);
        if (client.player == null || client.world == null) return;
        if (healFlash > 0) healFlash--;
        if (hungerFlash > 0) hungerFlash--;
        for (var bite : bites) {
            bite.age++;
            if (bite.age == 8 && bite.payload.healed() > 0 && bite.payload.attacker() == client.player.getId()) {
                if (ZombieBlood.active(client.player)) {
                    healFlash = bite.payload.killed() ? 18 : 12;
                    if (client.currentScreen == null && client.isWindowFocused())
                        client.player.playSound(SoundEvents.ENTITY_GENERIC_EAT, bite.payload.killed() ? 0.4F : 0.24F, 0.7F);
                }
            }
        }
        bites.removeIf(b -> b.age > 14 || client.world.getEntityById(b.payload.attacker()) == null);
        if (!ZombieBlood.active(client.player)) { previousFood = -1; return; }
        int food = client.player.getHungerManager().getFoodLevel();
        if (previousFood > 0 && food == 0) hungerFlash = 24;
        previousFood = food;
        float power = ZombieBlood.strength(client.player);
        if (ZombieBlood.unarmed(client.player) && client.player.handSwinging && power > 0.15 && client.player.age % 4 == 0) {
            var hand = client.player.getEyePos().add(client.player.getRotationVec(1).multiply(0.55)).add(0, -0.45, 0);
            client.world.addParticle(new DustParticleEffect(new Vector3f(0.55F, 0.035F, 0.06F), 0.6F),
                hand.x, hand.y, hand.z, 0, 0.01, 0);
        }
    }
    public static void renderHud(DrawContext draw, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || !ZombieBlood.active(client.player) || client.options.hudHidden || client.currentScreen != null) return;
        float dt = counter.getTickDelta(false);
        float power = ZombieBlood.strength(client.player);
        float clock = client.player.age + dt;
        double pulse = Math.pow(0.5 + Math.sin(clock * (0.13 + power * 0.2)) * 0.5, 4);
        int alpha = (int) (power * (18 + 65 * pulse) + healFlash * 9);
        alpha = Math.clamp(alpha, 0, 180);
        int left = draw.getScaledWindowWidth() / 2 - 91;
        int bottom = draw.getScaledWindowHeight() - 39;
        int rows = Math.max(1, (int) Math.ceil((client.player.getMaxHealth() + client.player.getAbsorptionAmount()) / 20));
        int spacing = Math.max(10 - (rows - 2), 3);
        int hearts = Math.min(100, (int) Math.ceil(client.player.getHealth() / 2));
        for (int i = 0; i < hearts; i++) {
            int x = left + (i % 10) * 8, y = bottom - (i / 10) * spacing;
            // A thin pulse follows the existing heart silhouettes, without a new meter or label.
            int color = (alpha << 24) | (healFlash > 0 ? 0xFF6655 : 0xCD1835);
            draw.fill(x + 1, y, x + 4, y + 1, color);
            draw.fill(x + 5, y, x + 8, y + 1, color);
            draw.fill(x, y + 1, x + 1, y + 4, color);
            draw.fill(x + 8, y + 1, x + 9, y + 4, color);
            for (int j = 0; j < 4; j++) {
                draw.fill(x + j, y + 4 + j, x + j + 1, y + 5 + j, color);
                draw.fill(x + 8 - j, y + 4 + j, x + 9 - j, y + 5 + j, color);
            }
        }
        if (hungerFlash > 0) {
            int color = ((int) (hungerFlash * 4 * (0.5 + 0.5 * Math.sin(clock))) << 24) | 0xAE1830;
            for (int i = 0; i < 10; i++) {
                int x = draw.getScaledWindowWidth() / 2 + 10 + i * 8;
                draw.fill(x, bottom + 7, x + 5, bottom + 8, color);
            }
        }
        lastHudFrame = client.player.age;
    }
    public static void afterEntities(WorldRenderContext context) {
        if (bites.isEmpty() || context.matrixStack() == null || context.consumers() == null) return;
        var client = MinecraftClient.getInstance();
        Vec3d camera = context.camera().getPos();
        Vector3f r = new Vector3f(1, 0, 0).rotate(context.camera().getRotation());
        Vector3f u = new Vector3f(0, 1, 0).rotate(context.camera().getRotation());
        Vec3d right = new Vec3d(r.x, r.y, r.z), up = new Vec3d(u.x, u.y, u.z);
        var matrices = context.matrixStack();
        var buffer = context.consumers().getBuffer(GLOW);
        float dt = context.tickCounter().getTickDelta(false);
        for (var bite : bites) {
            float age = bite.age + dt;
            float power = bite.payload.strength();
            float force = bite.payload.killed() ? 1.35F : 0.8F + power * 0.4F;
            if (age < 2) {
                // One compact contact flash; never a full-screen flash.
                square(buffer, matrices, bite.origin.subtract(camera), right, up,
                    0.10 * force, (1 - age / 2) * 0.95F, 2);
            }
            if (age < 5) {
                float fade = (1 - age / 5) * 0.95F;
                double pixel = 0.06 * force;
                for (int claw = -1; claw <= 1; claw++) for (int step = 0; step < 6; step++) {
                    Vec3d center = bite.origin.add(right.multiply(claw * 0.17 + (step / 2 - 1) * pixel))
                        .add(up.multiply((2.5 - step) * pixel * 1.5)).subtract(camera);
                    square(buffer, matrices, center, right, up, pixel * 0.65, fade, 0);
                }
            }
            if (age < 7) {
                float fade = 1 - age / 7;
                int fragments = bite.payload.killed() ? 14 : 9;
                for (int i = 0; i < fragments; i++) {
                    double angle = i * 2.39996;
                    double radius = (0.10 + age * 0.085) * force;
                    Vec3d center = bite.origin.add(right.multiply(Math.cos(angle) * radius))
                        .add(up.multiply(Math.sin(angle) * radius * 0.65 - age * age * 0.004)).subtract(camera);
                    square(buffer, matrices, center, right, up, (0.025 + (i % 3) * 0.009) * force * fade,
                        fade * 0.9F, i % 3 == 0 ? 1 : 0);
                }
            }
            var attacker = context.world().getEntityById(bite.payload.attacker());
            if (bite.payload.healed() > 0 && age >= 2 && age < 8 && attacker != null) {
                double t = (age - 2) / 6;
                t *= t;
                Vec3d end = attacker.getLerpedPos(dt).add(0, attacker.getHeight() * 0.7, 0);
                int count = Math.min(10, (bite.payload.killed() ? 6 : 3) + (int) bite.payload.healed());
                for (int i = 0; i < count; i++) {
                    double offset = i * 2.4;
                    Vec3d p = bite.origin.lerp(end, t).add(right.multiply(Math.sin(offset + t * 5) * 0.17 * (1 - t)))
                        .add(up.multiply(Math.sin(t * Math.PI) * (0.2 + 0.05 * i)));
                    // Shrink near the first-person camera so returning beads never fill the view.
                    double size = Math.min(0.05, p.distanceTo(camera) * 0.018);
                    Vec3d center = p.subtract(camera);
                    square(buffer, matrices, center, right, up, size, 0.9F, 1);
                }
            }
        }
    }
    private static void square(VertexConsumer buffer, MatrixStack matrices, Vec3d center,
                               Vec3d right, Vec3d up, double size, float alpha, int tint) {
        vertex(buffer, matrices, center.add(right.multiply(-size)).add(up.multiply(size)), alpha, tint);
        vertex(buffer, matrices, center.add(right.multiply(size)).add(up.multiply(size)), alpha, tint);
        vertex(buffer, matrices, center.add(right.multiply(size)).add(up.multiply(-size)), alpha, tint);
        vertex(buffer, matrices, center.add(right.multiply(-size)).add(up.multiply(-size)), alpha, tint);
    }
    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d p, float alpha, int tint) {
        buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
            .color(tint == 1 ? 0.7F : 1F, tint == 1 ? 0.1F : tint == 2 ? 0.95F : 0.74F,
                tint == 1 ? 0.08F : tint == 2 ? 0.8F : 0.46F, alpha)
            .texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0);
    }
    // Read-only validation observations, also useful to diagnose missing network feedback.
    public static boolean hasKillingBite() { return bites.stream().anyMatch(b -> b.payload.killed()); }
    public static int healingFlash() { return healFlash; }
    public static int activeBites() { return bites.size(); }
    public static boolean hasHealingBite() { return bites.stream().anyMatch(b -> b.payload.healed() > 0); }
    public static long lastHudFrame() { return lastHudFrame; }
    private static final class Bite {
        final ZombieBiteS2CPayload payload;
        final Vec3d origin;
        int age;
        Bite(ZombieBiteS2CPayload payload, Vec3d origin) { this.payload = payload; this.origin = origin; }
    }
}
