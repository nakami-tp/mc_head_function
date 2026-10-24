package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.head.HeadlessAccess;
import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.head.HeadType;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

public final class BeheadingClientValidation implements ClientModInitializer {
    private boolean opening, setup, finished, attacked;
    private volatile boolean ready;
    private volatile String serverFailure;
    private static int targetId = -1, controlId = -1;
    private static int hiddenFrames, visibleFrames, wrongFrames, shakeFrames, gloveHiddenFrames;
    public static boolean sideView;
    public static int flatGloveFrames;
    private static int rightArmFrames, leftArmFrames, thirdPersonPunchFrames;
    private static float minArmZ = Float.POSITIVE_INFINITY, maxArmZ = Float.NEGATIVE_INFINITY;
    private static float minRightX = Float.POSITIVE_INFINITY, maxRightX = Float.NEGATIVE_INFINITY;
    private final java.util.List<String> punches = new java.util.ArrayList<>();
    private final java.util.List<String> soundLog = new java.util.ArrayList<>();
    private int ticks, flightFrames;
    private double maxFlightZ;

    public static void observeArm(net.minecraft.util.Arm arm, float equip, float swing, float x, float z) {
        check(swing == 0 && equip == 0, "Glove must not use the vanilla sideways swing or cooldown dip");
        if (arm == net.minecraft.util.Arm.RIGHT) rightArmFrames++; else leftArmFrames++;
        if (arm == net.minecraft.util.Arm.RIGHT) { minRightX = Math.min(minRightX, x); maxRightX = Math.max(maxRightX, x); }
        minArmZ = Math.min(minArmZ, z); maxArmZ = Math.max(maxArmZ, z);
    }

    public static void observePlayer(float pitch, float swing) {
        if (sideView && swing > 0 && pitch < -1.2F) thirdPersonPunchFrames++;
    }

    private final long started = System.nanoTime();

