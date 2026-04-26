package cn.alini.safeuuid.config;

import cn.alini.safeuuid.SafeUUID;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SafeUuidConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue DEBUG;
    public static final Auth AUTH;
    private static volatile boolean loaded;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DEBUG = builder
                .comment(
                        "是否启用 SafeUUID 调试日志。",
                        "false：只输出适合长期保留的关键日志。",
                        "true：额外输出登录握手、payload、交互状态等排错细节。"
                )
                .define("debug", false);
        AUTH = new Auth(builder);
        SPEC = builder.build();
    }

    private SafeUuidConfig() {
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        loaded = true;
        SafeUUID.LOGGER.info("[SafeUUID] config loaded");
        SafeUUID.LOGGER.info("[SafeUUID] debug={}", debug());
        SafeUUID.LOGGER.info("[SafeUUID] timeoutMs={}", AUTH.timeoutMs());
        SafeUUID.LOGGER.info("[SafeUUID] allowOfflineOnTimeout={}", AUTH.allowOfflineOnTimeout());
        SafeUUID.LOGGER.info("[SafeUUID] allowOfflineOnFailure={}", AUTH.allowOfflineOnFailure());
        SafeUUID.LOGGER.info("[SafeUUID] knownPremiumDenyOffline={}", AUTH.knownPremiumDenyOffline());
        SafeUUID.LOGGER.info("[SafeUUID] allowOfflineForUnknownOnly={}", AUTH.allowOfflineForUnknownOnly());
        SafeUUID.LOGGER.info("[SafeUUID] recentIpGrace.enabled={}", AUTH.recentIpGraceEnabled());
        SafeUUID.LOGGER.info("[SafeUUID] recentIpGrace.ttlSeconds={}", AUTH.recentIpGraceTtlSeconds());
    }

    public static boolean debug() {
        return loaded && DEBUG.get();
    }

    public static final class Auth {
        private final ModConfigSpec.IntValue timeoutMs;
        private final ModConfigSpec.BooleanValue allowOfflineOnTimeout;
        private final ModConfigSpec.BooleanValue allowOfflineOnFailure;
        private final ModConfigSpec.ConfigValue<String> timeoutKickMessage;
        private final ModConfigSpec.ConfigValue<String> offlineFallbackMessage;
        private final ModConfigSpec.ConfigValue<String> offlineShortSubtitle;
        private final ModConfigSpec.ConfigValue<String> onlineShortSubtitle;
        private final ModConfigSpec.BooleanValue knownPremiumDenyOffline;
        private final ModConfigSpec.BooleanValue allowOfflineForUnknownOnly;
        private final ModConfigSpec.BooleanValue recentIpGraceEnabled;
        private final ModConfigSpec.IntValue recentIpGraceTtlSeconds;

        private Auth(ModConfigSpec.Builder builder) {
            builder.comment(
                    "SafeUUID 账号校验配置。",
                    "这些选项控制登录阶段正版校验、离线兜底、已知正版名字保护和玩家提示文本。"
            ).push("auth");

            this.timeoutMs = builder
                    .comment(
                            "登录阶段等待客户端返回 SafeUUID 校验结果的最长时间，单位为毫秒。",
                            "超过该时间后会按 allowOfflineOnTimeout 的设置决定踢出还是允许离线兜底。",
                            "默认值 10000，即 10 秒。"
                    )
                    .defineInRange("timeoutMs", 10000, 1000, 120000);
            this.allowOfflineOnTimeout = builder
                    .comment(
                            "客户端校验超时时是否允许离线兜底进入。",
                            "false：超时后直接踢出。",
                            "true：超时后按离线兜底策略继续登录。"
                    )
                    .define("allowOfflineOnTimeout", false);
            this.allowOfflineOnFailure = builder
                    .comment(
                            "旧式总开关：账号校验失败时是否允许离线兜底。",
                            "false：除超时策略外，校验失败一律拒绝离线进入。",
                            "true：校验失败时继续交给 knownPremiumDenyOffline / allowOfflineForUnknownOnly 等策略判断。"
                    )
                    .define("allowOfflineOnFailure", true);
            this.timeoutKickMessage = builder
                    .comment("登录阶段校验超时且不允许离线兜底时，客户端看到的断开原因。")
                    .define("timeoutKickMessage", "登录超时，未完成账号校验");
            this.offlineFallbackMessage = builder
                    .comment(
                            "玩家通过离线兜底进入服务器后收到的聊天提示。",
                            "建议保留数据风险提醒：如果后续成功绑定正版 UUID，离线 UUID 下的数据可能需要迁移。"
                    )
                    .define("offlineFallbackMessage", "注意：你当前以离线模式进入服务器；如果你是正版账号，可能是网络原因导致无法成功鉴权，请重新登陆重试。继续游玩，若后续鉴权成功可能会丢失玩家数据。");
            this.offlineShortSubtitle = builder
                    .comment("玩家通过离线兜底进入时显示的短副标题，同时也会作为一条简短聊天提示发送。")
                    .define("offlineShortSubtitle", "鉴权失败：离线模式");
            this.onlineShortSubtitle = builder
                    .comment("玩家通过正版校验进入时显示的短副标题，同时也会作为一条简短聊天提示发送。")
                    .define("onlineShortSubtitle", "已通过正版校验");
            this.knownPremiumDenyOffline = builder
                    .comment(
                            "已知正版名字保护。",
                            "true：某个玩家名只要曾经成功通过正版校验，后续同名校验失败时禁止按离线身份进入。",
                            "这可以防止正版 UUID 与离线 UUID 的身份分叉。"
                    )
                    .define("knownPremiumDenyOffline", true);
            this.allowOfflineForUnknownOnly = builder
                    .comment(
                            "仅允许未知名字使用离线兜底。",
                            "true：只有从未成功绑定过正版 UUID 的玩家名，才允许在校验失败时离线进入。",
                            "通常应与 knownPremiumDenyOffline 一起保持为 true，以获得接近 TrueUUID 的策略。"
                    )
                    .define("allowOfflineForUnknownOnly", true);

            builder.comment(
                    "近期同 IP 成功容错配置。",
                    "用于处理正版玩家刚刚成功校验过，但短时间内因网络抖动导致再次校验失败的情况。"
            ).push("recentIpGrace");
            this.recentIpGraceEnabled = builder
                    .comment(
                            "是否启用近期同 IP 成功容错。",
                            "true：同名玩家在同一 IP 最近成功通过正版校验后，短时间内的校验失败可以被视为可信兜底。",
                            "false：不使用该容错记录。"
                    )
                    .define("enabled", true);
            this.recentIpGraceTtlSeconds = builder
                    .comment(
                            "近期同 IP 成功容错记录的有效时间，单位为秒。",
                            "默认 300，即 5 分钟。设置为 0 会让记录立即失效。"
                    )
                    .defineInRange("ttlSeconds", 300, 0, 86400);
            builder.pop();
            builder.pop();
        }

        public int timeoutMs() {
            return this.timeoutMs.get();
        }

        public boolean allowOfflineOnTimeout() {
            return this.allowOfflineOnTimeout.get();
        }

        public boolean allowOfflineOnFailure() {
            return this.allowOfflineOnFailure.get();
        }

        public String timeoutKickMessage() {
            return this.timeoutKickMessage.get();
        }

        public String offlineFallbackMessage() {
            return this.offlineFallbackMessage.get();
        }

        public String offlineShortSubtitle() {
            return this.offlineShortSubtitle.get();
        }

        public String onlineShortSubtitle() {
            return this.onlineShortSubtitle.get();
        }

        public boolean knownPremiumDenyOffline() {
            return this.knownPremiumDenyOffline.get();
        }

        public boolean allowOfflineForUnknownOnly() {
            return this.allowOfflineForUnknownOnly.get();
        }

        public boolean recentIpGraceEnabled() {
            return this.recentIpGraceEnabled.get();
        }

        public int recentIpGraceTtlSeconds() {
            return this.recentIpGraceTtlSeconds.get();
        }
    }
}
