package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.net.ZombieBiteS2CPayload;
import com.nakami.mcheadfunction.progression.HeadMastery;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/** Worn zombie metabolism and bare-hand combat; starvation itself remains vanilla. */
public final class ZombieBlood {
    private static final Identifier DAMAGE = Identifier.of("mc_head_function", "zombie_damage");
    private static final Identifier ATTACK_SPEED = Identifier.of("mc_head_function", "zombie_attack_speed");
    private static final Identifier SPEED = Identifier.of("mc_head_function", "zombie_speed");
    public static final int DRAIN_INTERVAL = 10;
    public static final int FLESH_BUFF_TICKS = 1000;
    // A separate vanilla-managed effect prevents ordinary Strength/Haste from granting immunity.
    public static final RegistryEntry<StatusEffect> FRENZY = Registry.registerReference(Registries.STATUS_EFFECT,
        Identifier.of("mc_head_function", "zombie_frenzy"), new StatusEffect(StatusEffectCategory.BENEFICIAL, 0x537735) { });
    private ZombieBlood() { }

    public static void register() { }

    public static void eatFlesh(PlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, FLESH_BUFF_TICKS, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, FLESH_BUFF_TICKS, 0));
        player.addStatusEffect(new StatusEffectInstance(FRENZY, FLESH_BUFF_TICKS, 0, false, false, false));
    }

    public static boolean frenzied(PlayerEntity player) { return active(player) && player.hasStatusEffect(FRENZY); }
    public static boolean frenzyFists(PlayerEntity player) { return frenzied(player) && unarmed(player); }

    /** Replace vanilla/sprint knockback after the complete attack; never stack upward impulses. */
    public static void juggle(PlayerEntity player, LivingEntity victim) {
        if (!victim.isAlive() || HeadLookups.isBoss(victim)) return;
        double scale = 1 - victim.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (scale <= 0) return;
        var direction = player.getRotationVec(1).multiply(1, 0, 1).normalize();
        double lift = victim.getY() < player.getY() + 0.45 ? 0.12 * scale : Math.min(0, victim.getVelocity().y);
        victim.setVelocity(direction.x * 0.24 * scale, lift, direction.z * 0.24 * scale);
        victim.velocityModified = true;
    }

    public static boolean active(PlayerEntity player) {
        boolean survival = player instanceof ServerPlayerEntity server
            ? server.interactionManager.getGameMode() == net.minecraft.world.GameMode.SURVIVAL
                || server.interactionManager.getGameMode() == net.minecraft.world.GameMode.ADVENTURE
            : !player.isCreative() && !player.isSpectator();
        return player.isAlive() && survival && HeadLookups.worn(player) == HeadType.ZOMBIE;
    }
    public static boolean unarmed(PlayerEntity player) { return active(player) && player.getMainHandStack().isEmpty(); }
    public static float strength(PlayerEntity player) {
        return active(player) ? Math.clamp(1 - player.getHealth() / Math.max(1, player.getMaxHealth()), 0, 1) : 0;
    }
    public static float handSpeedMultiplier(PlayerEntity player) {
        return unarmed(player) ? 1 + 4 * strength(player) : 1;
    }
    public static void tick(PlayerEntity player) {
        updateAttributes(player);
        if (player.getWorld().isClient() || !active(player)) return;
        var state = PlayerHeadAccess.state(player);
        if (++state.zombieDrainTicks >= DRAIN_INTERVAL) {
            state.zombieDrainTicks = 0;
            var hunger = player.getHungerManager();
            hunger.setFoodLevel(Math.max(0, hunger.getFoodLevel() - 1));
            hunger.setSaturationLevel(Math.max(0, hunger.getSaturationLevel() - 1));
        }
    }
    public static void updateAttributes(PlayerEntity player) {
        float strength = strength(player);
        boolean fists = unarmed(player);
        modifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE, fists ? 9 * strength : 0);
        modifier(player, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED, fists ? 4 * strength : 0);
        modifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED, 2 * strength);
    }
    private static void modifier(PlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value) {
        var instance = player.getAttributeInstance(attribute);
        if (instance == null) return;
        var old = instance.getModifier(id);
        if (old != null && old.value() == value) return;
        instance.removeModifier(id);
        if (value > 0) instance.addTemporaryModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    /** Evaluate before healing, using the same missing-health proportion as the combat bonuses. */
    public static float lifestealRate(PlayerEntity player) {
        float power = strength(player);
        return HeadMastery.mastered(player, HeadType.ZOMBIE) ? 0.30F + 0.70F * power : 0.20F + 0.60F * power;
    }
    public static void hit(ServerPlayerEntity player, LivingEntity victim, float damage, boolean killed, boolean melee) {
        if (!unarmed(player) || !melee || damage <= 0) return;
        float power = strength(player);
        float before = player.getHealth();
        if (!frenzied(player)) player.heal(damage * lifestealRate(player) + (killed ? 6 : 0));
        float healed = player.getHealth() - before;
        HeadMastery.practice(player, HeadType.ZOMBIE);
        var payload = new ZombieBiteS2CPayload(player.getId(), victim.getX(), victim.getBodyY(0.6), victim.getZ(), power, healed, killed);
        for (var observer : PlayerLookup.tracking(player)) ServerPlayNetworking.send(observer, payload);
        ServerPlayNetworking.send(player, payload);
        player.getWorld().playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_SLIME_ATTACK,
            player.getSoundCategory(), 0.18F + power * 0.10F, 0.85F + player.getRandom().nextFloat() * 0.15F);
        player.getWorld().playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
            player.getSoundCategory(), 0.3F + power * 0.25F, 0.65F + player.getRandom().nextFloat() * 0.1F);
        if (killed) player.getWorld().playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_AMBIENT,
            player.getSoundCategory(), 0.5F, 0.75F);
        updateAttributes(player);
    }
}
