package com.nakami.mcheadfunction.wear;

import com.nakami.mcheadfunction.head.HeadLookups;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.head.PlayerHeadState;
import com.nakami.mcheadfunction.rule.HeadRules;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class WearHandler {
	private static final Identifier GOLEM_DAMAGE = Identifier.of("mc_head_function", "golem_damage");
	private static final Identifier GOLEM_KB = Identifier.of("mc_head_function", "golem_kb");
	private static final Identifier GOLEM_KB_RES = Identifier.of("mc_head_function", "golem_kb_res");
	private static final Identifier GOLEM_ARMOR = Identifier.of("mc_head_function", "golem_armor");

	private WearHandler() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(WearHandler::tickWorld);
	}

	private static void tickWorld(ServerWorld world) {
		boolean storm = world.isThundering();
		for (ServerPlayerEntity player : world.getPlayers()) {
			PlayerHeadState state = PlayerHeadAccess.state(player);
			state.tick(storm);
			HeadType worn = HeadLookups.worn(player);
			applyPassives(player, worn);
			tickGoatDash(player, state);
			tickBlazeSpray(player, worn, state);
			tickWolfThreat(world, player, worn);
			tickBees(world, player, worn);
		}
	}

	private static void applyPassives(ServerPlayerEntity player, HeadType worn) {
		clearGolem(player);
		if (worn == HeadType.CHICKEN) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 0, true, false, false));
		}
		if (worn == HeadType.RABBIT) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 1, true, false, false));
		}
		if (worn == HeadType.IRON_GOLEM) {
			addModifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, GOLEM_DAMAGE, 2.0, EntityAttributeModifier.Operation.ADD_VALUE);
			addModifier(player, EntityAttributes.GENERIC_ATTACK_KNOCKBACK, GOLEM_KB, 1.5, EntityAttributeModifier.Operation.ADD_VALUE);
			addModifier(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, GOLEM_KB_RES, 0.6, EntityAttributeModifier.Operation.ADD_VALUE);
			addModifier(player, EntityAttributes.GENERIC_ARMOR, GOLEM_ARMOR, 6.0, EntityAttributeModifier.Operation.ADD_VALUE);
		}
		if (worn == HeadType.BLAZE) {
			player.setFireTicks(0);
		}
	}

	private static void addModifier(PlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, Identifier id, double value, EntityAttributeModifier.Operation op) {
		var instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		if (instance.getModifier(id) == null) {
			instance.addPersistentModifier(new EntityAttributeModifier(id, value, op));
		}
	}

	private static void clearGolem(PlayerEntity player) {
		if (HeadLookups.worn(player) == HeadType.IRON_GOLEM) {
			return;
		}
		remove(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, GOLEM_DAMAGE);
		remove(player, EntityAttributes.GENERIC_ATTACK_KNOCKBACK, GOLEM_KB);
		remove(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, GOLEM_KB_RES);
		remove(player, EntityAttributes.GENERIC_ARMOR, GOLEM_ARMOR);
	}

	private static void remove(PlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, Identifier id) {
		var instance = player.getAttributeInstance(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private static void tickGoatDash(ServerPlayerEntity player, PlayerHeadState state) {
		if (state.goatDashTicks <= 0) {
			return;
		}
		Vec3d look = player.getRotationVec(1.0F);
		player.addVelocity(look.x * 0.35, 0, look.z * 0.35);
		player.velocityModified = true;
		Box box = player.getBoundingBox().expand(0.6);
		for (LivingEntity living : player.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
			living.addVelocity(look.x * 1.2, 0.75, look.z * 1.2);
			living.velocityModified = true;
			living.damage(player.getDamageSources().playerAttack(player), 3);
		}
	}

	private static void tickBlazeSpray(ServerPlayerEntity player, HeadType worn, PlayerHeadState state) {
		if (!state.skillHeld || worn != HeadType.BLAZE) {
			return;
		}
		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(6));
		for (LivingEntity living : player.getWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(6), e -> e != player)) {
			if (living.getBoundingBox().raycast(start, end).isPresent()) {
				living.setOnFireFor(4);
			}
		}
		var hit = player.getWorld().raycast(new net.minecraft.world.RaycastContext(start, end, net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
			var pos = hit.getBlockPos().offset(hit.getSide());
			if (player.getWorld().getBlockState(pos).isAir()) {
				player.getWorld().setBlockState(pos, net.minecraft.block.Blocks.FIRE.getDefaultState());
			}
		}
	}

	private static void tickWolfThreat(ServerWorld world, ServerPlayerEntity player, HeadType worn) {
		if (worn != HeadType.WOLF) {
			return;
		}
		Box box = player.getBoundingBox().expand(HeadRules.SONAR_RANGE);
		for (SkeletonEntity skeleton : world.getEntitiesByClass(SkeletonEntity.class, box, LivingEntity::isAlive)) {
			Vec3d away = skeleton.getPos().subtract(player.getPos()).normalize().multiply(0.35);
			skeleton.setTarget(null);
			skeleton.addVelocity(away.x, 0.05, away.z);
			skeleton.velocityModified = true;
		}
	}

	private static void tickBees(ServerWorld world, ServerPlayerEntity player, HeadType worn) {
		if (worn != HeadType.BEE) {
			return;
		}
		String tag = "mhf_owner:" + player.getUuid();
		Box box = player.getBoundingBox().expand(48);
		for (BeeEntity bee : world.getEntitiesByClass(BeeEntity.class, box, b -> b.getCommandTags().contains(tag))) {
			if (player.getAttacker() != null) {
				bee.setTarget(player.getAttacker());
			}
			if (bee.squaredDistanceTo(player) > 9) {
				bee.getNavigation().startMovingTo(player, 1.15);
			}
		}
	}

	public static void onEat(PlayerEntity player, ItemStack stack, FoodComponent food) {
		HeadType worn = HeadLookups.worn(player);
		if (worn == HeadType.ZOMBIE) {
			if (stack.isOf(Items.ROTTEN_FLESH)) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 200, 0));
				player.heal(2.0F);
			} else if (food != null) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
			}
		}
		if (worn == HeadType.PIG && food != null) {
			player.getHungerManager().add(0, 2.0F);
		}
	}

	public static void onDealtDamage(ServerPlayerEntity player, LivingEntity victim, float amount, boolean killed) {
		HeadType worn = HeadLookups.worn(player);
		if (worn == HeadType.ZOMBIE) {
			player.heal(amount * 0.10F);
			if (killed) {
				player.heal(6.0F);
			}
		}
		if (worn == HeadType.WOLF) {
			PlayerHeadAccess.state(player).markedTarget = victim.getUuid();
			victim.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0, true, false, false));
		}
	}

	public static boolean tryEndermanDodge(ServerPlayerEntity player) {
		if (HeadLookups.worn(player) != HeadType.ENDERMAN) {
			return false;
		}
		PlayerHeadState state = PlayerHeadAccess.state(player);
		if (state.endermanDodgeCooldown > 0) {
			return false;
		}
		state.endermanDodgeCooldown = HeadRules.ENDERMAN_DODGE_COOLDOWN_TICKS;
		Vec3d look = player.getRotationVec(1.0F);
		for (int i = 0; i < 8; i++) {
			double yaw = player.getRandom().nextDouble() * Math.PI * 2;
			double x = player.getX() + Math.cos(yaw) * 6;
			double z = player.getZ() + Math.sin(yaw) * 6;
			double y = player.getY();
			if (player.teleport(x, y, z, true)) {
				player.getWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT, player.getSoundCategory(), 1.0F, 1.0F);
				return true;
			}
		}
		player.teleport(player.getX() - look.x * 6, player.getY(), player.getZ() - look.z * 6, true);
		return true;
	}

	public static boolean reduceIncoming(ServerPlayerEntity player, net.minecraft.entity.damage.DamageSource source, float amount) {
		if (HeadLookups.worn(player) == HeadType.BLAZE && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) {
			return true;
		}
		return false;
	}

	public static float armadilloScale(ServerPlayerEntity player, float amount) {
		if (HeadLookups.worn(player) == HeadType.ARMADILLO && player.isSneaking()) {
			return amount * 0.6F;
		}
		return amount;
	}

	public static boolean wearingSheep(PlayerEntity player) {
		return HeadLookups.worn(player) == HeadType.SHEEP;
	}

	public static boolean wearingFox(PlayerEntity player) {
		return HeadLookups.worn(player) == HeadType.FOX;
	}

	public static StatusEffectInstance maybeHalve(PlayerEntity player, StatusEffectInstance effect) {
		if (HeadLookups.worn(player) != HeadType.COW) {
			return effect;
		}
		if (effect.getEffectType().value().getCategory() != StatusEffectCategory.HARMFUL) {
			return effect;
		}
		return new StatusEffectInstance(effect.getEffectType(), Math.max(1, effect.getDuration() / 2), effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
	}

	public static UUID marked(PlayerEntity player) {
		return PlayerHeadAccess.state(player).markedTarget;
	}
}
