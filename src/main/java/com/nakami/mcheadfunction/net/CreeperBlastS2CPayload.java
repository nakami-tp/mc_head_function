package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/** A blast survives the ammo entity's removal and reaches every nearby observer. */
public record CreeperBlastS2CPayload(Vec3d origin, int face) implements CustomPayload {
	public static final Id<CreeperBlastS2CPayload> ID = new Id<>(McHeadFunction.id("creeper_blast"));
	public static final PacketCodec<RegistryByteBuf, CreeperBlastS2CPayload> CODEC = new PacketCodec<>() {
		public CreeperBlastS2CPayload decode(RegistryByteBuf b) { return new CreeperBlastS2CPayload(b.readVec3d(), b.readVarInt()); }
		public void encode(RegistryByteBuf b, CreeperBlastS2CPayload p) { b.writeVec3d(p.origin()); b.writeVarInt(p.face()); }
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
