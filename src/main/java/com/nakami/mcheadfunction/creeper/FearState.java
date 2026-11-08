package com.nakami.mcheadfunction.creeper;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;

/** One fear episode per mob, shared by all players who frighten it. */
public final class FearState {
    public int exposure;
    public long lastHeard = -1000;
    public UUID source;
    public boolean rewarded;
    public long rewardCooldown;
    public int retaliationTicks;
    // Transient audio pacing; a reloaded frightened Enderman can cry again immediately.
    public long lastPanicSound = -1000;

    public void write(NbtCompound nbt) {
        var tag = new NbtCompound();
        tag.putInt("Exposure", exposure);
        tag.putLong("LastHeard", lastHeard);
        if (source != null) tag.putUuid("Source", source);
        tag.putBoolean("Rewarded", rewarded);
        tag.putLong("RewardCooldown", rewardCooldown);
        tag.putInt("Retaliation", retaliationTicks);
        nbt.put("mhf_fear", tag);
    }

    public void read(NbtCompound nbt) {
        var tag = nbt.getCompound("mhf_fear");
        exposure = Math.clamp(tag.getInt("Exposure"), 0, FearSystem.LEVEL_THREE);
        lastHeard = tag.contains("LastHeard") ? tag.getLong("LastHeard") : -1000;
        source = tag.containsUuid("Source") ? tag.getUuid("Source") : null;
        rewarded = tag.getBoolean("Rewarded");
        rewardCooldown = tag.getLong("RewardCooldown");
        retaliationTicks = Math.clamp(tag.getInt("Retaliation"), 0, 100);
    }
}
