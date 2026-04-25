package cn.alini.safeuuid.auth;

import net.minecraft.network.Connection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Minimal per-connection handoff from login phase to post-join notification.
public final class LoginAuthStateHolder {
    private static final Map<Connection, LoginAuthState> STATES = new ConcurrentHashMap<>();

    private LoginAuthStateHolder() {
    }

    public static void put(Connection connection, LoginAuthState state) {
        STATES.put(connection, state);
    }

    public static LoginAuthState remove(Connection connection) {
        return STATES.remove(connection);
    }
}
