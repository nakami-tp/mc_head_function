package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Authoritative successful bare-hand hit; healed is actual, capped health gained. */
public record ZombieBiteS2CPayload(int attacker, double x, double y, double z, float strength, float healed, boolean killed) implements CustomPayload {
    public static final Id<ZombieBiteS2CPayload> ID = new Id<>(McHeadFunction.id("zombie_bite"));
    public static final PacketCodec<RegistryByteBuf, ZombieBiteS2CPayload> CODEC = new PacketCodec<>() {
        public ZombieBiteS2CPayload decode(RegistryByteBuf b) {
            return new ZombieBiteS2CPayload(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readBoolean());
        }
        public void encode(RegistryByteBuf b, ZombieBiteS2CPayload p) {
            b.writeVarInt(p.attacker); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
            b.writeFloat(p.strength); b.writeFloat(p.healed); b.writeBoolean(p.killed);
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
