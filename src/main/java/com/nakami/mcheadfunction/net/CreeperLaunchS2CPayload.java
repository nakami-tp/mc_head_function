package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/** One short exhaust burst, only when the owner's bomb actually applies its impulse. */
public record CreeperLaunchS2CPayload(int player, Vec3d velocity) implements CustomPayload {
	public static final Id<CreeperLaunchS2CPayload> ID = new Id<>(McHeadFunction.id("creeper_launch"));
	public static final PacketCodec<RegistryByteBuf, CreeperLaunchS2CPayload> CODEC = new PacketCodec<>() {
		public CreeperLaunchS2CPayload decode(RegistryByteBuf b) { return new CreeperLaunchS2CPayload(b.readVarInt(), b.readVec3d()); }
		public void encode(RegistryByteBuf b, CreeperLaunchS2CPayload p) { b.writeVarInt(p.player()); b.writeVec3d(p.velocity()); }
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
