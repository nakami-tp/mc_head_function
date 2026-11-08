package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.client.CreeperCookingControl;
import com.nakami.mcheadfunction.client.CreeperGrenadeRender;
import com.nakami.mcheadfunction.creeper.CreeperCooking;
import com.nakami.mcheadfunction.creeper.FearSystem;
import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Actual left-click packets, server AI, tracked client levels, rendering, sound and loot. */
public final class FearClientValidation implements ClientModInitializer {
    private boolean opening, setup, finished, holdingAttack;
    private volatile boolean ready;
    private volatile String failure;
    public static volatile int sheepId = -1;
    private volatile int chickenId = -1, villagerId = -1, creeperId = -1;
    private int ticks;
    private final long started = System.nanoTime();
    private int levelOneFrames, levelTwoFrames, levelThreeFrames, primedSounds, tickSounds;
    public static int skullFrames, whiteFrames, greenFrames, shakenFrames;
    public static float minShake = Float.POSITIVE_INFINITY, maxShake = Float.NEGATIVE_INFINITY;
    private volatile boolean dropsVerified, playerHurt, creeperExploded;
    private volatile int grenadeId = -1, releaseFuse = -1;
    private boolean explosionBeforeDeadline, explosionAfterDeadline;
    private volatile float previousHealth;
    public static volatile int endermanId = -1;
    public static int panicMouthFrames, recoveredMouthFrames;
    private int endermanScreams, endermanStares, screamsAfterRecovery;
    private volatile boolean endermanDropsVerified, endermanFled;
    private Vec3d endermanStart;

    public static void observeEnderman(LivingEntity entity, float headY) {
        if (entity.getId() != endermanId) return;
        if (FearSystem.level(entity) == 3 && headY == -18) panicMouthFrames++;
        if (FearSystem.level(entity) == 0 && headY == -13) recoveredMouthFrames++;
    }

