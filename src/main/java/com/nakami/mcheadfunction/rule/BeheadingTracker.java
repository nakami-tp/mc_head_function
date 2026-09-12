package com.nakami.mcheadfunction.rule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Three empty-hand shakes on the same target inside a sliding time window.
 */
public final class BeheadingTracker {
	private final Map<Key, Window> windows = new HashMap<>();

	public boolean registerHit(UUID playerId, UUID targetId, long nowMs) {
		Key key = new Key(playerId, targetId);
		Window window = windows.get(key);
		if (window == null || nowMs - window.firstHitMs > HeadRules.SHAKE_WINDOW_MS) {
			windows.put(key, new Window(nowMs, 1));
			return false;
		}
		window.hits++;
		if (window.hits >= HeadRules.SHAKE_HITS) {
			windows.remove(key);
			return true;
		}
		return false;
	}

	public void clear(UUID playerId, UUID targetId) {
		windows.remove(new Key(playerId, targetId));
	}

	private record Key(UUID playerId, UUID targetId) {
	}

	private static final class Window {
		final long firstHitMs;
		int hits;

		Window(long firstHitMs, int hits) {
			this.firstHitMs = firstHitMs;
			this.hits = hits;
		}
	}
}
