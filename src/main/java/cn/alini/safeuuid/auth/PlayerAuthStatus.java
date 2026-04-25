package cn.alini.safeuuid.auth;

public enum PlayerAuthStatus {
    PREMIUM_APPLIED,
    OFFLINE_FALLBACK,
    KNOWN_PREMIUM_DENIED,
    RECENT_IP_GRACE,
    TIMEOUT_KICK,
    NOMOJANG_BYPASS,
    INTERNAL_ERROR,
    UNKNOWN
}
