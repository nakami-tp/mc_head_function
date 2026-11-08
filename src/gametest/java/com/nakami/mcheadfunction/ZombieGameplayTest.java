package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.wear.ZombieBlood;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;

public class ZombieGameplayTest implements FabricGameTest {
    private ServerPlayerEntity wearer(TestContext c) {
        var p = c.createMockCreativeServerPlayerInWorld();
        p.changeGameMode(GameMode.SURVIVAL);
        var spawn = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 2, 1));
        p.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        for (int i = 0; i < 80; i++) { p.tick(); p.playerTick(); } // Vanilla join invulnerability and initial attack cooldown.
        p.setHealth(p.getMaxHealth());
        p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
        c.assertTrue(ZombieBlood.active(p), "Survival fixture must have an active zombie head");
        p.getHungerManager().setFoodLevel(20);
        p.getHungerManager().setSaturationLevel(20);
        return p;
    }
    private void near(TestContext c, double actual, double expected, String label) {
        c.assertTrue(Math.abs(actual - expected) < 0.001, label + ": " + actual + " != " + expected);
    }
    private net.minecraft.entity.ItemEntity pickup(TestContext c, ServerPlayerEntity p, ItemStack stack) {
        var drop = new net.minecraft.entity.ItemEntity(c.getWorld(), p.getX(), p.getY(), p.getZ(), stack);
        drop.setPickupDelay(0);
        c.getWorld().spawnEntity(drop);
        drop.onPlayerCollision(p);
        return drop;
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pickupsPreserveFistsAndFullInventoryLeavesRemainder(TestContext c) {
        var p = wearer(c); p.setHealth(10);
        var inv = p.getInventory();
        inv.selectedSlot = 0;
        var drop = pickup(c, p, new ItemStack(Items.ROTTEN_FLESH, 3));
        c.assertTrue(drop.isRemoved() && inv.count(Items.ROTTEN_FLESH) == 3, "Pickup must enter inventory");
        c.assertTrue(ZombieBlood.unarmed(p), "Automatic pickup must preserve selected empty hand");
        for (int i = 1; i < 36; i++) inv.setStack(i, new ItemStack(Items.COBBLESTONE, 64));
        inv.setStack(1, new ItemStack(Items.ROTTEN_FLESH, 63));
        var remainder = pickup(c, p, new ItemStack(Items.ROTTEN_FLESH, 3));
        c.assertTrue(!remainder.isRemoved() && remainder.getStack().getCount() == 2,
            "Merge one item and leave the remaining two on the ground");
        var tool = new ItemStack(Items.IRON_SWORD); tool.setDamage(1);
        var sword = pickup(c, p, tool);
        c.assertTrue(!sword.isRemoved() && ZombieBlood.unarmed(p), "Damaged tools must not occupy reserved hand");
        inv.selectedSlot = 2;
        pickup(c, p, new ItemStack(Items.DIRT));
        c.assertTrue(inv.getStack(0).isOf(Items.DIRT), "Selecting an item restores ordinary pickup");
        inv.setStack(0, ItemStack.EMPTY); inv.selectedSlot = 0;
        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        pickup(c, p, new ItemStack(Items.STICK));
        c.assertTrue(p.getMainHandStack().isOf(Items.STICK), "Unequip restores ordinary pickup");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void drainTakesTenSecondsAndSurvivesEquipmentAndSave(TestContext c) {
        var p = wearer(c);
        for (int i = 0; i < 9; i++) ZombieBlood.tick(p);
        near(c, p.getHungerManager().getFoodLevel(), 20, "Must not clear food on equip");
        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        for (int i = 0; i < 40; i++) ZombieBlood.tick(p);
        var nbt = new NbtCompound();
        PlayerHeadAccess.state(p).write(nbt);
        var state = new PlayerHeadState(); state.read(nbt);
        c.assertTrue(state.zombieDrainTicks == 9, "Drain remainder must persist");
        p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
        ZombieBlood.tick(p);
        near(c, p.getHungerManager().getFoodLevel(), 19, "Re-equip must not restart drain");
        for (int i = 0; i < 189; i++) ZombieBlood.tick(p);
        near(c, p.getHungerManager().getFoodLevel(), 1, "Before exact final tick");
        ZombieBlood.tick(p);
        near(c, p.getHungerManager().getFoodLevel(), 0, "200 worn ticks exhaust food");
        near(c, p.getHungerManager().getSaturationLevel(), 0, "Saturation must also exhaust");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void halfHealthFistsScaleAndWeaponsRemoveOnlyCombatBonuses(TestContext c) {
        var p = wearer(c); p.setHealth(10);
        ZombieBlood.updateAttributes(p);
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 5.5, "Half-health damage");
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED), 12, "Half-health attack speed");
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), 0.2, "Half-health movement");
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        ZombieBlood.updateAttributes(p);
        // Tick equipment modifiers before comparing vanilla weapon stats.
        p.tick();
        double damage = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        double speed = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY); ZombieBlood.updateAttributes(p);
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), damage, "Sword gets no zombie damage");
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED), speed, "Sword gets no zombie attack speed");
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), 0.1, "Unequip clears movement");
        p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD)); p.tick();
        p.setHealth(20); ZombieBlood.updateAttributes(p);
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 1, "Full health clears damage");
        p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40);
        p.setHealth(20); ZombieBlood.updateAttributes(p);
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 5.5, "Use proportion of max health");
        p.setHealth(1); ZombieBlood.updateAttributes(p);
        near(c, p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 9.775, "Near-death damage approaches tenfold");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void actualFistHitLeechesAndWeaponHitDoesNot(TestContext c) {
        var p = wearer(c); p.setHealth(10); p.setOnGround(true);
        var target = c.spawnMob(EntityType.COW, 2, 1, 2); target.setAiDisabled(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); target.setHealth(100);
        // This body hit verifies normal unarmed combat; a separate test covers head hits.
        p.refreshPositionAndAngles(target.getX(), target.getY() - 1, target.getZ() - 2, 0, 0);
        p.attack(target);
        float lost = 100 - target.getHealth();
        near(c, lost, 5.5, "Real unarmed attack uses new damage");
        near(c, p.getHealth(), 10 + lost * 0.5, "Real hit restores actual-health lifesteal");
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        for (int i = 0; i < 25; i++) { p.tick(); p.playerTick(); }
        p.setHealth(10); target.timeUntilRegen = 0;
        p.attack(target);
        near(c, p.getHealth(), 10, "Weapon hit must not leech");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bareHandMiningScalesActualBreakingProgress(TestContext c) {
        var p = wearer(c); p.setOnGround(true);
        var pos = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 1, 2));
        var block = net.minecraft.block.Blocks.OAK_PLANKS.getDefaultState();
        c.getWorld().setBlockState(pos, block);
        p.setHealth(20);
        float baseline = p.getBlockBreakingSpeed(block);
        float normalDelta = block.calcBlockBreakingDelta(p, c.getWorld(), pos);
        p.setHealth(10);
        near(c, p.getBlockBreakingSpeed(block), baseline * 3, "Half-health mining matches attack-speed multiplier");
        near(c, block.calcBlockBreakingDelta(p, c.getWorld(), pos), normalDelta * 3, "Actual block progress must triple");
        p.setHealth(1);
        near(c, p.getBlockBreakingSpeed(block), baseline * 4.8, "Near-death hand mining");
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
        float toolSpeed = p.getBlockBreakingSpeed(block);
        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        near(c, p.getBlockBreakingSpeed(block), toolSpeed, "Held tools get no zombie mining bonus");
        p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        near(c, p.getBlockBreakingSpeed(block), baseline, "Unequip restores normal hand mining");
        p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD));
        var stone = net.minecraft.block.Blocks.STONE.getDefaultState();
        c.assertFalse(p.canHarvest(stone), "Speed must not bypass required harvesting tools");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lessHealthLeechesMoreForSameDamage(TestContext c) {
        var p = wearer(c);
        var target = c.spawnMob(EntityType.COW, 2, 1, 2);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
        float[] health = {18, 10, 2};
        float[][] rates = {{0.26F, 0.50F, 0.74F}, {0.37F, 0.65F, 0.93F}};
        for (int mastery = 0; mastery < 2; mastery++) {
            PlayerHeadAccess.state(p).mastery.put(HeadType.ZOMBIE, mastery == 0 ? 0 : 1000);
            for (int i = 0; i < health.length; i++) {
                p.setHealth(health[i]); target.setHealth(100); target.timeUntilRegen = 0;
                target.damage(c.getWorld().getDamageSources().playerAttack(p), 2);
                near(c, p.getHealth(), health[i] + 2 * rates[mastery][i], "Health-dependent real lifesteal");
            }
        }
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bareHandHeadHitsDamageAndLeechWithoutBeheading(TestContext c) {
        var p = wearer(c); p.setOnGround(true);
        var target = c.spawnMob(EntityType.ZOMBIE, 2, 1, 2); target.setAiDisabled(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100); target.setHealth(100);
        p.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ() - 1.5, 0, 0);
        c.assertTrue(com.nakami.mcheadfunction.behead.BeheadingHandler.aimingAtHead(p, target), "Fixture must hit head region");
        for (int i = 0; i < 3; i++) {
            target.timeUntilRegen = 0; p.setHealth(10);
            float before = target.getHealth();
            p.attack(target);
            c.assertTrue(target.getHealth() < before && p.getHealth() > 10, "Each head punch must damage and leech");
        }
        c.assertFalse(HeadlessAccess.isHeadless(target), "Empty-hand hits must never behead");
        c.assertTrue(((HeadShakeAccess) target).mhf$headShakeTicks() == 0, "Empty-hand hits must not shake head");
        c.assertTrue(c.getWorld().getEntitiesByClass(com.nakami.mcheadfunction.entity.ThrownHeadEntity.class,
            target.getBoundingBox().expand(5), e -> true).isEmpty(), "No detached head from fists");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void masteryKillAndFullHealthHealingAreCapped(TestContext c) {
        var p = wearer(c); p.setHealth(10);
        PlayerHeadAccess.state(p).mastery.put(HeadType.ZOMBIE, 1000);
        var target = c.spawnMob(EntityType.COW, 2, 1, 2);
        target.setHealth(1);
        target.damage(c.getWorld().getDamageSources().playerAttack(p), 100);
        near(c, p.getHealth(), 16.65, "Mastered kill heals actual one damage plus six");
        var next = c.spawnMob(EntityType.COW, 3, 1, 2);
        p.setHealth(20);
        next.damage(c.getWorld().getDamageSources().playerAttack(p), 1);
        near(c, p.getHealth(), 20, "Full health is capped");
        p.setHealth(10);
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        var weaponKill = c.spawnMob(EntityType.COW, 4, 1, 2); weaponKill.setHealth(1);
        weaponKill.damage(c.getWorld().getDamageSources().playerAttack(p), 5);
        near(c, p.getHealth(), 10, "Holding any item must disable kill healing too");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fleshProtectsForFiftySecondsAndMilkEndsProtection(TestContext c) {
        var p = wearer(c); p.setHealth(1);
        new ItemStack(Items.ROTTEN_FLESH).finishUsing(c.getWorld(), p);
        near(c, p.getStatusEffect(StatusEffects.STRENGTH).getDuration(), 1000, "Strength lasts five times longer");
        near(c, p.getStatusEffect(StatusEffects.HASTE).getDuration(), 1000, "Haste lasts five times longer");
        for (var source : new net.minecraft.entity.damage.DamageSource[]{
                p.getDamageSources().generic(), p.getDamageSources().starve(), p.getDamageSources().fall(),
                p.getDamageSources().lava(), p.getDamageSources().outOfWorld()}) {
            c.assertFalse(p.damage(source, 100), "Flesh frenzy must block " + source.getName());
            near(c, p.getHealth(), 1, "Protection keeps low health");
        }
        for (int i = 0; i < 999; i++) { p.tick(); p.playerTick(); }
        c.assertFalse(p.damage(p.getDamageSources().generic(), 1), "Protection lasts through penultimate tick");
        p.tick(); p.playerTick();
        c.assertTrue(p.damage(p.getDamageSources().generic(), 1), "Damage returns at expiry");
        c.assertFalse(p.isAlive(), "Low health is vulnerable again at expiry");
        var milk = wearer(c); milk.setHealth(4);
        new ItemStack(Items.ROTTEN_FLESH).finishUsing(c.getWorld(), milk);
        new ItemStack(Items.MILK_BUCKET).finishUsing(c.getWorld(), milk);
        c.assertTrue(milk.damage(milk.getDamageSources().generic(), 1), "Milk clears flesh protection");
        var potion = wearer(c);
        potion.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 1000));
        potion.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 1000));
        c.assertTrue(potion.damage(potion.getDamageSources().generic(), 1), "Ordinary potion buffs must not protect");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fleshFistsJuggleAtHighFrequencyWithoutHealing(TestContext c) {
        var p = wearer(c); p.setHealth(1); p.setOnGround(true);
        new ItemStack(Items.ROTTEN_FLESH).finishUsing(c.getWorld(), p);
        for (int z = 1; z <= 14; z++) {
            c.setBlockState(new net.minecraft.util.math.BlockPos(2, 1, z), net.minecraft.block.Blocks.STONE);
            for (int y = 2; y <= 5; y++) c.setBlockState(new net.minecraft.util.math.BlockPos(2, y, z), net.minecraft.block.Blocks.AIR);
        }
        var target = c.spawnMob(EntityType.HUSK, 2, 2, 2);
        target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
        target.setOnGround(true);
        double floor = target.getY(), start = target.getZ(), peak = floor;
        for (int hit = 0; hit < 15; hit++) {
            p.refreshPositionAndAngles(target.getX(), floor, target.getZ() - 1.8, 0, 0);
            p.setOnGround(true);
            float before = target.getHealth();
            p.attack(target);
            c.assertTrue(target.getHealth() < before, "Every cooled fist must hurt, including during vanilla immunity: " + hit);
            near(c, p.getHealth(), 1, "Frenzy does not leech");
            c.assertTrue(target.getVelocity().z > 0 && target.getVelocity().horizontalLength() <= 0.3,
                "Fists push forward at a bounded speed");
            for (int t = 0; t < 2; t++) {
                p.tick(); p.playerTick(); target.tick(); peak = Math.max(peak, target.getY());
            }
        }
        c.assertTrue(target.getZ() > start + 2, "Repeated hits carry the mob forward: " + (target.getZ() - start));
        c.assertTrue(peak > floor + 0.1 && peak < floor + 0.9, "Repeated hits bounce low, peak=" + (peak - floor));
        target.setHealth(1); p.attack(target);
        near(c, p.getHealth(), 1, "Frenzy kills must not heal either");
        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        c.assertTrue(p.damage(p.getDamageSources().generic(), 1), "Unequip immediately ends protection");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fleshRapidDamageDoesNotLeakToWeaponsOrOtherAttackers(TestContext c) {
        var p = wearer(c); p.setHealth(4);
        new ItemStack(Items.ROTTEN_FLESH).finishUsing(c.getWorld(), p);
        var target = c.spawnMob(EntityType.HUSK, 2, 2, 2); target.setAiDisabled(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
        p.attack(target);
        float afterFist = target.getHealth();
        var other = wearer(c);
        c.assertFalse(target.damage(target.getDamageSources().playerAttack(other), 1), "Other attackers retain vanilla immunity");
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        p.attack(target);
        near(c, target.getHealth(), afterFist, "Weapon cannot bypass victim immunity");
        c.assertFalse(p.damage(p.getDamageSources().generic(), 10), "Holding an item keeps frenzy protection");
        p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        target.setInvulnerable(true); target.timeUntilRegen = 17;
        p.attack(target);
        near(c, target.timeUntilRegen, 17, "Rejected frenzy hit preserves previous immunity");
        near(c, target.getHealth(), afterFist, "Invulnerable target cannot be damaged");
        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        c.assertTrue(p.damage(p.getDamageSources().generic(), 1), "Unequip ends protection immediately");
        p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ZOMBIE_HEAD)); p.timeUntilRegen = 0;
        c.assertFalse(p.damage(p.getDamageSources().generic(), 10), "Re-equip uses remaining frenzy");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rottenFleshHasBuffsButNoNutritionOrHealing(TestContext c) {
        var p = wearer(c); p.setHealth(10);
        p.getHungerManager().setFoodLevel(8); p.getHungerManager().setSaturationLevel(3);
        new ItemStack(Items.ROTTEN_FLESH).finishUsing(c.getWorld(), p);
        near(c, p.getHealth(), 10, "Flesh must not heal");
        near(c, p.getHungerManager().getFoodLevel(), 8, "Flesh must not feed");
        near(c, p.getHungerManager().getSaturationLevel(), 3, "Flesh must not saturate");
        c.assertTrue(p.hasStatusEffect(StatusEffects.STRENGTH) && p.hasStatusEffect(StatusEffects.HASTE), "Flesh buffs preserved");
        c.assertFalse(p.hasStatusEffect(StatusEffects.HUNGER), "Flesh hunger filtered");
        new ItemStack(Items.BREAD).finishUsing(c.getWorld(), p);
        near(c, p.getHungerManager().getFoodLevel(), 8, "Bread must not feed wearer");
        c.assertTrue(p.hasStatusEffect(StatusEffects.POISON), "Bread poisons wearer");
        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        new ItemStack(Items.BREAD).finishUsing(c.getWorld(), p);
        near(c, p.getHungerManager().getFoodLevel(), 13, "Bread feeds after unequip");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placedCakeCannotBypassZombieDiet(TestContext c) {
        var p = wearer(c); p.getHungerManager().setFoodLevel(8); p.getHungerManager().setSaturationLevel(0);
        var pos = new net.minecraft.util.math.BlockPos(2, 1, 2);
        c.setBlockState(pos, net.minecraft.block.Blocks.CAKE);
        c.useBlock(pos, p);
        near(c, p.getHungerManager().getFoodLevel(), 8, "Cake must not feed wearer");
        c.assertTrue(p.hasStatusEffect(StatusEffects.POISON), "Cake counts as ordinary food");
        c.assertTrue(c.getBlockState(pos).get(net.minecraft.block.CakeBlock.BITES) == 1, "Cake slice must be consumed");
        p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY); c.useBlock(pos, p);
        near(c, p.getHungerManager().getFoodLevel(), 10, "Cake feeds after unequip");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void playerVictimLeechesOnlyActualHealthNotAbsorption(TestContext c) {
        var attacker = wearer(c); attacker.setHealth(10);
        var victim = c.createMockPlayer(GameMode.SURVIVAL);
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0));
        near(c, victim.getAbsorptionAmount(), 4, "Absorption fixture");
        victim.damage(c.getWorld().getDamageSources().playerAttack(attacker), 4);
        near(c, attacker.getHealth(), 10, "Absorbed damage must not heal");
        victim.timeUntilRegen = 0;
        victim.damage(c.getWorld().getDamageSources().playerAttack(attacker), 4);
        near(c, attacker.getHealth(), 12, "Player victim real health loss must heal");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeAndSpectatorHaveNoDrainOrAttributes(TestContext c) {
        var p = wearer(c); p.setHealth(1);
        for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            p.changeGameMode(mode);
            for (int i = 0; i < 200; i++) ZombieBlood.tick(p);
            near(c, p.getHungerManager().getFoodLevel(), 20, "Special mode retains food");
            near(c, p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 1, "Special mode has no zombie damage");
        }
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zombie_difficulties")
    public void vanillaStarvationKeepsDifficultyFloors(TestContext c) {
        var server = c.getWorld().getServer(); var original = c.getWorld().getDifficulty();
        try {
            for (var difficulty : new Difficulty[]{Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.PEACEFUL}) {
                server.setDifficulty(difficulty, true);
                var p = wearer(c); p.getHungerManager().setFoodLevel(0); p.getHungerManager().setSaturationLevel(0);
                p.setHealth(difficulty == Difficulty.EASY ? 10 : 1);
                float before = p.getHealth();
                for (int i = 0; i < 80; i++) p.getHungerManager().update(p);
                if (difficulty == Difficulty.HARD) c.assertTrue(!p.isAlive(), "Hard starvation can kill");
                else near(c, p.getHealth(), before, "Difficulty starvation floor " + difficulty);
            }
        } finally { server.setDifficulty(original, true); }
        c.complete();
    }
}
