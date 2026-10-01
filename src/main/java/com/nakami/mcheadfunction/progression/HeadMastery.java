package com.nakami.mcheadfunction.progression;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.rule.HeadDepthRules;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvents;

/** Progress belongs to a player and species, never a disposable item stack. */
public final class HeadMastery {
	private HeadMastery() { }
	public static void register() {
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
			PlayerHeadAccess.state(newPlayer).mastery.putAll(PlayerHeadAccess.state(oldPlayer).mastery));
	}
	public static int points(PlayerEntity player, HeadType type) {
		return PlayerHeadAccess.state(player).mastery.getOrDefault(type, 0);
	}
	public static boolean mastered(PlayerEntity player, HeadType type) {
		return points(player, type) >= HeadDepthRules.MASTERY_MAX;
	}
	public static void practice(PlayerEntity player, HeadType type) {
		if (!(player instanceof ServerPlayerEntity server) || !player.isAlive() || player.isSpectator()) return;
		var state = PlayerHeadAccess.state(player);
		long now = player.getWorld().getTime();
		long previous = state.lastPractice.getOrDefault(type, Long.MIN_VALUE / 2);
		if (now - previous < 20) return;
		state.lastPractice.put(type, now);
		int before = points(player, type);
		if (before >= HeadDepthRules.MASTERY_MAX) return;
		state.mastery.put(type, Math.min(HeadDepthRules.MASTERY_MAX, before + 5));
		challenge(server, "first_use");
		if (mastered(player, type)) {
			challenge(server, "master_" + type.itemPath());
			server.sendMessage(Text.translatable("mastery.mc_head_function.unlocked", HeadItems.item(type).getName()), false);
			HeadSounds.play(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.0F);
			boolean all = java.util.Arrays.stream(HeadType.values()).allMatch(head -> mastered(player, head));
			if (all) challenge(server, "all_heads");
		}
	}
	public static void challenge(ServerPlayerEntity player, String path) {
		var advancement = player.getServer().getAdvancementLoader().get(Identifier.of("mc_head_function", path));
		if (advancement != null) player.getAdvancementTracker().grantCriterion(advancement, "achieved");
	}
}
