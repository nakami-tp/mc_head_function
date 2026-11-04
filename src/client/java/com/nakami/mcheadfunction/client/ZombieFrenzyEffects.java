package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.wear.ZombieBlood;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;

/** Cosmetic only: skin arms, breathing and alternating strikes never change attack timing or aim. */
public final class ZombieFrenzyEffects {
    private static final Map<PlayerEntity, Motion> motions = new IdentityHashMap<>();
    private static Object world;
    private static boolean wearing;
    private static int breathTicks, heartbeatTicks, previousFood = -1;
    private static int entries, hungerCues, breaths, swings, renderedArms;
    private static long lastArmFrame = -1;
    private ZombieFrenzyEffects() { }

    public static boolean posing(PlayerEntity player) {
        return ZombieBlood.unarmed(player) && !player.isUsingItem() && !player.isSwimming()
            && !player.isFallFlying() && !player.isSleeping();
    }

    public static void tick(MinecraftClient client) {
        if (world != client.world) {
            world = client.world; motions.clear(); wearing = false;
            breathTicks = heartbeatTicks = 0; previousFood = -1;
            entries = hungerCues = breaths = swings = renderedArms = 0;
            lastArmFrame = -1;
        }
        if (client.world == null || client.player == null || client.isPaused()) return;
        motions.keySet().removeIf(p -> p.isRemoved() || !ZombieBlood.unarmed(p));
        for (var player : client.world.getPlayers()) {
            if (!ZombieBlood.unarmed(player)) continue;
            var motion = motions.computeIfAbsent(player, p -> new Motion());
            float swing = player.getHandSwingProgress(1);
            if (swing > 0 && (motion.previousSwing == 0 || swing < motion.previousSwing)) {
                motion.punch++;
                if (player == client.player && posing(player)) {
                    swings++;
                    sound(client, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.16F,
                        0.75F + ZombieBlood.strength(player) * 0.35F);
                }
            }
            motion.previousSwing = swing;
        }
        boolean active = ZombieBlood.active(client.player);
        if (!active) {
            wearing = false; previousFood = -1;
            breathTicks = heartbeatTicks = 0;
            return;
        }
        if (!wearing) {
            wearing = true; breathTicks = 60; entries++;
            sound(client, SoundEvents.ENTITY_PLAYER_BREATH, 0.65F, 0.65F);
            sound(client, SoundEvents.ENTITY_ZOMBIE_AMBIENT, 0.23F, 0.75F);
        }
        int food = client.player.getHungerManager().getFoodLevel();
        if (previousFood > 0 && food == 0) {
            hungerCues++;
            sound(client, SoundEvents.ENTITY_PLAYER_BREATH, 0.7F, 0.75F);
        }
        previousFood = food;
        float power = ZombieBlood.strength(client.player);
        if (--breathTicks <= 0) {
            breaths++;
            sound(client, SoundEvents.ENTITY_PLAYER_BREATH, 0.18F + 0.35F * power,
                0.65F + 0.3F * power);
            // 6 s at full health, 2 s near death; hunger hastens the next breath slightly.
            breathTicks = Math.max(35, (int) (120 - 80 * power) - (food == 0 ? 10 : 0));
        }
        if (power > 0.55F && --heartbeatTicks <= 0) {
            sound(client, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.08F + 0.15F * power, 0.9F + power * 0.2F);
            heartbeatTicks = (int) (35 - 16 * power);
        } else if (power <= 0.55F) heartbeatTicks = 0;
    }

    private static void sound(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
        if (client.currentScreen == null && client.isWindowFocused()) client.player.playSound(sound, volume, pitch);
    }

    public static Arm strikingArm(PlayerEntity player) {
        var motion = motions.get(player);
        return player.getOffHandStack().isEmpty() && motion != null && motion.punch % 2 == 0
            ? player.getMainArm().getOpposite() : player.getMainArm();
    }

    @FunctionalInterface public interface ArmRenderer {
        void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, float equip, float swing, Arm arm);
    }

    public static void firstPerson(PlayerEntity player, float swing, float equip, MatrixStack matrices,
                                   VertexConsumerProvider vertices, int light, ArmRenderer renderer) {
        if (player.isInvisible()) return;
        // Idle is exactly vanilla: one relaxed main arm. Only choose a different arm during a swing.
        Arm arm = swing > 0 ? strikingArm(player) : player.getMainArm();
        matrices.push();
        renderer.render(matrices, vertices, light, equip, swing, arm);
        matrices.pop();
        renderedArms = arm == Arm.RIGHT ? 1 : 2;
        lastArmFrame = player.age;
    }

    public static int entryCues() { return entries; }
    public static int hungerCues() { return hungerCues; }
    public static int breathCues() { return breaths; }
    public static int swingCues() { return swings; }
    public static int renderedArms() { return renderedArms; }
    public static long lastArmFrame() { return lastArmFrame; }
    private static final class Motion { float previousSwing; int punch; }
}
