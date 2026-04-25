package cn.alini.safeuuid.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Minimal runtime status for querying the current player's auth result.
public final class PlayerAuthStatusHolder {
    private static final Map<UUID, PlayerAuthStatus> STATES = new ConcurrentHashMap<>();

    private PlayerAuthStatusHolder() {
    }

    public static void put(UUID playerId, PlayerAuthStatus status) {
        STATES.put(playerId, status);
    }

    public static PlayerAuthStatus get(UUID playerId) {
        return STATES.getOrDefault(playerId, PlayerAuthStatus.UNKNOWN);
    }

    public static void remove(UUID playerId) {
        STATES.remove(playerId);
    }
}
