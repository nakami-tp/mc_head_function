package com.nakami.mcheadfunction.head;

import com.nakami.mcheadfunction.McHeadFunction;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class HeadItems {
	private static final Map<HeadType, Item> ITEMS = new EnumMap<>(HeadType.class);
	private static final Map<Item, HeadType> BY_ITEM = new HashMap<>();

	private HeadItems() {
	}

	public static void register() {
		bindVanilla(HeadType.ZOMBIE, Items.ZOMBIE_HEAD);
		bindVanilla(HeadType.CREEPER, Items.CREEPER_HEAD);
		for (HeadType type : HeadType.values()) {
			if (type.vanillaItem) {
				continue;
			}
			Item item = Registry.register(
				Registries.ITEM,
				McHeadFunction.id(type.itemPath()),
				new HeadItem(type, HeadItem.wearableSettings())
			);
			ITEMS.put(type, item);
			BY_ITEM.put(item, type);
		}
		Registry.register(Registries.ITEM_GROUP, McHeadFunction.id("heads"), FabricItemGroup.builder()
			.displayName(Text.translatable("itemGroup.mc_head_function.heads"))
			.icon(() -> new ItemStack(Items.ZOMBIE_HEAD))
			.entries((context, entries) -> {
				for (HeadType type : HeadType.values()) {
					entries.add(item(type));
				}
			})
			.build()
		);
	}

	private static void bindVanilla(HeadType type, Item item) {
		ITEMS.put(type, item);
		BY_ITEM.put(item, type);
	}

	public static Item item(HeadType type) {
		return ITEMS.get(type);
	}

	public static HeadType of(Item item) {
		return BY_ITEM.get(item);
	}

	public static HeadType ofStack(net.minecraft.item.ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		return of(stack.getItem());
	}

	public static boolean isHead(net.minecraft.item.ItemStack stack) {
		return ofStack(stack) != null;
	}
}
