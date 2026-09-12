package com.nakami.mcheadfunction.head;

public enum HeadType {
	ZOMBIE(ThrowStyle.ZOMBIE_HOP, WearStyle.ZOMBIE, true),
	ENDERMAN(ThrowStyle.ENDERMAN_EAT, WearStyle.ENDERMAN, false),
	CHARGED_CREEPER(ThrowStyle.CHARGED_PIERCE, WearStyle.CHARGED_CREEPER, false),
	CREEPER(ThrowStyle.CREEPER_BLAST, WearStyle.CREEPER, true),
	BLAZE(ThrowStyle.BLAZE_TRAIL, WearStyle.BLAZE, false),
	IRON_GOLEM(ThrowStyle.GOLEM_ROLL, WearStyle.IRON_GOLEM, false),
	GOAT(ThrowStyle.GOAT_RAM, WearStyle.GOAT, false),
	PIG(ThrowStyle.DEFAULT, WearStyle.PIG, false),
	COW(ThrowStyle.DEFAULT, WearStyle.COW, false),
	SHEEP(ThrowStyle.DEFAULT, WearStyle.SHEEP, false),
	CHICKEN(ThrowStyle.DEFAULT, WearStyle.CHICKEN, false),
	RABBIT(ThrowStyle.DEFAULT, WearStyle.RABBIT, false),
	FOX(ThrowStyle.DEFAULT, WearStyle.FOX, false),
	WOLF(ThrowStyle.DEFAULT, WearStyle.WOLF, false),
	FROG(ThrowStyle.DEFAULT, WearStyle.FROG, false),
	BEE(ThrowStyle.BEE_SUMMON, WearStyle.BEE, false),
	ARMADILLO(ThrowStyle.DEFAULT, WearStyle.ARMADILLO, false),
	LLAMA(ThrowStyle.DEFAULT, WearStyle.LLAMA, false),
	BAT(ThrowStyle.BAT_SONAR, WearStyle.NONE, false);

	public final ThrowStyle throwStyle;
	public final WearStyle wearStyle;
	public final boolean vanillaItem;

	HeadType(ThrowStyle throwStyle, WearStyle wearStyle, boolean vanillaItem) {
		this.throwStyle = throwStyle;
		this.wearStyle = wearStyle;
		this.vanillaItem = vanillaItem;
	}

	public String itemPath() {
		return switch (this) {
			case CHARGED_CREEPER -> "charged_creeper_head";
			case IRON_GOLEM -> "iron_golem_head";
			default -> name().toLowerCase() + "_head";
		};
	}

	public enum ThrowStyle {
		DEFAULT,
		ZOMBIE_HOP,
		ENDERMAN_EAT,
		CHARGED_PIERCE,
		CREEPER_BLAST,
		BLAZE_TRAIL,
		GOLEM_ROLL,
		GOAT_RAM,
		BEE_SUMMON,
		BAT_SONAR,
		KNOCK_OFF
	}

	public enum WearStyle {
		NONE,
		ZOMBIE,
		ENDERMAN,
		CHARGED_CREEPER,
		CREEPER,
		BLAZE,
		IRON_GOLEM,
		GOAT,
		PIG,
		COW,
		SHEEP,
		CHICKEN,
		RABBIT,
		FOX,
		WOLF,
		FROG,
		BEE,
		ARMADILLO,
		LLAMA
	}
}
