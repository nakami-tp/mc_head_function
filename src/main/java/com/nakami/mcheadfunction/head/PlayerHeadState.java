package com.nakami.mcheadfunction.head;

import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;

public final class PlayerHeadState {
	public int creeperCharges = HeadRules.CREEPER_MAX_CHARGES;
	public int creeperRecharge;
	public int lightningCharge;
	public int frogCooldown;
	public int llamaCooldown;
	public int endermanDodgeCooldown;
	public int goatDashTicks;
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
		}
	}

	public void write(NbtCompound nbt) {
		NbtCompound tag = new NbtCompound();
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
