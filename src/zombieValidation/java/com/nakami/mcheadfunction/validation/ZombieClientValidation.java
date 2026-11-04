package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.client.ZombieBloodEffects;
import com.nakami.mcheadfunction.client.ZombieFrenzyEffects;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.Arm;
import com.nakami.mcheadfunction.wear.ZombieBlood;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Networked real-client regression with no fake UI or client-only impact injection. */
public final class ZombieClientValidation implements ClientModInitializer {
    private boolean opening, setup, finished;
    private int ticks, phase, sceneTick, impactFrames, healingFrames;
    private volatile boolean ready;
    private volatile int cowId = -1;
    private volatile String failure;
    private volatile int serverAttacks;
    private int attackStart, guiAttacks, weaponStart, slowMineTicks, miningStart;
    private boolean miningStarted;
    private final java.util.Map<String, Integer> sounds = new java.util.concurrent.ConcurrentHashMap<>();
    private int cuesBeforeUnequip;
    private Arm firstStrike;
    private long armFrameBeforeItem;
    private final BlockPos slowBlock = new BlockPos(6, 101, 2), fastBlock = new BlockPos(8, 101, 2);
    private final long started = System.nanoTime();
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!world.isClient() && target.getId() == cowId) serverAttacks++;
            return net.minecraft.util.ActionResult.PASS;
        });
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            check((System.nanoTime() - started) / 1_000_000_000 < 180, "Timed out");
            check(failure == null, failure);
            if (!opening && client.currentScreen != null && client.getOverlay() == null) {
                opening = true;
                client.getSoundManager().registerListener((sound, set, distance) ->
                    sounds.merge(sound.getId().getPath(), 1, Integer::sum));
                org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getHandle());
                client.options.pauseOnLostFocus = false;
                client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                client.options.getViewDistance().setValue(4);
                client.options.getMaxFps().setValue(60);
                client.options.getParticles().setValue(ParticlesMode.MINIMAL);
                client.createIntegratedServerLoader().createAndStart("zombie-" + System.currentTimeMillis(),
                    new LevelInfo("Zombie blood validation", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                        new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(),
                    client.currentScreen);
            }
            if (client.player == null || client.world == null || client.getServer() == null) return;
            if (!setup) {
                setup = true;
                onServer(client, () -> {
                    var server = client.getServer(); var world = server.getOverworld();
                    world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
                    world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                    world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
                    world.setTimeOfDay(6000); world.setWeather(0, 100000, false, false);
                    for (int x = -12; x <= 12; x++) for (int z = -12; z <= 20; z++) {
                        world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
                    }
                    var player = server.getPlayerManager().getPlayerList().getFirst();
                    player.teleport(world, 0.5, 100, 4, 180, 20);
                    var cow = EntityType.COW.create(world); cow.setAiDisabled(true);
                    cow.refreshPositionAndAngles(0.5, 100, 2, 180, 0);
                    cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);
                    cow.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
                    cow.setHealth(1000); world.spawnEntity(cow); cowId = cow.getId(); ready = true;
                });
            }
            if (!ready || (phase < 8 && client.world.getEntityById(cowId) == null) || ticks++ < 100) return;
            if (phase == 0) {
                phase = 1; sceneTick = 0;
                onServer(client, () -> {
                    var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    p.changeGameMode(GameMode.SURVIVAL); p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
                    p.getHungerManager().setFoodLevel(20); p.getHungerManager().setSaturationLevel(20);
                });
            }
            sceneTick++;
            if (phase == 1) {
                if (sceneTick == 6) capture(client, "00-equip-tension.png");
                if (sceneTick == 20) {
                    check(ZombieFrenzyEffects.entryCues() == 1, "Missing or repeated entry cue");
                    check(ZombieFrenzyEffects.lastArmFrame() >= client.player.age - 2
                        && ZombieFrenzyEffects.renderedArms() == (client.player.getMainArm() == Arm.RIGHT ? 1 : 2), "Idle must render only the relaxed main arm");
                    capture(client, "01-full-health.png");
                }
                if (sceneTick == 205) capture(client, "01b-hunger-twitch.png");
                if (sceneTick == 220) {
                    check(client.player.getHungerManager().getFoodLevel() == 0, "Food did not drain to zero");
                    check(ZombieFrenzyEffects.hungerCues() == 1 && ZombieFrenzyEffects.breathCues() > 0,
                        "Hunger must trigger once and sustained breathing must run");
                    onServer(client, () -> {
                        var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                        p.setHealth(4); ZombieBlood.updateAttributes(p);
                    });
                    phase = 2; sceneTick = 0;
                }
            } else if (phase == 2) {
                if (sceneTick == 10) {
                    check(client.player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) > 16, "Attack speed did not sync");
                    check(com.nakami.mcheadfunction.behead.BeheadingHandler.aimingAtHead(client.player,
                        (net.minecraft.entity.LivingEntity) client.world.getEntityById(cowId)), "Fixture must aim at head");
                    capture(client, "02-low-health.png"); attack(client);
                }
                if (ZombieBloodEffects.activeBites() > 0) {
                    impactFrames++;
                    if (impactFrames <= 10) capture(client, String.format("impact-%02d.png", impactFrames));
                }
                if (ZombieBloodEffects.hasHealingBite()) healingFrames++;
                if (sceneTick == 40) {
                    check(ZombieBloodEffects.activeBites() == 0, "Impact must expire after its short lifetime");
                    check(impactFrames > 4 && healingFrames > 4, "No authoritative impact/healing visuals");
                    check(client.player.getHealth() > 8, "Stronger low-health lifesteal did not reach client");
                    check(ZombieBloodEffects.lastHudFrame() >= client.player.age - 2, "Zombie HUD was not rendered");
                    // Test a successful full-health hit, which must show claws but no blood return.
                    onServer(client, () -> client.getServer().getPlayerManager().getPlayerList().getFirst().setHealth(20));
                }
                if (sceneTick == 50) attack(client);
                if (sceneTick == 54) {
                    check(ZombieBloodEffects.activeBites() > 0 && !ZombieBloodEffects.hasHealingBite(), "Full health must not show healing blood");
                    capture(client, "03-full-health-hit.png");
                }
                if (sceneTick == 65) attack(client);
                if (sceneTick == 72) {
                    var target = (net.minecraft.entity.LivingEntity) client.world.getEntityById(cowId);
                    check(!com.nakami.mcheadfunction.head.HeadlessAccess.isHeadless(target), "Three networked fist head hits beheaded target");
                    capture(client, "03b-three-head-punches.png");
                }
                if (sceneTick == 75) {
                    onServer(client, () -> {
                        var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                        p.teleport(p.getServerWorld(), 6, 100, 0, 0, 0); p.setHealth(10);
                        p.getHungerManager().setFoodLevel(0); p.getHungerManager().setSaturationLevel(0);
                    });
                    phase = 3; sceneTick = 0;
                }
            } else if (phase == 3) {
                if (sceneTick == 10) { client.options.forwardKey.setPressed(true); client.options.sprintKey.setPressed(true); }
                if (sceneTick == 15) {
                    check(client.player.isSprinting(), "Empty-food zombie cannot start sprinting");
                    capture(client, "04-hungry-sprint.png");
                }
                if (sceneTick == 22) {
                    check(client.player.isSprinting(), "Empty-food zombie cannot maintain sprinting");
                    client.options.forwardKey.setPressed(false); client.options.sprintKey.setPressed(false);
                    onServer(client, () -> {
                        var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                    });
                }
                if (sceneTick == 32) {
                    check(client.player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) < 4, "Weapon retained fist speed");
                    onServer(client, () -> {
                        var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY); ZombieBlood.updateAttributes(p);
                    });
                }
                if (sceneTick == 42) { client.options.forwardKey.setPressed(true); client.options.sprintKey.setPressed(true); }
                if (sceneTick == 48) {
                    check(!client.player.isSprinting(), "Sprint hunger exemption leaked after unequip");
                    check(client.player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) < 0.11, "Movement bonus leaked after unequip");
                    client.options.forwardKey.setPressed(false); client.options.sprintKey.setPressed(false);
                    onServer(client, () -> {
                        var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                        p.teleport(p.getServerWorld(), 0.5, 100, 4, 180, 20);
                        p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
                        p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY); p.setHealth(4);
                        p.getHungerManager().setFoodLevel(20); p.getHungerManager().setSaturationLevel(20);
                        ZombieBlood.updateAttributes(p);
                    });
                    phase = 4; sceneTick = 0;
                }
            } else if (phase == 4) {
                // Keep the fixture aimed after cursor recapture around the inventory screen.
                client.player.setYaw(180); client.player.setPitch(20);
                if (sceneTick == 10) { attackStart = serverAttacks; pressAttack(client); }

                if (sceneTick == 18) onServer(client, () -> {
                    var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    var drop = new net.minecraft.entity.ItemEntity(p.getServerWorld(), p.getX(), p.getY(), p.getZ(),
                        new ItemStack(Items.ROTTEN_FLESH, 3));
                    drop.setPickupDelay(0); p.getServerWorld().spawnEntity(drop);
                });
                if (sceneTick == 30) {
                    check(ZombieBlood.unarmed(client.player), "Pickup interrupted empty hand on client");
                    check(client.player.getInventory().count(Items.ROTTEN_FLESH) == 3, "Dropped flesh did not sync into inventory");
                    capture(client, "05-held-fist-combat.png");
                }
                if (sceneTick == 40) {
                    check(serverAttacks - attackStart >= 5 && serverAttacks - attackStart <= 20,
                        "Held attack did not repeat at a bounded cooldown: " + (serverAttacks - attackStart));
                    client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));
                    client.options.attackKey.setPressed(true);
                }
                if (sceneTick == 45) guiAttacks = serverAttacks;
                if (sceneTick == 55) {
                    check(serverAttacks == guiAttacks, "Held attack leaked into inventory");
                    client.options.attackKey.setPressed(false); client.setScreen(null);
                }
                if (sceneTick == 65) {
                    check(serverAttacks == guiAttacks, "Released attack kept repeating");
                    onServer(client, () -> client.getServer().getPlayerManager().getPlayerList().getFirst()
                        .setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD)));
                }
                if (sceneTick == 75) { weaponStart = serverAttacks; pressAttack(client); }
                if (sceneTick == 100) client.options.attackKey.setPressed(false);
                if (sceneTick == 105) {
                    check(serverAttacks == weaponStart + 1, "Weapon held input must remain a single vanilla click: " + (serverAttacks - weaponStart));
                    onServer(client, () -> {
                        var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY); p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                        p.setHealth(20); p.getHungerManager().setFoodLevel(20);
                        p.getServerWorld().setBlockState(slowBlock, Blocks.OAK_PLANKS.getDefaultState());
                        p.getServerWorld().setBlockState(fastBlock, Blocks.OAK_PLANKS.getDefaultState());
                        p.teleport(p.getServerWorld(), 6.5, 100, 4, 180, 0);
                    });
                    phase = 5; sceneTick = 0; miningStarted = false;
                }
            } else if (phase == 5 || phase == 6) {
                BlockPos block = phase == 5 ? slowBlock : fastBlock;
                if (sceneTick == 10) {
                    check(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit && hit.getBlockPos().equals(block),
                        "Mining fixture must point at the correct block");
                    miningStart = sceneTick; miningStarted = true; pressAttack(client);
                }
                check(sceneTick < 140, "Held mining timed out");
                if (miningStarted && client.world.getBlockState(block).isAir()) {
                    int elapsed = sceneTick - miningStart;
                    client.options.attackKey.setPressed(false); miningStarted = false;
                    if (phase == 5) {
                        slowMineTicks = elapsed;
                        check(slowMineTicks >= 40, "Normal hand mining benchmark unexpectedly instant: " + slowMineTicks);
                        onServer(client, () -> {
                            var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                            check(p.getServerWorld().getBlockState(slowBlock).isAir(), "Baseline block break not accepted by server");
                            p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
                            p.setHealth(4); p.getHungerManager().setFoodLevel(20); p.getHungerManager().setSaturationLevel(20);
                            ZombieBlood.updateAttributes(p); p.teleport(p.getServerWorld(), 8.5, 100, 4, 180, 0);
                        });
                        phase = 6; sceneTick = 0;
                    } else {
                        check(elapsed > 0 && elapsed < slowMineTicks / 2, "Actual mining was not faster: normal=" + slowMineTicks + " zombie=" + elapsed);
                        onServer(client, () -> check(client.getServer().getOverworld().getBlockState(fastBlock).isAir(), "Fast block break not accepted by server"));
                        capture(client, "06-fast-hand-mining.png");
                        phase = 7; sceneTick = 0;
                        miningStart = elapsed;
                    }
                }
            } else if (phase == 7 && sceneTick == 10) {
                onServer(client, () -> {
                    var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    p.teleport(p.getServerWorld(), 0.5, 100, 4, 180, 20); p.setHealth(4);
                    p.getHungerManager().setFoodLevel(20); p.getHungerManager().setSaturationLevel(20);
                });
                phase = 8; sceneTick = 0;
            } else if (phase == 8) {
                if (sceneTick >= 50 && sceneTick < 75) {
                    client.player.setYaw(90); client.player.setPitch(5);
                }
                if (sceneTick <= 74) capture(client, String.format("frenzy-preview-%03d.png", sceneTick));
                if (sceneTick == 10) { attack(client); }
                if (sceneTick == 12) firstStrike = ZombieFrenzyEffects.strikingArm(client.player);
                if (sceneTick >= 11 && sceneTick <= 20) capture(client, String.format("frenzy-right-%02d.png", sceneTick - 10));
                if (sceneTick == 35) attack(client);
                if (sceneTick == 37) check(ZombieFrenzyEffects.strikingArm(client.player) != firstStrike,
                    "Consecutive fist swings must alternate arms");
                if (sceneTick >= 36 && sceneTick <= 45) capture(client, String.format("frenzy-left-%02d.png", sceneTick - 35));
                if (sceneTick == 50) client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                if (sceneTick == 56) {
                    var renderer = (net.minecraft.client.render.entity.PlayerEntityRenderer) client.getEntityRenderDispatcher().getRenderer(client.player);
                    var model = renderer.getModel();
                    check(Math.abs(model.rightArm.pitch) < 0.25F && Math.abs(model.leftArm.pitch) < 0.25F
                        && Math.abs(model.body.pitch) < 0.01F, "Third-person idle must have relaxed arms and no custom lean");
                    capture(client, "07-third-person-relaxed.png");
                }
                if (sceneTick == 60) attack(client);
                if (sceneTick == 62) capture(client, "08-third-person-strike.png");
                if (sceneTick == 75) {
                    client.options.setPerspective(Perspective.FIRST_PERSON);
                    client.player.setYaw(180); client.player.setPitch(20);
                    client.player.setMainArm(Arm.LEFT);
                    onServer(client, () -> {
                        var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                        p.setMainArm(Arm.LEFT); p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
                    });
                }
                if (sceneTick == 85) {
                    check(ZombieFrenzyEffects.renderedArms() == 2, "Shield must keep its vanilla offhand rendering");
                    capture(client, "09-left-handed-shield.png");
                    client.options.useKey.setPressed(true);
                    client.interactionManager.interactItem(client.player, Hand.OFF_HAND);
                }
                if (sceneTick == 90) {
                    check(client.player.isUsingItem() && !ZombieFrenzyEffects.posing(client.player), "Shield use must restore vanilla pose");
                    capture(client, "10-shield-use.png");
                    client.options.useKey.setPressed(false);
                    client.interactionManager.stopUsingItem(client.player);
                }
                if (sceneTick == 95) onServer(client, () -> {
                    var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                });
                if (sceneTick == 102) armFrameBeforeItem = ZombieFrenzyEffects.lastArmFrame();
                if (sceneTick == 108) {
                    check(!ZombieFrenzyEffects.posing(client.player) && ZombieFrenzyEffects.lastArmFrame() == armFrameBeforeItem,
                        "Held item must stop rendering frenzy arms");
                    onServer(client, () -> {
                        var p = client.getServer().getPlayerManager().getPlayerList().getFirst();
                        p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY); p.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
                        p.setHealth(4);
                        ((net.minecraft.entity.LivingEntity) p.getServerWorld().getEntityById(cowId)).setHealth(1);
                    });
                }
                if (sceneTick == 120) attack(client);
                if (sceneTick == 124) {
                    check(ZombieBloodEffects.hasKillingBite(), "Kill flag did not arrive from real combat");
                    capture(client, "11-kill-burst.png");
                }
                if (sceneTick == 130) {
                    check(ZombieBloodEffects.healingFlash() > 0, "Returning fragments must pulse hearts");
                    capture(client, "12-kill-return.png");
                    onServer(client, () -> client.getServer().getPlayerManager().getPlayerList().getFirst()
                        .equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY));
                }
                if (sceneTick == 140) {
                    cuesBeforeUnequip = ZombieFrenzyEffects.breathCues();
                    armFrameBeforeItem = ZombieFrenzyEffects.lastArmFrame();
                }
                if (sceneTick == 185) {
                    check(!ZombieFrenzyEffects.posing(client.player)
                        && ZombieFrenzyEffects.lastArmFrame() == armFrameBeforeItem
                        && ZombieFrenzyEffects.breathCues() == cuesBeforeUnequip, "Unequip must stop pose and breathing");
                    check(sounds.getOrDefault("entity.player.breath", 0) > 1
                        && sounds.getOrDefault("entity.warden.heartbeat", 0) > 0
                        && sounds.getOrDefault("entity.generic.eat", 0) > 0
                        && sounds.getOrDefault("entity.player.attack.knockback", 0) > 0,
                        "Sound engine did not receive breathing/heartbeat/return/impact: " + sounds);
                    capture(client, "13-unequipped.png");
                    finish(client, "PASS zombie gameplay + pickup + mining " + slowMineTicks + "/" + miningStart
                        + " ticks; equip/hunger/breath; alternating vanilla swings; first/third person; left hand/shield/use/item/unequip;"
                        + " authoritative kill/return/expiry; sound engine events=" + sounds + "; particles=MINIMAL");
                }
            }

        } catch (Throwable error) { finish(client, "FAIL " + error); }
    }
    private void pressAttack(MinecraftClient client) {
        client.mouse.lockCursor();
        client.options.attackKey.setPressed(true);
        net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.fromTranslationKey(client.options.attackKey.getBoundKeyTranslationKey()));
    }
    private void attack(MinecraftClient client) {
        client.interactionManager.attackEntity(client.player, client.world.getEntityById(cowId));
        client.player.swingHand(Hand.MAIN_HAND);
    }
    private void onServer(MinecraftClient client, Runnable action) {
        client.getServer().execute(() -> { try { action.run(); } catch (Throwable error) { failure = error.toString(); } });
    }
    private static void check(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {});
    }
    private void finish(MinecraftClient client, String result) {
        finished = true; client.options.useKey.setPressed(false); client.options.attackKey.setPressed(false); client.options.forwardKey.setPressed(false); client.options.sprintKey.setPressed(false);
        try { Files.writeString(Path.of("result.txt"), result + "\n"); }
        catch (Exception error) { throw new RuntimeException(error); }
        System.out.println("[ZOMBIE-VALIDATION] " + result); client.scheduleStop();
    }
}
