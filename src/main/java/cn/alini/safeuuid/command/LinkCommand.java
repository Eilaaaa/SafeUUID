package cn.alini.safeuuid.command;

import cn.alini.safeuuid.SafeUUID;
import cn.alini.safeuuid.auth.PremiumNameRegistry;
import cn.alini.safeuuid.service.PlayerDataLinkService;
import cn.alini.safeuuid.service.PlayerDataLinkService.FilePlan;
import cn.alini.safeuuid.service.PlayerDataLinkService.LinkPlan;
import cn.alini.safeuuid.service.PlayerDataLinkService.LinkRunResult;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.UUID;

public final class LinkCommand {
    private static final PlayerDataLinkService LINK_SERVICE = new PlayerDataLinkService();

    private LinkCommand() {
    }

    public static void appendToRoot(com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("link")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dryrun")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> dryRun(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))
                .then(Commands.literal("run")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> run(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                )))));
    }

    private static int dryRun(CommandSourceStack source, String name) {
        SafeUUID.LOGGER.info("[SafeUUID] link dryrun name={}", name);
        UUID premiumUuid = findPremiumUuidOrReport(source, name);
        if (premiumUuid == null) {
            return 0;
        }

        LinkPlan plan = LINK_SERVICE.plan(source.getServer(), name, premiumUuid);
        logUuids(plan);
        reportPlan(source, plan, false);
        return plan.hasMigratableData() ? 1 : 0;
    }

    private static int run(CommandSourceStack source, String name) {
        SafeUUID.LOGGER.info("[SafeUUID] link run name={}", name);
        UUID premiumUuid = findPremiumUuidOrReport(source, name);
        if (premiumUuid == null) {
            return 0;
        }

        LinkPlan plan = LINK_SERVICE.plan(source.getServer(), name, premiumUuid);
        logUuids(plan);
        if (plan.offlineUuid().equals(plan.premiumUuid())) {
            source.sendFailure(Component.literal("offline UUID 与 premium UUID 相同，无需迁移"));
            return 0;
        }

        try {
            LinkRunResult result = LINK_SERVICE.run(source.getServer(), name, premiumUuid);
            reportPlan(source, result.plan(), true);
            if (result.backupCreated()) {
                source.sendSuccess(() -> Component.literal("备份目录: " + result.backupRoot()), true);
            }
            source.sendSuccess(() -> Component.literal("迁移完成: moved=" + result.migrated() + ", skipped=" + result.skipped()), true);
            return result.migrated() > 0 ? 1 : 0;
        } catch (IOException | RuntimeException exception) {
            SafeUUID.LOGGER.error("[SafeUUID] link run failed name={}", name, exception);
            source.sendFailure(Component.literal("迁移失败: " + exception.getMessage()));
            return 0;
        }
    }

    private static UUID findPremiumUuidOrReport(CommandSourceStack source, String name) {
        UUID premiumUuid = PremiumNameRegistry.findPremiumUuid(name).orElse(null);
        if (premiumUuid == null) {
            source.sendFailure(Component.literal("注册表中找不到该名字的正版 UUID: " + name));
        }
        return premiumUuid;
    }

    private static void reportPlan(CommandSourceStack source, LinkPlan plan, boolean afterRun) {
        if (plan.offlineUuid().equals(plan.premiumUuid())) {
            source.sendFailure(Component.literal("offline UUID 与 premium UUID 相同，无需迁移"));
            return;
        }

        source.sendSuccess(() -> Component.literal("SafeUUID link " + (afterRun ? "run" : "dryrun") + ": " + plan.name()), false);
        source.sendSuccess(() -> Component.literal("offline UUID: " + plan.offlineUuid()), false);
        source.sendSuccess(() -> Component.literal("premium UUID: " + plan.premiumUuid()), false);

        boolean anyOffline = false;
        for (FilePlan file : plan.files()) {
            anyOffline |= file.offlineExists();
            source.sendSuccess(() -> Component.literal(formatFilePlan(file, afterRun)), false);
        }

        if (!anyOffline) {
            source.sendSuccess(() -> Component.literal("无可迁移数据"), false);
        }
    }

    private static String formatFilePlan(FilePlan file, boolean afterRun) {
        String action = switch (file.action()) {
            case MOVE -> afterRun ? "已移动" : "run 时移动";
            case SKIP_TARGET_EXISTS -> "跳过，因正版文件已存在";
            case NO_SOURCE -> "无可迁移数据";
        };
        return file.label()
                + ": offlineExists=" + file.offlineExists()
                + ", premiumExists=" + file.premiumExists()
                + ", action=" + action;
    }

    private static void logUuids(LinkPlan plan) {
        SafeUUID.debugLog("[SafeUUID] link offlineUuid={}", plan.offlineUuid());
        SafeUUID.debugLog("[SafeUUID] link premiumUuid={}", plan.premiumUuid());
    }
}
