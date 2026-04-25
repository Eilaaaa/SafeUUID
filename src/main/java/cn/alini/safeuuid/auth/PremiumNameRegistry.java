package cn.alini.safeuuid.auth;

import cn.alini.safeuuid.SafeUUID;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Persistent record of player names that have successfully resolved to a premium UUID.
public final class PremiumNameRegistry {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("safeuuid-premium-names.txt");
    private static final Map<String, UUID> PREMIUM_NAMES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private PremiumNameRegistry() {
    }

    public static boolean isKnownPremiumName(String name) {
        loadIfNeeded();
        return PREMIUM_NAMES.containsKey(normalize(name));
    }

    public static Optional<UUID> findPremiumUuid(String name) {
        loadIfNeeded();
        return Optional.ofNullable(PREMIUM_NAMES.get(normalize(name)));
    }

    public static void recordPremiumName(String name, UUID uuid) {
        if (name == null || name.isBlank() || uuid == null) {
            return;
        }

        loadIfNeeded();
        String normalizedName = normalize(name);
        UUID previous = PREMIUM_NAMES.put(normalizedName, uuid);
        if (uuid.equals(previous)) {
            return;
        }

        save();
        SafeUUID.LOGGER.info("[SafeUUID] recorded premium name binding name={} uuid={}", name, uuid);
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }

        synchronized (PremiumNameRegistry.class) {
            if (loaded) {
                return;
            }

            if (Files.exists(FILE)) {
                try {
                    for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                        int separator = line.indexOf('=');
                        if (separator <= 0 || separator >= line.length() - 1) {
                            continue;
                        }

                        String name = line.substring(0, separator).trim();
                        String uuid = line.substring(separator + 1).trim();
                        if (!name.isBlank()) {
                            PREMIUM_NAMES.put(normalize(name), UUID.fromString(uuid));
                        }
                    }
                } catch (IllegalArgumentException | IOException exception) {
                    SafeUUID.LOGGER.warn("[SafeUUID] failed to load premium name bindings", exception);
                }
            }

            loaded = true;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            StringBuilder content = new StringBuilder();
            for (Map.Entry<String, UUID> entry : PREMIUM_NAMES.entrySet()) {
                content.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
            Files.writeString(
                    FILE,
                    content.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            SafeUUID.LOGGER.warn("[SafeUUID] failed to save premium name bindings", exception);
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
