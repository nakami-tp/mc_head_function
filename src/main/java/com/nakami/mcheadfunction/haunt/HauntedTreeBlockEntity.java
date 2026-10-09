package com.nakami.mcheadfunction.haunt;

import com.nakami.mcheadfunction.head.HeadItems;
import com.nakami.mcheadfunction.head.HeadType;
import com.nakami.mcheadfunction.rule.HauntRules;
import com.nakami.mcheadfunction.rule.HauntRules.HangResult;
import com.nakami.mcheadfunction.rule.HauntRules.HostKind;
import com.nakami.mcheadfunction.rule.HauntRules.TakeResult;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class HauntedTreeBlockEntity extends BlockEntity implements HauntTreeShape.HeadTypeSlots {
	private final HeadType[] slots = new HeadType[HauntRules.SLOTS];
	private boolean haunting;
	private long lastNight = HauntRules.NEVER;
	private final List<Wave> waves = new ArrayList<>();
	private int age;

	public HauntedTreeBlockEntity(BlockPos pos, BlockState state) {
		super(HauntBlocks.TREE_ENTITY, pos, state);
	}

	@Override
	public void setWorld(World world) {
		super.setWorld(world);
		if (world != null) HauntIndex.add(world, pos);
	}

	@Override
	public void markRemoved() {
		if (world != null) HauntIndex.remove(world, pos);
		super.markRemoved();
	}

	public boolean haunting() { return haunting; }
	public boolean has(HeadType type) {
		for (HeadType hung : slots) if (hung == type) return true;
		return false;
	}
	public Set<HeadType> hung() {
		var set = EnumSet.noneOf(HeadType.class);
		for (HeadType hung : slots) if (hung != null) set.add(hung);
		return set;
	}
	public int hungCount() { return hung().size(); }
	public HeadType slot(int i) { return slots[i]; }
	@Override public boolean occupied(int slot) { return slots[slot] != null; }

	public void hang(PlayerEntity player, HeadType type, ItemStack stack) {
		HangResult result = HauntRules.canHang(hung(), type, haunting);
		if (result != HangResult.OK) {
			player.sendMessage(Text.translatable("haunt.mc_head_function." + result.name().toLowerCase()), true);
			return;
		}
		int index = HauntRules.nextSlot(slots);
		if (index < 0) return;
		slots[index] = type;
		if (!player.isCreative()) stack.decrement(1);
		sync();
		if (world instanceof ServerWorld server && HauntRules.shouldStart(HauntRules.isFull(hung()), haunting, server.getTimeOfDay(), lastNight)) {
			start(false);
		}
	}

	public void take(PlayerEntity player) {
		TakeResult result = HauntRules.canTake(hungCount(), haunting);
		if (result != TakeResult.OK) {
			player.sendMessage(Text.translatable("haunt.mc_head_function.take_" + result.name().toLowerCase()), true);
			return;
		}
		int index = HauntTreeShape.nearestHung(this, player.getEyePos(), player.getRotationVec(1), pos);
		if (index < 0) {
			for (int i = 0; i < slots.length; i++) if (slots[i] != null) { index = i; break; }
		}
		if (index < 0) return;
		ItemStack stack = new ItemStack(HeadItems.item(slots[index]));
		slots[index] = null;
		if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
		sync();
	}

	public void fill(HeadType except) {
		for (HeadType type : HeadType.values()) {
			if (type == except || has(type)) continue;
			int index = HauntRules.nextSlot(slots);
			if (index >= 0) slots[index] = type;
		}
		sync();
	}

	public void start(boolean force) {
		if (!(world instanceof ServerWorld server)) return;
		if (haunting && !force) return;
		haunting = true;
		lastNight = HauntRules.night(server.getTimeOfDay());
		waves.clear();
		for (HostKind kind : HostKind.values()) {
			if (has(kind.head)) spawnWave(server, kind);
		}
		server.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.2F, 0.55F);
		server.playSound(null, pos, SoundEvents.AMBIENT_CAVE.value(), SoundCategory.AMBIENT, 0.8F, 0.6F);
		sync();
	}

	public void stop() {
		if (!haunting || !(world instanceof ServerWorld server)) return;
		haunting = false;
		waves.clear();
		for (GhostHeadEntity ghost : ghosts(server)) ghost.releaseAndReturn();
		sync();
	}

	public static void tick(World world, BlockPos pos, BlockState state, HauntedTreeBlockEntity tree) {
		if (!(world instanceof ServerWorld server)) return;
		tree.age++;
		long time = server.getTimeOfDay();
		if (HauntRules.shouldEnd(tree.haunting, time)) tree.stop();
		else if (HauntRules.shouldStart(HauntRules.isFull(tree.hung()), tree.haunting, time, tree.lastNight)) tree.start(false);
		if (!tree.haunting) return;
		tree.waves.removeIf(wave -> {
			if (wave.delay-- > 0) return false;
			tree.spawnWave(server, wave.kind);
			return true;
		});
	}

	private void spawnWave(ServerWorld world, HostKind kind) {
		int want = HauntWorldData.get(world).count(kind);
		var targets = HauntTargets.pick(world, pos, kind, want);
		if (targets.isEmpty()) return;
		Vec3d gather = HauntTargets.gather(pos, targets);
		int hang = hangSlot(kind.head);
		for (HauntTargets.Target target : targets) {
			var ghost = new GhostHeadEntity(world, this, hang, kind, gather, target);
			world.spawnEntity(ghost);
		}
	}

	private int hangSlot(HeadType type) {
		for (int i = 0; i < slots.length; i++) if (slots[i] == type) return i;
		return 0;
	}

	private List<GhostHeadEntity> ghosts(ServerWorld world) {
		return world.getEntitiesByClass(GhostHeadEntity.class, new Box(pos).expand(HauntTreeShape.RANGE, HauntTreeShape.RANGE_Y + 8, HauntTreeShape.RANGE),
			ghost -> ghost.treePos().equals(pos));
	}

	public void dropContents() {
		if (!(world instanceof ServerWorld server)) return;
		BlockPos drop = pos;
		server.spawnEntity(new ItemEntity(server, drop.getX() + 0.5, drop.getY() + 1, drop.getZ() + 0.5, new ItemStack(HauntBlocks.ITEM)));
		for (HeadType type : slots) {
			if (type == null) continue;
			server.spawnEntity(new ItemEntity(server, drop.getX() + 0.5, drop.getY() + 1, drop.getZ() + 0.5, new ItemStack(HeadItems.item(type))));
		}
	}

	private void sync() {
		markDirty();
		if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		for (int i = 0; i < slots.length; i++) if (slots[i] != null) nbt.putString("s" + i, slots[i].name());
		nbt.putBoolean("haunting", haunting);
		nbt.putLong("lastNight", lastNight);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		java.util.Arrays.fill(slots, null);
		for (int i = 0; i < slots.length; i++) {
			if (nbt.contains("s" + i)) slots[i] = HeadType.valueOf(nbt.getString("s" + i));
		}
		haunting = nbt.getBoolean("haunting");
		lastNight = nbt.contains("lastNight") ? nbt.getLong("lastNight") : HauntRules.NEVER;
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	@Override
	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	public static HauntedTreeBlockEntity nearest(ServerWorld world, net.minecraft.util.math.Vec3d at) {
		HauntedTreeBlockEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos pos : HauntIndex.trees(world)) {
			if (world.getBlockEntity(pos) instanceof HauntedTreeBlockEntity tree) {
				double d = pos.getSquaredDistance(at.x, at.y, at.z);
				if (d < bestD) { bestD = d; best = tree; }
			}
		}
		return best;
	}

	private static final class Wave {
		final HostKind kind;
		int delay;
		Wave(HostKind kind, int delay) { this.kind = kind; this.delay = delay; }
	}
}
