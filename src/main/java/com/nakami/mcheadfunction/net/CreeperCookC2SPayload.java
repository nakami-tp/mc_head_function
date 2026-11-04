package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record CreeperCookC2SPayload(boolean pressed) implements CustomPayload {
    public static final Id<CreeperCookC2SPayload> ID = new Id<>(McHeadFunction.id("creeper_cook"));
    public static final PacketCodec<RegistryByteBuf, CreeperCookC2SPayload> CODEC =
        PacketCodec.tuple(PacketCodecs.BOOL, CreeperCookC2SPayload::pressed, CreeperCookC2SPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
