package com.nakami.mcheadfunction.net;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server-authoritative HUD snapshot; sent only while a head is worn. */
public record HeadStatusS2CPayload(int charges, int recharge, int lightning, int frog, int llama, int dodge, boolean active) implements CustomPayload {
	public static final Id<HeadStatusS2CPayload> ID = new Id<>(McHeadFunction.id("head_status"));
	public static final PacketCodec<RegistryByteBuf, HeadStatusS2CPayload> CODEC = new PacketCodec<>() {
		public HeadStatusS2CPayload decode(RegistryByteBuf b) {
			return new HeadStatusS2CPayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean());
		}
		public void encode(RegistryByteBuf b, HeadStatusS2CPayload p) {
			b.writeVarInt(p.charges()); b.writeVarInt(p.recharge()); b.writeVarInt(p.lightning());
			b.writeVarInt(p.frog()); b.writeVarInt(p.llama()); b.writeVarInt(p.dodge()); b.writeBoolean(p.active());
		}
	};
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
