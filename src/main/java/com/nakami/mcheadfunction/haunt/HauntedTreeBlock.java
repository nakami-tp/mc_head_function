package com.nakami.mcheadfunction.haunt;

import com.mojang.serialization.MapCodec;
import com.nakami.mcheadfunction.head.HeadItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class HauntedTreeBlock extends BlockWithEntity implements BlockEntityProvider {
	public static final MapCodec<HauntedTreeBlock> CODEC = createCodec(HauntedTreeBlock::new);
	public static final IntProperty SEGMENT = IntProperty.of("segment", 0, HauntTreeShape.TRUNK - 1);
	private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);

	public HauntedTreeBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(SEGMENT, 0));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

	@Override
	protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(SEGMENT);
	}

	@Override
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockPos pos = ctx.getBlockPos();
		var world = ctx.getWorld();
		for (int i = 0; i < HauntTreeShape.TRUNK; i++) {
			if (!world.getBlockState(pos.up(i)).canReplace(ctx)) return null;
		}
		return getDefaultState().with(SEGMENT, 0);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		for (int i = 1; i < HauntTreeShape.TRUNK; i++) {
			world.setBlockState(pos.up(i), getDefaultState().with(SEGMENT, i), Block.NOTIFY_ALL);
		}
	}

	public static BlockPos core(BlockPos pos, BlockState state) {
		return pos.down(state.get(SEGMENT));
	}

	public static @Nullable HauntedTreeBlockEntity entity(World world, BlockPos pos, BlockState state) {
		return world.getBlockEntity(core(pos, state)) instanceof HauntedTreeBlockEntity tree ? tree : null;
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
		PlayerEntity player, Hand hand, BlockHitResult hit) {
		var tree = entity(world, pos, state);
		if (tree == null || HeadItems.ofStack(stack) == null) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (world.isClient()) return ItemActionResult.SUCCESS;
		tree.hang(player, HeadItems.ofStack(stack), stack);
		return ItemActionResult.SUCCESS;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!player.isSneaking() || !player.getMainHandStack().isEmpty()) return ActionResult.PASS;
		var tree = entity(world, pos, state);
		if (tree == null) return ActionResult.PASS;
		if (!world.isClient()) tree.take(player);
		return ActionResult.SUCCESS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (DISMANTLING.get() || state.getBlock() == newState.getBlock()) {
			super.onStateReplaced(state, world, pos, newState, moved);
			return;
		}
		dismantle(world, core(pos, state), !moved);
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	public static void dismantle(World world, BlockPos core, boolean drop) {
		if (Boolean.TRUE.equals(DISMANTLING.get())) return;
		DISMANTLING.set(true);
		try {
			if (drop && world.getBlockEntity(core) instanceof HauntedTreeBlockEntity tree) tree.dropContents();
			for (int i = 0; i < HauntTreeShape.TRUNK; i++) {
				BlockPos p = core.up(i);
				if (world.getBlockState(p).isOf(HauntBlocks.TREE)) world.removeBlock(p, false);
			}
		} finally {
			DISMANTLING.set(false);
		}
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return state.get(SEGMENT) == 0 ? new HauntedTreeBlockEntity(pos, state) : null;
	}

	@Override
	public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient() || state.get(SEGMENT) != 0 ? null : validateTicker(type, HauntBlocks.TREE_ENTITY, HauntedTreeBlockEntity::tick);
	}
}
