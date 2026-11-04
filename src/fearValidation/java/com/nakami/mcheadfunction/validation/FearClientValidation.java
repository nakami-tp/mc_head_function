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
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Actual left-click packets, server AI, tracked client levels, rendering, sound and loot. */
public final class FearClientValidation implements ClientModInitializer {
    private boolean opening, setup, finished;
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

    public static void observeHead(ThrownHeadEntity grenade) {
        skullFrames++;
        if (CreeperGrenadeRender.flashing(grenade.fuseTicks())) whiteFrames++; else greenFrames++;
    }
    public static void observeMob(LivingEntity entity, float x) {
        if (entity.getId() == sheepId && FearSystem.level(entity) >= 2) {
            shakenFrames++;
            minShake = Math.min(minShake, x); maxShake = Math.max(maxShake, x);
        }
    }
    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
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
            if (ticks >= 52 && ticks <= 78) {
                var held = CreeperCookingControl.held(client.player);
                check(held != null && held.fuseTicks() > 0 && held.fuseTicks() < 60, "Actual attack input failed to cook on client");
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
                check(grenade != null && !grenade.isCooking() && grenade.fuseTicks() < 22, "Release must preserve cooked fuse, grenade=" + grenade + " cooking=" + (grenade != null && grenade.isCooking()) + " fuse=" + (grenade == null ? -1 : grenade.fuseTicks()));
                releaseFuse = grenade.fuseTicks();
            });
            if (ticks == 88) client.player.setPitch(12);
            if (ticks == 90) screenshot(client, "level-two");
            if (ticks == 92) {
                check(client.world.getEntityById(grenadeId) != null, "Grenade exploded before cooked deadline");
                explosionBeforeDeadline = true;
            }
            if (ticks == 110) {
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
            if (ticks == 430) server(client, () -> {
                var player = player(client);
                creeperExploded = player.getServerWorld().getEntityById(creeperId) == null;
                playerHurt = player.getHealth() < previousHealth;
                check(creeperExploded && playerHurt, "Retaliation must self-destruct and hurt survival player, health=" + player.getHealth());
            });
            if (ticks == 435) screenshot(client, "after-explosion");
            if (ticks == 445) {
                check(dropsVerified && playerHurt && creeperExploded && explosionBeforeDeadline && explosionAfterDeadline, "Missing final server assertions");
                finish(client, "PASS actualLeftClick=true levels=" + levelOneFrames + "," + levelTwoFrames + "," + levelThreeFrames
                    + " skullFrames=" + skullFrames + " white=" + whiteFrames + " green=" + greenFrames
                    + " shaken=" + shakenFrames + " shakeSpan=" + (maxShake-minShake)
                    + " ignitionSounds=" + primedSounds + " tickSounds=" + tickSounds
                    + " drops=egg,wool,emerald recovery=true cookedReleaseFuse=" + releaseFuse
                    + " retaliationExplosion=true survivalPlayerHurt=true particles=MINIMAL");
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
        player.teleport(world,0,100,4,180,12);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.CREEPER_HEAD,8));
    }
    private void press(MinecraftClient client) {
        org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getHandle());
        client.options.attackKey.setPressed(true);
        KeyBinding.onKeyPressed(InputUtil.Type.MOUSE.createFromCode(0));
    }
    private void release(MinecraftClient client) { client.options.attackKey.setPressed(false); }
    private void screenshot(MinecraftClient client,String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory,name+".png",client.getFramebuffer(),m -> {}); }
    private ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayerList().getFirst(); }
    private void server(MinecraftClient client,Runnable action) { client.getServer().execute(() -> { try { action.run(); } catch(Throwable error) { failure=error.toString(); } }); }
    private static void check(boolean pass,String message) { if(!pass) throw new IllegalStateException(message); }
    private void finish(MinecraftClient client,String result) {
        finished=true; client.options.attackKey.setPressed(false);
        try { Files.writeString(Path.of("result.txt"),result+"\n"); } catch(Exception error) { throw new RuntimeException(error); }
        client.scheduleStop();
    }
}
