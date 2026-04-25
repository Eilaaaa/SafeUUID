package cn.alini.safeuuid.service;

import cn.alini.safeuuid.SafeUUID;
import cn.alini.safeuuid.util.UuidUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerDataLinkService {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<DataFile> DATA_FILES = List.of(
            new DataFile("playerdata", ".dat", "playerdata"),
            new DataFile("advancements", ".json", "advancements"),
            new DataFile("stats", ".json", "stats")
    );

    public LinkPlan plan(MinecraftServer server, String name, UUID premiumUuid) {
        UUID offlineUuid = UuidUtil.offlinePlayerUuid(name);
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        List<FilePlan> files = new ArrayList<>();

        for (DataFile dataFile : DATA_FILES) {
            Path offlinePath = safeKnownFile(worldRoot, dataFile, offlineUuid);
            Path premiumPath = safeKnownFile(worldRoot, dataFile, premiumUuid);
            boolean offlineExists = Files.isRegularFile(offlinePath);
            boolean premiumExists = Files.isRegularFile(premiumPath);
            LinkAction action;
            if (!offlineExists) {
                action = LinkAction.NO_SOURCE;
            } else if (premiumExists) {
                action = LinkAction.SKIP_TARGET_EXISTS;
            } else {
                action = LinkAction.MOVE;
            }

            files.add(new FilePlan(dataFile.label(), offlinePath, premiumPath, offlineExists, premiumExists, action));
        }

        return new LinkPlan(name, offlineUuid, premiumUuid, worldRoot, files);
    }

    public LinkRunResult run(MinecraftServer server, String name, UUID premiumUuid) throws IOException {
        LinkPlan plan = plan(server, name, premiumUuid);
        Path backupRoot = plan.worldRoot()
                .resolve("backups")
                .resolve("safeuuid")
                .resolve(LocalDateTime.now().format(BACKUP_TIMESTAMP))
                .toAbsolutePath()
                .normalize();
        ensureInside(plan.worldRoot(), backupRoot);

        boolean hasRelevantFiles = false;
        for (FilePlan file : plan.files()) {
            hasRelevantFiles |= file.offlineExists() || file.premiumExists();
        }

        if (hasRelevantFiles) {
            Files.createDirectories(backupRoot);
            SafeUUID.LOGGER.info("[SafeUUID] backup created at {}", backupRoot);
            for (FilePlan file : plan.files()) {
                backupIfExists(plan.worldRoot(), backupRoot, file.offlinePath());
                backupIfExists(plan.worldRoot(), backupRoot, file.premiumPath());
            }
        }

        int migrated = 0;
        int skipped = 0;
        for (FilePlan file : plan.files()) {
            if (file.action() == LinkAction.MOVE) {
                Files.createDirectories(file.premiumPath().getParent());
                Files.move(file.offlinePath(), file.premiumPath(), StandardCopyOption.ATOMIC_MOVE);
                migrated++;
                SafeUUID.LOGGER.info("[SafeUUID] migrated file {} -> {}", file.offlinePath(), file.premiumPath());
                continue;
            }

            if (file.action() == LinkAction.SKIP_TARGET_EXISTS) {
                skipped++;
                SafeUUID.LOGGER.info("[SafeUUID] skipped file because premium target exists {}", file.premiumPath());
            }
        }

        boolean hasOfflineData = plan.files().stream().anyMatch(FilePlan::offlineExists);
        if (!hasOfflineData) {
            SafeUUID.LOGGER.info("[SafeUUID] no data to migrate name={}", name);
        }

        return new LinkRunResult(plan, backupRoot, hasRelevantFiles, migrated, skipped);
    }

    private static void backupIfExists(Path worldRoot, Path backupRoot, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }

        Path relative = worldRoot.relativize(file);
        Path backupFile = backupRoot.resolve(relative).normalize();
        ensureInside(backupRoot, backupFile);
        Files.createDirectories(backupFile.getParent());
        Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static Path safeKnownFile(Path worldRoot, DataFile dataFile, UUID uuid) {
        Path directory = worldRoot.resolve(dataFile.directory()).normalize();
        Path file = directory.resolve(uuid + dataFile.extension()).normalize();
        ensureInside(directory, file);
        return file;
    }

    private static void ensureInside(Path root, Path path) {
        if (!path.normalize().startsWith(root.normalize())) {
            throw new IllegalArgumentException("Unsafe path outside world directory: " + path);
        }
    }

    private record DataFile(String directory, String extension, String label) {
    }

    public record LinkPlan(String name, UUID offlineUuid, UUID premiumUuid, Path worldRoot, List<FilePlan> files) {
        public boolean hasMigratableData() {
            return this.files.stream().anyMatch(file -> file.action() == LinkAction.MOVE);
        }
    }

    public record LinkRunResult(LinkPlan plan, Path backupRoot, boolean backupCreated, int migrated, int skipped) {
    }

    public record FilePlan(
            String label,
            Path offlinePath,
            Path premiumPath,
            boolean offlineExists,
            boolean premiumExists,
            LinkAction action
    ) {
    }

    public enum LinkAction {
        MOVE,
        SKIP_TARGET_EXISTS,
        NO_SOURCE
    }

    // TODO: merge playerdata when both offline and premium files exist.
    // TODO: merge advancements when both offline and premium files exist.
    // TODO: merge stats when both offline and premium files exist.
}
