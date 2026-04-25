package cn.alini.safeuuid.auth;

import cn.alini.safeuuid.SafeUUID;
import cn.alini.safeuuid.config.SafeUuidConfig;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RecentIpGraceRegistry {
    private static final Map<String, GraceRecord> RECORDS = new ConcurrentHashMap<>();

    private RecentIpGraceRegistry() {
    }

    public static void recordSuccess(String name, InetAddress address) {
        if (!SafeUuidConfig.AUTH.recentIpGraceEnabled() || name == null || name.isBlank() || address == null) {
            return;
        }

        String ip = address.getHostAddress();
        RECORDS.put(normalize(name), new GraceRecord(ip, System.currentTimeMillis()));
        SafeUUID.debugLog("[SafeUUID] recentIpGrace record success name={} ip={}", name, ip);
    }

    public static boolean hasGrace(String name, InetAddress address) {
        if (!SafeUuidConfig.AUTH.recentIpGraceEnabled() || name == null || name.isBlank()) {
            return false;
        }

        String normalizedName = normalize(name);
        String ip = address == null ? "null" : address.getHostAddress();
        if (address == null) {
            SafeUUID.debugLog("[SafeUUID] recentIpGrace miss name={} ip={}", name, ip);
            return false;
        }

        GraceRecord record = RECORDS.get(normalizedName);
        if (record == null || !record.address().equals(ip)) {
            SafeUUID.debugLog("[SafeUUID] recentIpGrace miss name={} ip={}", name, ip);
            return false;
        }

        long ttlMillis = SafeUuidConfig.AUTH.recentIpGraceTtlSeconds() * 1000L;
        if (ttlMillis <= 0 || System.currentTimeMillis() - record.createdAtMillis() > ttlMillis) {
            RECORDS.remove(normalizedName, record);
            SafeUUID.LOGGER.info("[SafeUUID] recentIpGrace expired name={} ip={}", name, ip);
            return false;
        }

        SafeUUID.LOGGER.info("[SafeUUID] recentIpGrace hit name={} ip={}", name, ip);
        return true;
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private record GraceRecord(String address, long createdAtMillis) {
    }
}
