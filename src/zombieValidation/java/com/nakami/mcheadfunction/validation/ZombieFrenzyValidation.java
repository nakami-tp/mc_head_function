package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.client.ZombieBloodEffects;
import com.nakami.mcheadfunction.wear.ZombieBlood;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Real eating input, held attacks and forward movement through the normal client/server path. */
public final class ZombieFrenzyValidation implements ClientModInitializer {
    private boolean opening, setup;
    private volatile boolean finished, attacking;
    private volatile LivingEntity serverTarget;
    private int ticks, combatTicks, visibleHits;
    private volatile int targetId = -1, hits, lastHitTick, fastHits, combatServerTicks;
    private volatile boolean ready;
    private volatile double distance, peak;
    private volatile float previousHealth = 1000;
    private volatile String failure;
    private final long started = System.nanoTime();

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!attacking || finished) return;
            try {
                combatServerTicks++;
                var p = server.getPlayerManager().getPlayerList().getFirst();
                var target = (LivingEntity) server.getOverworld().getEntityById(targetId);
                check(target != null, "Target disappeared: reason=" + serverTarget.getRemovalReason()
                    + ", health=" + serverTarget.getHealth() + ", difficulty=" + server.getOverworld().getDifficulty());
                check(p.getHealth() == 1, "Frenzy changed low health");
                check(!p.damage(p.getDamageSources().generic(), 10), "Frenzy failed immunity during combat");
                distance = 2 - target.getZ(); peak = Math.max(peak, target.getY() - 100);
                if (target.getHealth() < previousHealth) {
                    if (hits > 0 && server.getTicks() - lastHitTick <= 3) fastHits++;
                    hits++; lastHitTick = server.getTicks(); previousHealth = target.getHealth();
                }
            } catch (Throwable error) { failure = error.toString(); }
        });
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            check((System.nanoTime() - started) / 1_000_000_000 < 150, "Timed out");
            check(failure == null, failure);
            if (!opening && client.currentScreen != null && client.getOverlay() == null) {
                opening = true;
                org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getHandle());
                client.options.pauseOnLostFocus = false;
                client.options.getViewDistance().setValue(4);
                client.options.getMaxFps().setValue(60);
                client.options.getParticles().setValue(ParticlesMode.MINIMAL);
                client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                client.createIntegratedServerLoader().createAndStart("flesh-frenzy-" + System.currentTimeMillis(),
                    new LevelInfo("Flesh frenzy validation", GameMode.CREATIVE, false, Difficulty.HARD, true,
                        new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(),
                    client.currentScreen);
            }
            if (client.player == null || client.world == null || client.getServer() == null) return;
            if (!setup) {
                setup = true;
                client.getServer().execute(() -> {
                    try {
                        var server = client.getServer(); var world = server.getOverworld();
                        server.setDifficulty(Difficulty.HARD, true);
                        server.setDifficultyLocked(true);
                        world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
                        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                        world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
                        world.setTimeOfDay(6000); world.setWeather(0, 100000, false, false);
                        for (int x = -10; x <= 10; x++) for (int z = -50; z <= 14; z++)
                            world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
                        var p = server.getPlayerManager().getPlayerList().getFirst();
                        p.teleport(world, 0.5, 100, 4, 180, 15);
                        p.changeGameMode(GameMode.SURVIVAL);
                        p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
                        p.setHealth(1); p.getHungerManager().setFoodLevel(0); p.getHungerManager().setSaturationLevel(0);
                        p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.ROTTEN_FLESH));
                        var target = EntityType.HUSK.create(world);
                        target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
                        target.refreshPositionAndAngles(0.5, 100, 2, 0, 0);
                        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);
                        target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.RESISTANCE, 1000, 2, false, false));
                        target.setPersistent(); target.setHealth(1000);
                        check(world.spawnEntity(target), "Target spawn rejected");
                        serverTarget = target; targetId = target.getId(); ready = true;
                    } catch (Throwable error) { failure = error.toString(); }
                });
            }
            if (!ready || client.world.getEntityById(targetId) == null || ++ticks < 30) return;
            if (!attacking) {
                if (ticks == 30) {
                    client.mouse.lockCursor(); client.options.useKey.setPressed(true);
                    client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
                }
                check(ticks < 100, "Eating did not grant frenzy");
                if (!ZombieBlood.frenzied(client.player)) return;
                var icon = client.getStatusEffectSpriteManager().getSprite(ZombieBlood.FRENZY);
                check(!icon.getContents().getId().equals(net.minecraft.client.texture.MissingSprite.getMissingSpriteId()),
                    "Frenzy inventory icon must resolve");
                client.options.useKey.setPressed(false);
                check(client.player.getMainHandStack().isEmpty(), "Last flesh bite must leave an empty fist");
                check(client.player.getHealth() == 1, "Flesh must not heal");
                attacking = true;
                client.options.attackKey.setPressed(true);
                net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.fromTranslationKey(
                    client.options.attackKey.getBoundKeyTranslationKey()));
            }
            combatTicks++;
            var target = (LivingEntity) client.world.getEntityById(targetId);
            var delta = target.getPos().add(0, 1, 0).subtract(client.player.getEyePos());
            client.player.setYaw((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength())));
            client.options.forwardKey.setPressed(client.player.squaredDistanceTo(target) > 1.8 * 1.8);
            if (ZombieBloodEffects.activeBites() > 0) visibleHits++;
            check(!ZombieBloodEffects.hasHealingBite(), "Frenzy must not show healing return");
            if (combatTicks == 20) client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            if (combatTicks <= 80) ScreenshotRecorder.saveScreenshot(client.runDirectory,
                String.format("juggle-%03d.png", combatTicks), client.getFramebuffer(), message -> {});
            if (combatTicks == 80) {
                check(hits >= 20 && fastHits >= 15, "Insufficient real rapid damage: " + hits + "/" + fastHits);
                check(distance > 6, "Target did not travel forward: " + distance);
                check(peak > 0.1 && peak < 0.9, "Juggle height out of range: " + peak);
                check(visibleHits >= 30, "Authoritative impacts did not render repeatedly: " + visibleHits);
                finish(client, "PASS real eating + held fist attacks + forward pursuit; serverTicks=" + combatServerTicks + ", hits=" + hits
                    + ", intervals<=3 ticks=" + fastHits + ", distance=" + distance + ", peak=" + peak
                    + "; health=1, incoming damage blocked, no healing visuals; particles=MINIMAL");
            }
        } catch (Throwable error) { finish(client, "FAIL " + error); }
    }

    private static void check(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }
    private void finish(MinecraftClient client, String result) {
        finished = true;
        client.options.useKey.setPressed(false); client.options.attackKey.setPressed(false); client.options.forwardKey.setPressed(false);
        try { Files.writeString(Path.of("result.txt"), result + "\n"); }
        catch (Exception error) { throw new RuntimeException(error); }
        System.out.println("[ZOMBIE-FRENZY-VALIDATION] " + result); client.scheduleStop();
    }
}
