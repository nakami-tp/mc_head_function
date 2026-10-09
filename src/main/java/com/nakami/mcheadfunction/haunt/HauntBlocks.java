package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.McHeadFunction;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class HauntBlocks {
	public static final HauntedTreeBlock TREE = new HauntedTreeBlock(AbstractBlock.Settings.create()
		.mapColor(MapColor.TERRACOTTA_BROWN).strength(2.0F, 3.0F).sounds(BlockSoundGroup.WOOD)
		.nonOpaque().solid());
	public static final BlockItem ITEM = new BlockItem(TREE, new Item.Settings());
	public static final BlockEntityType<HauntedTreeBlockEntity> TREE_ENTITY = BlockEntityType.Builder
		.create(HauntedTreeBlockEntity::new, TREE).build();

	private HauntBlocks() { }

	public static void register() {
		Registry.register(Registries.BLOCK, McHeadFunction.id("haunted_tree"), TREE);
		Registry.register(Registries.ITEM, McHeadFunction.id("haunted_tree"), ITEM);
		Registry.register(Registries.BLOCK_ENTITY_TYPE, McHeadFunction.id("haunted_tree"), TREE_ENTITY);
	}
}