    public static void observe(LivingEntity entity, boolean head, boolean hat, float roll) {
        if (entity.getId() == targetId && HeadlessAccess.isHeadless(entity)) {
            if (head || hat) wrongFrames++; else hiddenFrames++;
        }
        if (entity.getId() == controlId) {
            if (!HeadlessAccess.isHeadless(entity) && head) visibleFrames++;
            if (((com.nakami.mcheadfunction.head.HeadShakeAccess) entity).mhf$headShakeTicks() > 0 && Math.abs(roll) > 0.02) shakeFrames++;
            if (HeadlessAccess.isHeadless(entity) && !head && !hat) gloveHiddenFrames++;
        }
    }

    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            if ((System.nanoTime() - started) / 1_000_000_000 > 120) throw new IllegalStateException("Timed out");
            if (serverFailure != null) throw new IllegalStateException(serverFailure);
            if (!opening && client.currentScreen != null && client.getOverlay() == null) {
                opening = true;
                client.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER).setValue(1.0);
                client.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.PLAYERS).setValue(1.0);
                client.getSoundManager().registerListener((sound, set, range) -> {
                    String id = sound.getId().toString();
                    if (id.startsWith("mc_head_function:beheading.")) {
                        punches.add(id);
                        soundLog.add("tick=" + ticks + " " + id + " volume=" + sound.getVolume() + " pitch=" + sound.getPitch());
                    }
                });
                client.options.pauseOnLostFocus = false;
                client.options.getEnableVsync().setValue(false);
                client.options.getViewDistance().setValue(4);
                client.options.getMaxFps().setValue(60);
                client.options.hudHidden = false;
                client.createIntegratedServerLoader().createAndStart("beheading-" + System.currentTimeMillis(),
                    new LevelInfo("Beheading regression", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                        new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
            }
            if (client.player == null || client.world == null || client.getServer() == null) return;
            if (!setup) {
                setup = true;
                onServer(client, () -> {
                    var server = client.getServer();
                    var world = server.getOverworld();
                    world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                    world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    world.setTimeOfDay(6000);
                    world.setWeather(0, 100000, false, false);
                    for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
                    var target = EntityType.ENDERMAN.create(world);
                    target.refreshPositionAndAngles(-1.5, 100, 0, 0, 0);
                    target.setPersistent(); target.setAiDisabled(true); world.spawnEntity(target); targetId = target.getId();
                    var control = EntityType.ENDERMAN.create(world);
                    control.refreshPositionAndAngles(1.5, 100, 0, 0, 0);
                    control.setPersistent(); control.setAiDisabled(true); world.spawnEntity(control); controlId = control.getId();
                    camera(client);
                    ready = true;
                });
            }
            if (!ready || (!attacked && client.world.getEntityById(targetId) == null)) return;
            ticks++;
            if (ticks >= 85 && ticks <= 145) ScreenshotRecorder.saveScreenshot(client.runDirectory, String.format("combo-%03d.png", ticks - 85), client.getFramebuffer(), m -> {});
            if (ticks == 30) ScreenshotRecorder.saveScreenshot(client.runDirectory, "before.png", client.getFramebuffer(), m -> {});
            if (ticks == 35 && !attacked) {
                attacked = true;
                onServer(client, () -> {
                    var world = client.getServer().getOverworld();
                    var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    var target = (LivingEntity) world.getEntityById(targetId);
                    player.refreshPositionAndAngles(target.getX(), 101, -2, 0, 0);
                    player.setHeadYaw(0);
                    player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                    float health = target.getHealth();
                    player.attack(target); player.attack(target); player.attack(target);
                    check(HeadlessAccess.isHeadless(target), "Three head hits failed: eye=" + player.getEyePos() + " look=" + player.getRotationVec(1) + " box=" + target.getBoundingBox());
                    check(target.getHealth() == health, "Head hits damaged target");
                    check(world.getEntitiesByClass(ThrownHeadEntity.class, target.getBoundingBox().expand(8), h -> h.getHeadType() == HeadType.ENDERMAN).size() == 1, "Expected exactly one head drop");
                    camera(client);
                });
            }
            if (ticks >= 36 && ticks <= 60) {
                for (var entity : client.world.getEntities()) {
                    if (entity instanceof ThrownHeadEntity head && head.getHeadType() == HeadType.ENDERMAN && head.getX() < 0) {
                        flightFrames++;
                        maxFlightZ = Math.max(maxFlightZ, head.getZ());
                    }
                }
            }
            if (ticks == 42) ScreenshotRecorder.saveScreenshot(client.runDirectory, "head-in-flight.png", client.getFramebuffer(), m -> {});
            if (ticks == 65) ScreenshotRecorder.saveScreenshot(client.runDirectory, "after.png", client.getFramebuffer(), m -> {});
            if (ticks == 70) {
                check(HeadlessAccess.isHeadless((LivingEntity) client.world.getEntityById(targetId)), "Headless state did not reach client");
                check(wrongFrames == 0 && hiddenFrames > 5, "Decapitated enderman still renders its head: wrongFrames=" + wrongFrames + " hiddenFrames=" + hiddenFrames);
                check(visibleFrames > 5, "Intact enderman lost its head");
                check(flightFrames >= 8 && maxFlightZ >= 2.0 && maxFlightZ <= 3.1, "Head did not visibly fly 2–3 blocks: frames=" + flightFrames + " distance=" + maxFlightZ);
                onServer(client, () -> {
                    var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    player.teleport(client.getServer().getOverworld(), 1.5, 100, 2, 180, 0);
                    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(com.nakami.mcheadfunction.head.HeadItems.BEHEADING_GLOVE));
                    player.currentScreenHandler.sendContentUpdates();
                });
            }
            if (ticks == 90) {
                punches.clear();
                ScreenshotRecorder.saveScreenshot(client.runDirectory, "glove-idle.png", client.getFramebuffer(), m -> {});
            }
            if (ticks == 95 || ticks == 109 || ticks == 123) {
                var victim = (LivingEntity) client.world.getEntityById(controlId);
                check(victim != null, "Glove target disappeared");
                check(client.player.getMainHandStack().isOf(com.nakami.mcheadfunction.head.HeadItems.BEHEADING_GLOVE), "Glove not synchronized");
                check(!com.nakami.mcheadfunction.behead.BeheadingHandler.aimingAtHead(client.player, victim), "Fixture must aim at body");
                client.interactionManager.attackEntity(client.player, victim);
                client.player.swingHand(Hand.MAIN_HAND);
            }
            if (ticks == 97 || ticks == 99 || ticks == 111 || ticks == 113 || ticks == 125) ScreenshotRecorder.saveScreenshot(client.runDirectory, "punch-" + ticks + ".png", client.getFramebuffer(), m -> {});
            if (ticks == 119) {
                check(!HeadlessAccess.isHeadless((LivingEntity) client.world.getEntityById(controlId)), "Two punches beheaded target");
                check(shakeFrames > 5, "Head never visibly shook after punches");
            }
            if (ticks == 145) {
                ScreenshotRecorder.saveScreenshot(client.runDirectory, "glove-after.png", client.getFramebuffer(), m -> {});
                check(gloveHiddenFrames > 5, "Three networked body punches did not hide head");
                onServer(client, () -> {
                    var target = (LivingEntity) client.getServer().getOverworld().getEntityById(controlId);
                    check(target != null && HeadlessAccess.isHeadless(target) && target.getHealth() == target.getMaxHealth(), "Glove target damaged or missing");
                    check(target.squaredDistanceTo(1.5, 100, 0) < 0.01, "Knocked-off head teleported enderman");
                });
            }
            if (ticks == 150) {
                check(punches.equals(java.util.List.of("mc_head_function:beheading.punch_one", "mc_head_function:beheading.punch_two", "mc_head_function:beheading.punch_finish")), "Wrong punch audio sequence: " + punches);
                sideView = true;
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            }
            if (ticks == 165 || ticks == 173) ScreenshotRecorder.saveScreenshot(client.runDirectory, "third-person-" + ticks + ".png", client.getFramebuffer(), m -> {});
            if (ticks == 170) client.player.swingHand(Hand.MAIN_HAND);
            if (ticks == 180) {
                sideView = false;
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.options.getMainArm().setValue(net.minecraft.util.Arm.LEFT);
                onServer(client, () -> client.getServer().getPlayerManager().getPlayerList().getFirst().setMainArm(net.minecraft.util.Arm.LEFT));
            }
            if (ticks == 190 || ticks == 196) ScreenshotRecorder.saveScreenshot(client.runDirectory, "left-hand-" + ticks + ".png", client.getFramebuffer(), m -> {});
            if (ticks == 193) client.player.swingHand(Hand.MAIN_HAND);
            if (ticks == 205) {
                check(flatGloveFrames == 0, "Glove still rendered as a held flat item");
                check(rightArmFrames > 20 && leftArmFrames > 10 && maxArmZ - minArmZ > 0.25F, "Missing fist animation or handedness: " + rightArmFrames + "/" + leftArmFrames + " depth=" + (maxArmZ-minArmZ));
                check(maxRightX - minRightX > 0.20F && maxRightX - minRightX < 0.40F, "Jab must stay on its own side instead of sweeping across the screen: " + (maxRightX-minRightX));
                check(thirdPersonPunchFrames > 3, "Third-person arm did not punch forward");
                onServer(client, () -> {
                    var world = client.getServer().getOverworld();
                    var golem = EntityType.IRON_GOLEM.create(world);
                    golem.refreshPositionAndAngles(0, 100, 0, 0, 0);
                    golem.setAiDisabled(true); world.spawnEntity(golem);
                    var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    player.refreshPositionAndAngles(0, 100, 2, 180, 0);
                    player.attack(golem); player.attack(golem); player.attack(golem);
                    check(HeadlessAccess.isHeadless(golem), "Golem not beheaded");
                    player.teleport(world, 4, 104, 5, 140, 30);
                });
            }
            if (ticks == 230) ScreenshotRecorder.saveScreenshot(client.runDirectory, "golem-clean-neck.png", client.getFramebuffer(), m -> {});
            if (ticks == 235) finish(client, "PASS hiddenFrames=" + hiddenFrames + " intactFrames=" + visibleFrames + " shakeFrames=" + shakeFrames + " gloveHiddenFrames=" + gloveHiddenFrames
                + " jabLateralRange=" + (maxRightX-minRightX) + " rightArmFrames=" + rightArmFrames + " leftArmFrames=" + leftArmFrames + " thirdPersonPunchFrames=" + thirdPersonPunchFrames + " flatGloveFrames=" + flatGloveFrames + " orderedPunchSounds=3 flightFrames=" + flightFrames + " maxFlightZ=" + maxFlightZ);
        } catch (Throwable failure) { finish(client, "FAIL " + failure); }
    }
    private void camera(MinecraftClient client) {
        var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
        player.teleport(client.getServer().getOverworld(), 0, 100, 7, 180, 0);
        player.getAbilities().flying = true; player.sendAbilitiesUpdate();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(com.nakami.mcheadfunction.head.HeadItems.item(HeadType.ENDERMAN)));
    }
    private void onServer(MinecraftClient client, Runnable action) {
        client.getServer().execute(() -> { try { action.run(); } catch (Throwable e) { serverFailure = e.toString(); } });
    }
    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private void finish(MinecraftClient client, String result) {
        finished = true;
        System.out.println("[BEHEADING-VALIDATION] " + result);
        try { Files.writeString(Path.of("result.txt"), result + "\n"); Files.write(Path.of("punch-sounds.txt"), soundLog); } catch (Exception e) { throw new RuntimeException(e); }
        client.scheduleStop();
    }
}
