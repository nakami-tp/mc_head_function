package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.behead.HeadlessHandler;
import com.nakami.mcheadfunction.entity.ModEntities;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.net.ModNetworking;
import com.nakami.mcheadfunction.wear.WearHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McHeadFunction implements ModInitializer {
	public static final String MOD_ID = "mc_head_function";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		HeadItems.register();
		ModEntities.register();
		ModNetworking.register();
		WearHandler.register();
		HeadlessHandler.register();
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !HeadItems.isHead(player.getMainHandStack()));
		LOGGER.info("Mc Head Function initialized");
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
