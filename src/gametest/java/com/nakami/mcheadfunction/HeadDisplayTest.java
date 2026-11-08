package com.nakami.mcheadfunction;

import com.nakami.mcheadfunction.head.HeadDisplayCommand;
import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public class HeadDisplayTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void allPlacesEveryHeadInFrontOfThePlayer(TestContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = operator(context, 0);
		var before = standIds(context);
		int placed = context.getWorld().getServer().getCommandManager().getDispatcher()
			.execute("heads", player.getCommandSource().withLevel(2));
		var stands = newStands(context, before);
		context.assertTrue(placed == HeadType.values().length, "The command must report one stand per head");
		context.assertTrue(stands.size() == HeadType.values().length, "Every head needs its own armor stand");
		var seen = EnumSet.noneOf(HeadType.class);
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		for (var stand : stands) {
			context.assertTrue(stand.getZ() > player.getZ() + 2, "Stands must sit in front of the facing direction");
			seen.add(HeadItems.ofStack(stand.getEquippedStack(EquipmentSlot.HEAD)));
			minX = Math.min(minX, stand.getX());
			maxX = Math.max(maxX, stand.getX());
		}
		context.assertTrue(seen.size() == HeadType.values().length, "The row must include each head once");
		context.assertTrue(maxX - minX > 20, "Nineteen heads must spread across the row");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void namedHeadsKeepRequestOrderAndRejectUnknownNames(TestContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = operator(context, 90);
		var dispatcher = context.getWorld().getServer().getCommandManager().getDispatcher();
		var before = standIds(context);
		String command = "heads zombie enderman creeper frog blaze rabbit goat pig";
		int placed = dispatcher.execute(command, player.getCommandSource().withLevel(2));
		var stands = newStands(context, before);
		stands.sort((a, b) -> Double.compare(b.getZ(), a.getZ()));
		HeadType[] expected = {
			HeadType.ZOMBIE, HeadType.ENDERMAN, HeadType.CREEPER, HeadType.FROG,
			HeadType.BLAZE, HeadType.RABBIT, HeadType.GOAT, HeadType.PIG
		};
		context.assertTrue(placed == expected.length && stands.size() == expected.length, "The script row must place eight stands");
		for (int i = 0; i < expected.length; i++) {
			context.assertTrue(HeadItems.ofStack(stands.get(i).getEquippedStack(EquipmentSlot.HEAD)) == expected[i],
				"Stand " + i + " must wear the requested head");
			context.assertTrue(stands.get(i).getX() < player.getX() - 2, "A player facing west must see the row ahead");
		}
		int rejected = dispatcher.execute("heads zombie not_a_head", player.getCommandSource().withLevel(2));
		context.assertTrue(rejected == 0, "An unknown head name must fail the whole command");
		context.assertTrue(newStands(context, before).size() == expected.length,
			"A rejected name must not add another stand");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aConsoleCannotPlaceStands(TestContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var before = standIds(context);
		int placed = context.getWorld().getServer().getCommandManager().getDispatcher()
			.execute("heads", context.getWorld().getServer().getCommandSource());
		context.assertTrue(placed == 0, "The console has no facing to place a row in front of");
		context.assertTrue(newStands(context, before).isEmpty(), "A console request must not spawn stands");
		context.assertTrue(HeadDisplayCommand.parse("all").size() == HeadType.values().length, "all must mean every head");
		context.assertTrue(HeadDisplayCommand.parse("pig pig").size() == 2, "Repeated names keep their requested places");
		context.assertTrue(HeadDisplayCommand.parse("nope") == null, "Unknown names do not parse");
		context.complete();
	}

	private static net.minecraft.server.network.ServerPlayerEntity operator(TestContext context, float yaw) {
		var player = context.createMockCreativeServerPlayerInWorld();
		var origin = context.getAbsolutePos(BlockPos.ORIGIN);
		player.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, yaw, 0);
		player.setYaw(yaw);
		player.setHeadYaw(yaw);
		return player;
	}

	private static HashSet<UUID> standIds(TestContext context) {
		var ids = new HashSet<UUID>();
		for (var entity : context.getWorld().iterateEntities()) {
			if (entity instanceof ArmorStandEntity) ids.add(entity.getUuid());
		}
		return ids;
	}

	private static java.util.List<ArmorStandEntity> newStands(TestContext context, HashSet<UUID> before) {
		var created = new java.util.ArrayList<ArmorStandEntity>();
		for (var entity : context.getWorld().iterateEntities()) {
			if (entity instanceof ArmorStandEntity stand && !before.contains(stand.getUuid())) created.add(stand);
		}
		return created;
	}
}
