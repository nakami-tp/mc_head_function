package com.nakami.mcheadfunction.creeper;

import com.nakami.mcheadfunction.McHeadFunction;
import com.nakami.mcheadfunction.head.HeadlessAccess;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/** Fear controls existing mobs without deleting their goals or replacing their brains. */
public final class FearSystem {
    public static final double RANGE = 12;
    public static final int LEVEL_TWO = 30;
    public static final int LEVEL_THREE = 75;
    public static final int DECAY_DELAY = 30;
    public static final int RETALIATION_PAUSE = 15;
    public static final int REWARD_COOLDOWN = 1200;
    private static final Identifier SLOW = McHeadFunction.id("fear_slow");
    private FearSystem() { }

    public static int level(LivingEntity entity) {
        return entity instanceof FearAccess access ? access.mhf$fearLevel() : 0;
    }

    public static boolean retaliating(MobEntity mob) {
        return ((FearAccess) mob).mhf$fear().retaliationTicks > 0;
    }

    public static boolean pacified(LivingEntity entity) {
        return entity instanceof MobEntity mob && level(mob) > 0 && !retaliating(mob);
    }

    public static void expose(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        long now = world.getTime();
        for (var mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(RANGE),
                mob -> mob.isAlive() && !HeadlessAccess.isHeadless(mob)
                    && !(mob instanceof WitherEntity) && !(mob instanceof WardenEntity)
                    && !(mob instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity)
                    && mob.squaredDistanceTo(player) <= RANGE * RANGE)) {
            var state = ((FearAccess) mob).mhf$fear();
            if (state.retaliationTicks > 0 || state.lastHeard == now) continue;
            state.lastHeard = now;
            state.source = player.getUuid();
            state.exposure = Math.min(LEVEL_THREE, state.exposure + 1);
        }
    }

    public static void tick(MobEntity mob) {
        if (!(mob.getWorld() instanceof ServerWorld world)) return;
        var access = (FearAccess) mob;
        var state = access.mhf$fear();
        if (state.exposure == 0 && state.retaliationTicks == 0 && access.mhf$fearLevel() == 0) return;
        long now = world.getTime();
        if (!mob.isAlive() || HeadlessAccess.isHeadless(mob)) {
            state.exposure = 0;
            state.retaliationTicks = 0;
        } else if (state.retaliationTicks == 0 && now - state.lastHeard > DECAY_DELAY) {
            state.exposure = Math.max(0, state.exposure - 1);
        }
        int previousLevel = access.mhf$fearLevel();
        int level = state.exposure >= LEVEL_THREE ? 3 : state.exposure >= LEVEL_TWO ? 2 : state.exposure > 0 ? 1 : 0;
        access.mhf$setFearLevel(level);
        var speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            boolean slow = level >= 2 && state.retaliationTicks == 0;
            if (slow && !speed.hasModifier(SLOW)) speed.addTemporaryModifier(new EntityAttributeModifier(
                SLOW, -0.45, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (!slow) speed.removeModifier(SLOW);
        }
        if (level == 0) {
            if (previousLevel > 0) mob.getNavigation().stop();
            state.rewarded = false;
            state.source = null;
            return;
        }
        mob.setTarget(null);
        mob.setAttacking(false);
        if (mob instanceof CreeperEntity creeper) {
            if (level < 3 && !creeper.isIgnited()) creeper.setFuseSpeed(-1);
            if (level == 3 && state.retaliationTicks == 0) {
                state.retaliationTicks = 1;
                mob.getNavigation().stop();
            }
            if (state.retaliationTicks > 0) {
                state.retaliationTicks++;
                if (state.retaliationTicks > RETALIATION_PAUSE) creeper.ignite();
            }
        } else if (level == 3 && !state.rewarded) {
            state.rewarded = true;
            if (now >= state.rewardCooldown) {
                if (mob instanceof ChickenEntity chicken && !chicken.isBaby()) chicken.dropItem(Items.EGG);
                if (mob instanceof SheepEntity sheep && sheep.isShearable()) sheep.sheared(SoundCategory.NEUTRAL);
                if (mob instanceof VillagerEntity villager && !villager.isBaby()) villager.dropItem(Items.EMERALD);
                state.rewardCooldown = now + REWARD_COOLDOWN;
            }
        }
    }

    /** Called instead of normal AI while frightened; controllers still tick so paths actually move. */
    public static boolean control(MobEntity mob) {
        if (level(mob) == 0) return false;
        var state = ((FearAccess) mob).mhf$fear();
        var source = state.source == null ? null : mob.getWorld().getPlayerByUuid(state.source);
        if (retaliating(mob)) {
            if (source != null) {
                mob.getLookControl().lookAt(source, 90, 90);
                if (state.retaliationTicks > RETALIATION_PAUSE) mob.getNavigation().startMovingTo(source, 1.4);
            }
        } else {
            if (mob instanceof CreeperEntity creeper && !creeper.isIgnited()) creeper.setFuseSpeed(-1);
            if (source != null && mob instanceof PathAwareEntity path && (mob.age % 8 == 0 || mob.getNavigation().isIdle())) {
                var destination = FuzzyTargeting.findFrom(path, 10, 3, source.getPos());
                if (destination != null) mob.getNavigation().startMovingTo(destination.x, destination.y, destination.z, 1.35);
            } else if (source != null && !(mob instanceof PathAwareEntity) && mob.age % 8 == 0) {
                var away = mob.getPos().subtract(source.getPos()).normalize();
                var destination = mob.getPos().add(away.multiply(10)).add(0, 2, 0);
                mob.getMoveControl().moveTo(destination.x, destination.y, destination.z, 1.35);
            }
        }
        mob.getNavigation().tick();
        mob.getMoveControl().tick();
        mob.getLookControl().tick();
        mob.getJumpControl().tick();
        return true;
    }
}
