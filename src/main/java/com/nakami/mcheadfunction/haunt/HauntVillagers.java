package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.rule.HauntRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

/** Linger and convert villagers while a haunting can still reach them. */
public final class HauntVillagers {
	private HauntVillagers() { }

	public static boolean linger(LivingEntity living) {
		return living instanceof VillagerEntity villager && HauntStayOut.active(villager);
	}

	public static boolean zombieBite(DamageSource source) {
		return source.getAttacker() instanceof ZombieEntity;
	}

	public static boolean shouldConvert(VillagerEntity villager, DamageSource source) {
		return HauntRules.convertVillager(linger(villager), zombieBite(source), villager.getHealth());
	}

	public static float amount(VillagerEntity villager, float amount) {
		return HauntRules.lingerAmount(linger(villager), villager.getHealth(), amount);
	}

	public static boolean convert(VillagerEntity villager) {
		if (!(villager.getWorld() instanceof ServerWorld)) return false;
		var tags = java.util.List.copyOf(villager.getCommandTags());
		var data = villager.getVillagerData();
		int xp = villager.getExperience();
		ZombieVillagerEntity converted = villager.convertTo(net.minecraft.entity.EntityType.ZOMBIE_VILLAGER, false);
		if (converted == null) return false;
		converted.setVillagerData(data);
		converted.setXp(xp);
		converted.setPersistent();
		converted.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0, true, false));
		for (String tag : tags) converted.addCommandTag(tag);
		return true;
	}
}
