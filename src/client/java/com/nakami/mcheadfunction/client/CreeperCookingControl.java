package com.nakami.mcheadfunction.client;

import com.nakami.mcheadfunction.entity.ThrownHeadEntity;
import com.nakami.mcheadfunction.net.CreeperCookC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/** Only press/release intent crosses the network; fuse and inventory belong to the server. */
public final class CreeperCookingControl {
    private static boolean pressed;
    private CreeperCookingControl() { }
    public static boolean activeInput() { return pressed; }
    public static void begin() {
        if (pressed) return;
        pressed = true;
        ClientPlayNetworking.send(new CreeperCookC2SPayload(true));
    }
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) { pressed = false; return; }
        if (pressed && (!client.options.attackKey.isPressed() || client.currentScreen != null || !client.isWindowFocused())) {
            pressed = false;
            ClientPlayNetworking.send(new CreeperCookC2SPayload(false));
        }
    }
    public static ThrownHeadEntity held(PlayerEntity player) {
        return player.getWorld().getEntitiesByClass(ThrownHeadEntity.class, player.getBoundingBox().expand(3),
            entity -> entity.isCooking() && entity.getOwner() == player).stream().findFirst().orElse(null);
    }
}
