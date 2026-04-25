package cn.alini.safeuuid.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class UuidUtil {
    private UuidUtil() {
    }

    public static UUID offlinePlayerUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
