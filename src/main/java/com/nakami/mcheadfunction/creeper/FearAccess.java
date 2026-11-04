package com.nakami.mcheadfunction.creeper;

/** Server state and a tracked presentation level on the original mob. */
public interface FearAccess {
    FearState mhf$fear();
    int mhf$fearLevel();
    void mhf$setFearLevel(int level);
}
