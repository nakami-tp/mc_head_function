package com.nakami.mcheadfunction.head;

/** Short, synchronized hit reaction; it does not stun or damage the creature. */
public interface HeadShakeAccess {
    int DURATION = 10;
    int mhf$headShakeTicks();
    void mhf$setHeadShakeTicks(int ticks);
}
