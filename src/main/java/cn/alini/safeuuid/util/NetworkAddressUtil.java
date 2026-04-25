package cn.alini.safeuuid.util;

import net.minecraft.network.Connection;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class NetworkAddressUtil {
    private NetworkAddressUtil() {
    }

    @Nullable
    public static InetAddress remoteInetAddress(Connection connection) {
        SocketAddress socketAddress = connection.getRemoteAddress();
        return socketAddress instanceof InetSocketAddress inetSocketAddress ? inetSocketAddress.getAddress() : null;
    }

    public static String format(@Nullable InetAddress address) {
        return address == null ? "null" : address.getHostAddress();
    }
}