    public static void observeHead(ThrownHeadEntity grenade) {
        skullFrames++;
        if (CreeperGrenadeRender.flashing(grenade)) whiteFrames++; else greenFrames++;
    }
    public static void observeMob(LivingEntity entity, float x) {
        if (entity.getId() == sheepId && FearSystem.level(entity) >= 2) {
            shakenFrames++;
            minShake = Math.min(minShake, x); maxShake = Math.max(maxShake, x);
        }
    }
    @Override public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            // This isolated client owns scripted input. Desktop focus changes
            // must not synthesize a release before the scheduled release tick.
            if (holdingAttack && !finished) {
                client.onWindowFocusChanged(true);
                client.options.attackKey.setPressed(true);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            check((System.nanoTime() - started) / 1_000_000_000 < 240, "Timed out");
            check(failure == null, failure);
            if (!opening && client.currentScreen != null && client.getOverlay() == null) {
                opening = true;
                client.options.pauseOnLostFocus = false;
                client.options.getEnableVsync().setValue(false);
                client.options.getViewDistance().setValue(4);
                client.options.getMaxFps().setValue(60);
                client.options.getParticles().setValue(ParticlesMode.MINIMAL);
                client.options.hudHidden = false;
                client.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER).setValue(1.0);
                client.getSoundManager().registerListener((sound, set, range) -> {
                    if (sound.getId().getPath().equals("entity.creeper.primed")) primedSounds++;
                    if (sound.getId().getPath().equals("block.dispenser.fail")) tickSounds++;
                    if (endermanId >= 0 && sound.getId().getPath().equals("entity.enderman.scream")) endermanScreams++;
                    if (endermanId >= 0 && sound.getId().getPath().equals("entity.enderman.stare")) endermanStares++;
                });
                client.createIntegratedServerLoader().createAndStart("fear-" + System.currentTimeMillis(),
                    new LevelInfo("Cooking and fear regression", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                        new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), client.currentScreen);
            }
            if (client.player == null || client.world == null || client.getServer() == null) return;
            if (!setup) {
                setup = true;
                server(client, () -> setup(client));
            }
            if (!ready || (ticks == 0 && client.world.getEntityById(sheepId) == null)) return;
            client.getToastManager().clear();
            ticks++;
            // Building the shaded arena and tracking its new entity may take several server ticks.
            if (ticks == 446 && !(client.world.getEntityById(endermanId) instanceof EndermanEntity)) {
                ticks--;
                return;
            }
            // All gameplay input uses the same attack key events as the actual player.
            if (ticks == 40 || ticks == 91 || ticks == 295 || ticks == 346) press(client);
            if (ticks == 85 || ticks == 140 || ticks == 340 || ticks == 395) release(client);
            var sheep = client.world.getEntityById(sheepId);
            if (sheep instanceof LivingEntity living) {
                int level = FearSystem.level(living);
                if (level == 1) levelOneFrames++;
                if (level == 2) levelTwoFrames++;
                if (level == 3) levelThreeFrames++;
            }
            if (ticks >= 42 && ticks <= 51) {
                var held = CreeperCookingControl.held(client.player);
                check(held != null && !CreeperGrenadeRender.flashing(held), "Ignition shakes must finish before the fuse flashes");
                screenshot(client, String.format("pin-%03d", ticks - 42));
            }
            if (ticks >= 52 && ticks <= 78) {
                var held = CreeperCookingControl.held(client.player);
                check(held != null && held.fuseTicks() > 0 && held.fuseTicks() < CreeperCooking.FUSE_TICKS, "Actual attack input failed to cook on client");
                if (ticks % 2 == 0) screenshot(client, String.format("warm-%03d", (ticks - 52) / 2));
                if (ticks == 55 || ticks == 66 || ticks == 75) screenshot(client, "held-" + ticks);
            }
            if (ticks == 82) {
                // Throw away from targets; only hand-held cooking is a fear source.
                client.player.setPitch(-65);
                server(client, () -> {
                    var grenade = CreeperCooking.held(player(client));
                    check(grenade != null, "Server missing reserved grenade");
                    grenadeId = grenade.getId();
                });
            }
            if (ticks == 89) server(client, () -> {
                var grenade = (ThrownHeadEntity) player(client).getServerWorld().getEntityById(grenadeId);
                check(grenade != null && !grenade.isCooking() && grenade.fuseTicks() >= 50 && grenade.fuseTicks() <= 60, "Release must preserve cooked fuse, grenade=" + grenade + " cooking=" + (grenade != null && grenade.isCooking()) + " fuse=" + (grenade == null ? -1 : grenade.fuseTicks()));
                releaseFuse = grenade.fuseTicks();
            });
            if (ticks == 88) client.player.setPitch(12);
            if (ticks == 90) screenshot(client, "level-two");
            if (ticks == 92) {
                check(client.world.getEntityById(grenadeId) != null, "Grenade exploded before cooked deadline");
                explosionBeforeDeadline = true;
            }
            if (ticks == 148) {
                check(client.world.getEntityById(grenadeId) == null, "Grenade did not explode after cooked deadline");
                explosionAfterDeadline = true;
            }
            if (ticks == 130) {
                server(client, () -> {
                    var world = player(client).getServerWorld();
                    check(FearSystem.level((LivingEntity) world.getEntityById(sheepId)) == 3, "Server missing level three");
                    var items = world.getEntitiesByClass(ItemEntity.class, player(client).getBoundingBox().expand(18), e -> true);
                    check(items.stream().filter(e -> e.getStack().isOf(Items.EGG)).mapToInt(e -> e.getStack().getCount()).sum() == 1, "Chicken reward mismatch");
                    check(items.stream().filter(e -> e.getStack().isOf(Items.EMERALD)).mapToInt(e -> e.getStack().getCount()).sum() == 1, "Villager reward mismatch");
                    check(items.stream().anyMatch(e -> e.getStack().isOf(Items.WHITE_WOOL)), "Missing wool");
                    dropsVerified = true;
                });
                screenshot(client, "level-three-drops");
            }
            if (ticks == 138) client.player.setPitch(-65);
            if (ticks == 145) { client.player.setPitch(12); screenshot(client, "rewards"); }
            if (ticks == 255) {
                check(FearSystem.level((LivingEntity) sheep) == 0, "Client fear did not clear after decay");
                check(levelOneFrames > 5 && levelTwoFrames > 5 && levelThreeFrames > 5, "Missing tracked fear stages");
                check(shakenFrames > 5 && maxShake - minShake > 0.02, "Actual mob render did not shake");
                check(whiteFrames > 0 && greenFrames > 0 && skullFrames > 10, "No rendered white/green fuse alternation");
                check(primedSounds >= 2 && tickSounds > 2, "Ignition/ticking sound events failed to reach client");
                screenshot(client, "recovered");
                server(client, () -> prepareCreeper(client));
            }
            if (ticks >= 310 && ticks <= 385 && ticks % 5 == 0) {
                // Follow the fleeing mob, keeping the threatening player within hearing range.
                server(client, () -> {
                    var mob = player(client).getServerWorld().getEntityById(creeperId);
                    if (mob instanceof CreeperEntity creeper && !FearSystem.retaliating(creeper)) {
                        creeper.setAiDisabled(false);
                        player(client).teleport(player(client).getServerWorld(), creeper.getX(), 100, creeper.getZ() + 4, 180, 12);
                    }
                });
            }
            if (ticks == 337 || ticks == 392) client.player.setPitch(-65);
            if (ticks == 343 || ticks == 398) client.player.setPitch(12);
            if (ticks >= 360 && ticks <= 430 && ticks % 2 == 0) screenshot(client, String.format("retaliation-%03d", (ticks - 360) / 2));
            if (ticks == 374) screenshot(client, "creeper-pauses");
            if (ticks == 397) {
                var creeper = client.world.getEntityById(creeperId);
                check(creeper instanceof CreeperEntity && ((CreeperEntity) creeper).isIgnited(), "Client creeper did not ignite");
                screenshot(client, "creeper-ignited");
            }
            if (ticks >= 397 && ticks <= 430) server(client, () -> {
                playerHurt |= player(client).getHealth() < previousHealth;
            });
            if (ticks == 430) server(client, () -> {
                var player = player(client);
                creeperExploded = player.getServerWorld().getEntityById(creeperId) == null;
                playerHurt |= player.getHealth() < previousHealth;
                check(creeperExploded && playerHurt, "Retaliation must self-destruct and hurt survival player, health=" + player.getHealth());
            });
            if (ticks == 435) screenshot(client, "after-explosion");
            if (ticks == 445) {
                check(dropsVerified && playerHurt && creeperExploded && explosionBeforeDeadline && explosionAfterDeadline, "Missing final server assertions");
                server(client, () -> prepareEnderman(client));
            }
            if (ticks > 445) tickEnderman(client);
            if (ticks == 785) {
                check(endermanDropsVerified && endermanFled && panicMouthFrames > 5 && recoveredMouthFrames > 5,
                    "Enderman drop, fleeing or actual open/recovered mouth render missing");
                check(endermanStares >= 1 && endermanScreams >= 2 && endermanScreams == screamsAfterRecovery,
                    "Enderman must cry repeatedly during panic and stop after recovery");
                finish(client, "PASS actualLeftClick=true levels=" + levelOneFrames + "," + levelTwoFrames + "," + levelThreeFrames
                    + " skullFrames=" + skullFrames + " white=" + whiteFrames + " green=" + greenFrames
                    + " shaken=" + shakenFrames + " shakeSpan=" + (maxShake-minShake)
                    + " ignitionSounds=" + primedSounds + " tickSounds=" + tickSounds
                    + " drops=egg,wool,emerald recovery=true cookedReleaseFuse=" + releaseFuse
                    + " retaliationExplosion=true survivalPlayerHurt=true endermanBlockDrop=true endermanFled=true"
                    + " panicMouthFrames=" + panicMouthFrames + " recoveredMouthFrames=" + recoveredMouthFrames
                    + " endermanStares=" + endermanStares + " endermanScreams=" + endermanScreams + " particles=MINIMAL");
            }
        } catch (Throwable error) { finish(client, "FAIL tick=" + ticks + " " + error); }
    }
    private void setup(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_LOOT).set(true, client.getServer());
        world.setTimeOfDay(6000); world.setWeather(0, 100000, false, false);
        for (int x=-35; x<=35; x++) for(int z=-35; z<=35; z++) world.setBlockState(new BlockPos(x,99,z), Blocks.SMOOTH_QUARTZ.getDefaultState());
        sheepId = spawn(client, EntityType.SHEEP, -3).getId();
        chickenId = spawn(client, EntityType.CHICKEN, 0).getId();
        villagerId = spawn(client, EntityType.VILLAGER, 3).getId();
        var player = player(client);
        player.teleport(world, 0, 100, 7, 180, 12);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CREEPER_HEAD, 12));
        player.getHungerManager().setFoodLevel(20);
        ready = true;
    }
    private <T extends MobEntity> T spawn(MinecraftClient client, EntityType<T> type, double x) {
        var world = client.getServer().getOverworld();
        T mob = type.create(world); mob.refreshPositionAndAngles(x,100,0,0,0);
        mob.setAiDisabled(true); mob.setPersistent(); world.spawnEntity(mob); return mob;
    }
    private void prepareCreeper(MinecraftClient client) {
        var world = player(client).getServerWorld();
        for(var mob : world.getEntitiesByClass(MobEntity.class, player(client).getBoundingBox().expand(30), e -> true)) mob.discard();
        // Keep sheep as a passive tracked anchor for the validation tick readiness check.
        sheepId = spawn(client, EntityType.SHEEP, -25).getId();
        var creeper = spawn(client, EntityType.CREEPER, 0); creeperId = creeper.getId();
        var player = player(client); player.setHealth(20); previousHealth = 20;
        // Survive the existing retaliation check so the same client can continue into Enderman validation.
        player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, Items.NETHERITE_HELMET.getDefaultStack());
        player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE.getDefaultStack());
        player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS.getDefaultStack());
        player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, Items.NETHERITE_BOOTS.getDefaultStack());
        player.teleport(world,0,100,4,180,12);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.CREEPER_HEAD,8));
    }
    private void prepareEnderman(MinecraftClient client) {
        var player = player(client);
        var world = player.getServerWorld();
        var pending = CreeperCooking.held(player);
        if (pending != null) pending.discard();
        for (var mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(50), entity -> true)) mob.discard();
        // Shade prevents vanilla daylight teleportation from obscuring the fleeing behavior.
        for (int x = -35; x <= 35; x++) for (int z = -35; z <= 35; z++) {
            world.setBlockState(new BlockPos(x, 99, z), Blocks.SMOOTH_QUARTZ.getDefaultState());
            world.setBlockState(new BlockPos(x, 104, z),
                ((x & 3) == 0 && (z & 3) == 0 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_QUARTZ).getDefaultState());
        }
        var enderman = spawn(client, EntityType.ENDERMAN, 0);
        enderman.setCarriedBlock(Blocks.GRASS_BLOCK.getDefaultState());
        enderman.getRandom().setSeed(42);
        endermanStart = enderman.getPos();
        endermanId = enderman.getId();
        player.setHealth(20);
        player.teleport(world, 0, 100, 6, 180, 12);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CREEPER_HEAD, 8));
    }
    private void tickEnderman(MinecraftClient client) {
        if (ticks == 465) {
            var enderman = client.world.getEntityById(endermanId);
            check(enderman instanceof EndermanEntity mob && mob.getCarriedBlock() != null,
                "Client must initially render the carried block, id=" + endermanId + " entity=" + enderman
                    + " block=" + (enderman instanceof EndermanEntity mob ? mob.getCarriedBlock() : null));
            screenshot(client, "enderman-before");
        }
        if (ticks == 470 || ticks == 565) press(client);
        // Switching slots uses the normal defuse path, keeping blasts out of the shaded arena.
        if (ticks == 555 || ticks == 640) client.player.getInventory().selectedSlot = 1;
        if (ticks == 640) recoveredMouthFrames = 0;
        if (ticks == 558 || ticks == 643) release(client);
        if (ticks == 562) client.player.getInventory().selectedSlot = 0;
        if (ticks == 552) {
            var enderman = client.world.getEntityById(endermanId);
            check(enderman instanceof EndermanEntity mob && FearSystem.level(mob) == 3 && mob.getCarriedBlock() == null,
                "Client must receive level three and cleared carried block");
            screenshot(client, "enderman-screaming");
            server(client, () -> {
                var mob = (EndermanEntity) player(client).getServerWorld().getEntityById(endermanId);
                check(mob.getCarriedBlock() == null && mob.getTarget() == null, "Panic must clear block without attacking");
                var items = player(client).getServerWorld().getEntitiesByClass(ItemEntity.class, mob.getBoundingBox().expand(8),
                    item -> item.getStack().isOf(Items.GRASS_BLOCK));
                check(items.stream().mapToInt(item -> item.getStack().getCount()).sum() == 1, "Enderman must drop exactly one grass block");
                endermanDropsVerified = true;
                mob.setAiDisabled(false);
            });
        }
        if (ticks >= 565 && ticks <= 640 && ticks % 5 == 0) server(client, () -> {
            var player = player(client);
            var mob = (EndermanEntity) player.getServerWorld().getEntityById(endermanId);
            check(mob != null && mob.getTarget() == null, "Frightened Enderman must remain alive and not attack");
            if (mob.getPos().subtract(endermanStart).horizontalLength() > 2) endermanFled = true;
            player.teleport(player.getServerWorld(), mob.getX(), 100, mob.getZ() + 5, 180, 12);
        });
        if (ticks >= 570 && ticks <= 630 && ticks % 10 == 0) screenshot(client, "enderman-flee-" + ticks);
        if (ticks == 750) server(client, () -> {
            var player = player(client);
            var mob = (EndermanEntity) player.getServerWorld().getEntityById(endermanId);
            check(mob != null && FearSystem.level(mob) == 0, "Server Enderman must recover fully");
            mob.setAiDisabled(true);
            mob.setYaw(0);
            mob.setHeadYaw(0);
            mob.setBodyYaw(0);
            player.teleport(player.getServerWorld(), mob.getX(), 100, mob.getZ() + 5, 180, 12);
        });
        if (ticks == 760) {
            var enderman = client.world.getEntityById(endermanId);
            check(enderman instanceof EndermanEntity mob && FearSystem.level(mob) == 0, "Client Enderman must recover fully");
            screamsAfterRecovery = endermanScreams;
            screenshot(client, "enderman-recovered");
        }
    }
    private void press(MinecraftClient client) {
        holdingAttack = true;
        client.onWindowFocusChanged(true);
        client.options.attackKey.setPressed(true);
        KeyBinding.onKeyPressed(InputUtil.Type.MOUSE.createFromCode(0));
    }
    private void release(MinecraftClient client) { holdingAttack = false; client.options.attackKey.setPressed(false); }
    private void screenshot(MinecraftClient client,String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory,name+".png",client.getFramebuffer(),m -> {}); }
    private ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayerList().getFirst(); }
    private void server(MinecraftClient client,Runnable action) { client.getServer().execute(() -> { try { action.run(); } catch(Throwable error) { failure=error.toString(); } }); }
    private static void check(boolean pass,String message) { if(!pass) throw new IllegalStateException(message); }
    private void finish(MinecraftClient client,String result) {
        finished=true; holdingAttack=false; client.options.attackKey.setPressed(false);
        try { Files.writeString(Path.of("result.txt"),result+"\n"); } catch(Exception error) { throw new RuntimeException(error); }
        client.scheduleStop();
    }
}
