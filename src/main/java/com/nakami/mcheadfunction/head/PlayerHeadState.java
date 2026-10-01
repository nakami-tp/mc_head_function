package com.nakami.mcheadfunction.head;

import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

public final class PlayerHeadState {
	public int creeperCharges = HeadRules.CREEPER_MAX_CHARGES;
	public int creeperRecharge;
	public boolean creeperFallProtected;
	public long creeperLaunchedAt;
	public int lightningCharge;
	public int frogCooldown;
	public int llamaCooldown;
	public int endermanDodgeCooldown;
	public int goatDashTicks;
	public Vec3d goatDashOrigin;
	public final Set<UUID> goatHit = new HashSet<>();
	public final Map<UUID, Integer> wolfFleeRemaining = new HashMap<>();
	public float goatHeading;
	public double goatSpeed;
	public int goatCooldown;
	public int rabbitChain;
	public int rabbitJumpRequestTicks;
	public long rabbitLandedAt = -100;
	public boolean rabbitAirborne;
	public double rabbitLaunchY;
	public boolean rabbitFallProtected;
	public UUID beeTarget;
	public int beeTargetTicks;
	public final java.util.EnumMap<HeadType, Integer> mastery = new java.util.EnumMap<>(HeadType.class);
	public final java.util.EnumMap<HeadType, Long> lastPractice = new java.util.EnumMap<>(HeadType.class);
	public boolean skillHeld;
	public UUID markedTarget;
	public UUID lastAmmo;

	public void tick(boolean thunderstorm) {
		if (creeperCharges < HeadRules.CREEPER_MAX_CHARGES) {
			creeperRecharge++;
			if (creeperRecharge >= HeadRules.CREEPER_RECHARGE_TICKS) {
				creeperRecharge = 0;
				creeperCharges++;
			}
		}
		int needed = HeadRules.lightningChargeNeeded(thunderstorm);
		if (lightningCharge < needed) {
			lightningCharge++;
		}
		if (goatCooldown > 0) goatCooldown--;
		if (beeTargetTicks > 0) beeTargetTicks--;
		if (frogCooldown > 0) {
			frogCooldown--;
		}
		if (llamaCooldown > 0) {
			llamaCooldown--;
		}
		if (endermanDodgeCooldown > 0) {
			endermanDodgeCooldown--;
		}
		if (goatDashTicks > 0) {
			goatDashTicks--;
			if (goatDashTicks == 0) {
				clearGoatDash();
			}
		}
	}

	public void startGoatDash(Vec3d origin) {
		goatDashTicks = com.nakami.mcheadfunction.rule.HeadDepthRules.GOAT_DURATION;
		goatSpeed = 0.35;
		goatDashOrigin = origin;
		goatHit.clear();
	}

	public void clearGoatDash() {
		goatDashTicks = 0;
		goatDashOrigin = null;
		goatHit.clear();
	}

	public void write(NbtCompound nbt) {
		NbtCompound tag = new NbtCompound();
		NbtCompound progress = new NbtCompound();
		mastery.forEach((type, xp) -> progress.putInt(type.name(), xp));
		tag.put("mastery", progress);
		tag.putInt("goatCooldown", goatCooldown);
		tag.putInt("creeperCharges", creeperCharges);
		tag.putInt("creeperRecharge", creeperRecharge);
		tag.putInt("lightningCharge", lightningCharge);
		tag.putInt("frogCooldown", frogCooldown);
		tag.putInt("llamaCooldown", llamaCooldown);
		tag.putInt("endermanDodgeCooldown", endermanDodgeCooldown);
		if (markedTarget != null) {
			tag.putUuid("marked", markedTarget);
		}
		nbt.put("mhf", tag);
	}

	public void read(NbtCompound nbt) {
		if (!nbt.contains("mhf")) {
			return;
		}
		NbtCompound tag = nbt.getCompound("mhf");
		NbtCompound progress = tag.getCompound("mastery");
		mastery.clear();
		for (HeadType type : HeadType.values()) mastery.put(type, Math.clamp(progress.getInt(type.name()), 0, com.nakami.mcheadfunction.rule.HeadDepthRules.MASTERY_MAX));
		goatCooldown = tag.getInt("goatCooldown");
		creeperCharges = tag.getInt("creeperCharges");
		creeperRecharge = tag.getInt("creeperRecharge");
		lightningCharge = tag.getInt("lightningCharge");
		frogCooldown = tag.getInt("frogCooldown");
		llamaCooldown = tag.getInt("llamaCooldown");
		endermanDodgeCooldown = tag.getInt("endermanDodgeCooldown");
		if (tag.containsUuid("marked")) {
			markedTarget = tag.getUuid("marked");
		}
	}
}
