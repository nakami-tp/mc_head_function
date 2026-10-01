package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.entity.HeadAmmoEntity;
import com.nakami.mcheadfunction.head.PlayerHeadAccess;
import com.nakami.mcheadfunction.throwing.ThrowHandler;
import com.nakami.mcheadfunction.wear.SkillHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class CreeperGameplayTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void secondPressDetonatesWithoutSpendingReserve(TestContext context) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var pos = context.getAbsolutePos(new BlockPos(2, 3, 2));
		player.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
		player.equipStack(EquipmentSlot.HEAD, Items.CREEPER_HEAD.getDefaultStack());
		SkillHandler.onSkill(player, true);
		SkillHandler.onSkill(player, false);
		var bombs = context.getWorld().getEntitiesByClass(HeadAmmoEntity.class, player.getBoundingBox().expand(5), e -> player.getUuid().equals(e.ownerId()));
		context.assertTrue(bombs.size() == 1, "First press must launch one bomb");
		SkillHandler.onSkill(player, true);
		context.assertTrue(bombs.getFirst().isRemoved(), "Second press must detonate first bomb");
		context.assertTrue(PlayerHeadAccess.state(player).creeperCharges == 1, "Detonation must preserve reserve charge");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 30)
	public void thrownCreeperBreaksBlocks(TestContext context) {
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) context.setBlockState(new BlockPos(x, 1, z), Blocks.DIRT);
		var player = context.createMockCreativeServerPlayerInWorld();
		var pos = context.getAbsolutePos(new BlockPos(2, 4, 2));
		player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 90);
		player.setStackInHand(Hand.MAIN_HAND, Items.CREEPER_HEAD.getDefaultStack());
		ThrowHandler.throwHeldHead(player);
		context.waitAndRun(8, () -> {
			context.assertTrue(context.getBlockState(new BlockPos(2, 1, 2)).isAir(), "Thrown creeper must break impact dirt");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 30)
	public void ammoStopsAtActualFloorContact(TestContext context) {
		context.setBlockState(new BlockPos(2, 1, 2), Blocks.STONE);
		var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
		var player = context.createMockCreativeServerPlayerInWorld();
		var bomb = new HeadAmmoEntity(context.getWorld(), player);
		bomb.setPosition(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
		bomb.setVelocity(0, -1.2, 0);
		context.getWorld().spawnEntity(bomb);
		context.waitAndRun(3, () -> {
			context.assertTrue(Math.abs(bomb.getY() - pos.getY()) < 0.03, "Bomb must rest flush with floor, y=" + (bomb.getY() - pos.getY()));
			context.assertTrue(bomb.getVelocity().lengthSquared() < 0.0001, "Planted bomb must not keep moving");
			bomb.discard();
			context.complete();
		});
	}
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 30)
	public void ammoAttachesToAllSixFacesAndSlab(TestContext context) {
		var center = context.getAbsolutePos(new BlockPos(2, 3, 2));
		context.setBlockState(new BlockPos(2, 3, 2), Blocks.STONE);
		var bombs = new java.util.ArrayList<HeadAmmoEntity>();
		for (var face : net.minecraft.util.math.Direction.values()) {
			var normal = Vec3d.of(face.getVector());
			var bomb = new HeadAmmoEntity(context.getWorld(), context.createMockCreativeServerPlayerInWorld());
			bomb.setPosition(Vec3d.ofCenter(center).add(normal.multiply(0.7)));
			bomb.setVelocity(normal.multiply(-1.2));
			context.getWorld().spawnEntity(bomb);
			bombs.add(bomb);
		}
		context.setBlockState(new BlockPos(5, 1, 2), Blocks.STONE_SLAB);
		var slab = new HeadAmmoEntity(context.getWorld(), context.createMockCreativeServerPlayerInWorld());
		var slabPos = context.getAbsolutePos(new BlockPos(5, 1, 2));
		slab.setPosition(slabPos.getX() + 0.5, slabPos.getY() + 0.65, slabPos.getZ() + 0.5);
		slab.setVelocity(0, -1.2, 0);
		context.getWorld().spawnEntity(slab);
		context.waitAndRun(5, () -> {
			for (int n = 0; n < bombs.size(); n++) {
				var bomb = bombs.get(n);
				var face = net.minecraft.util.math.Direction.values()[n];
				context.assertTrue(bomb.isPlanted() && bomb.attachedFace() == face, "Wrong attachment face: " + face);
				context.assertFalse(bomb.getBoundingBox().intersects(new net.minecraft.util.math.Box(center)), "Bomb penetrates " + face);
				var nbt = new net.minecraft.nbt.NbtCompound();
				bomb.writeNbt(nbt);
				var restored = new HeadAmmoEntity(com.nakami.mcheadfunction.entity.ModEntities.HEAD_AMMO, context.getWorld());
				restored.readNbt(nbt);
				context.assertTrue(restored.isPlanted() && restored.attachedFace() == face, "Attachment must survive reload");
				bomb.discard();
			}
			context.assertTrue(Math.abs(slab.getY() - slabPos.getY() - 0.5) < 0.03, "Bomb must attach to slab collision surface");
			slab.discard();
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void ownerLaunchHasSingleReducedImpulseAndOneLandingProtection(TestContext context) {
		var world = context.getWorld();
		var data = net.minecraft.server.network.ConnectedClientData.createDefault(
			new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "creeper-landing"), false);
		var player = new net.minecraft.server.network.ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		var connection = new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
		// Join protection must expire before proving survival damage and immunity.
		context.waitAndRun(65, () -> {
			var origin = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 4, 2)));
			player.setPosition(origin);
			player.setVelocity(Vec3d.ZERO);
			var bomb = new HeadAmmoEntity(context.getWorld(), player);
			bomb.setPosition(origin.add(-1, 0, 0));
			context.getWorld().spawnEntity(bomb);
			float health = player.getHealth();
			bomb.detonate();
			context.assertTrue(player.getHealth() == health, "Owner must not take explosion damage");
			context.assertTrue(player.getVelocity().distanceTo(new Vec3d(0.8, 0.275, 0)) < 0.001, "Owner must receive only the halved impulse");
			player.equipStack(EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
			player.handleFallDamage(12, 1, player.getDamageSources().fall());
			context.assertTrue(player.getHealth() == health, "First landing must be protected even without helmet");
			context.assertFalse(PlayerHeadAccess.state(player).creeperFallProtected, "Landing must consume protection");
			player.clearCurrentExplosion();
			player.handleFallDamage(12, 1, player.getDamageSources().fall());
			context.assertTrue(player.getHealth() < health, "Subsequent unrelated falls must damage player");
			context.complete();
		});
	}

}
