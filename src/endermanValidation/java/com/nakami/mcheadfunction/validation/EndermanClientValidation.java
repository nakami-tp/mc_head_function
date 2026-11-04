package com.nakami.mcheadfunction.validation;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Isolated real-player network/landing regression. */
public final class EndermanClientValidation implements ClientModInitializer {
    private boolean opening, setup, triggered, finished;
    private int ticks, frames;
    private volatile boolean ready;
    private volatile Vec3d landing;
    private volatile String failure;
    private final long started = System.nanoTime();

    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            check((System.nanoTime() - started) / 1_000_000_000 < 180, "Timed out");
            check(failure == null, failure);
            if (!opening && client.currentScreen != null && client.getOverlay() == null) {
                opening = true;
                client.options.pauseOnLostFocus = false;
                client.options.getViewDistance().setValue(4);
                client.createIntegratedServerLoader().createAndStart("enderman-" + System.currentTimeMillis(),
                    new LevelInfo("Enderman dodge validation", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                        new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(),
                    client.currentScreen);
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
                    for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
                        for (int y = 99; y <= 103; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.STONE.getDefaultState());
                    }
                    world.setBlockState(new BlockPos(0, 100, 0), Blocks.AIR.getDefaultState());
                    world.setBlockState(new BlockPos(0, 101, 0), Blocks.AIR.getDefaultState());
                    var player = server.getPlayerManager().getPlayerList().getFirst();
                    player.teleport(world, 0.5, 100, 0.5, 0, 0);
                    player.changeGameMode(GameMode.SURVIVAL);
                    player.equipStack(EquipmentSlot.HEAD, new ItemStack(HeadItems.item(HeadType.ENDERMAN)));
                    ready = true;
                });
            }
            if (!ready || ticks++ < 100) return;
            if (!triggered) {
                triggered = true;
                onServer(client, () -> {
                    var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    float health = player.getHealth();
                    player.damage(player.getDamageSources().generic(), 4);
                    check(player.getHealth() == health, "Triggering damage was not cancelled");
                    check(PlayerHeadAccess.state(player).endermanDodgeCooldown == 40, "Wrong cooldown");
                    check(!player.getWorld().isSpaceEmpty(player), "Expected embedded landing");
                    landing = player.getPos();
                });
            }
            if (landing == null) return;
            if (++frames < 10) return;
            check(client.player.getPos().squaredDistanceTo(landing) < 0.05, "Client did not receive trapped landing");
            check(client.player.getHealth() < 20, "Natural wall suffocation did not reach client");
            check(!client.world.isSpaceEmpty(client.player), "Client is not inside wall");
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "trapped-landing.png", client.getFramebuffer(), message -> {});
            finish(client, "PASS server hit cancelled; cooldown=40; client trapped landing=" + landing + "; health=" + client.player.getHealth());
        } catch (Throwable error) { finish(client, "FAIL " + error); }
    }

    private void onServer(MinecraftClient client, Runnable action) {
        client.getServer().execute(() -> { try { action.run(); } catch (Throwable error) { failure = error.toString(); } });
    }
    private static void check(boolean pass, String message) { if (!pass) throw new IllegalStateException(message); }
    private void finish(MinecraftClient client, String result) {
        finished = true;
        try { Files.writeString(Path.of("result.txt"), result + "\n"); }
        catch (Exception error) { throw new RuntimeException(error); }
        System.out.println("[ENDERMAN-VALIDATION] " + result);
        client.scheduleStop();
    }
}
