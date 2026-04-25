package cn.alini.safeuuid.auth;

import com.mojang.authlib.GameProfile;
import java.util.UUID;

// Minimal per-login auth state kept on the server login listener.
public final class LoginAuthState {
    private final int txId;
    private final String serverId;
    private final long createdAtMillis;
    private Boolean clientAuthOk;
    private boolean hasJoinedChecked;
    private boolean hasJoinedSuccess;
    private UUID premiumUuid;
    private String premiumName;
    private GameProfile premiumProfile;
    private boolean premiumProfileApplied;
    private boolean recentIpGraceApplied;

    public LoginAuthState(int txId, String serverId) {
        this.txId = txId;
        this.serverId = serverId;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public int txId() {
        return this.txId;
    }

    public String serverId() {
        return this.serverId;
    }

    public long createdAtMillis() {
        return this.createdAtMillis;
    }

    public Boolean clientAuthOk() {
        return this.clientAuthOk;
    }

    public void setClientAuthOk(boolean clientAuthOk) {
        this.clientAuthOk = clientAuthOk;
    }

    public boolean hasJoinedChecked() {
        return this.hasJoinedChecked;
    }

    public void setHasJoinedChecked(boolean hasJoinedChecked) {
        this.hasJoinedChecked = hasJoinedChecked;
    }

    public boolean hasJoinedSuccess() {
        return this.hasJoinedSuccess;
    }

    public void setHasJoinedSuccess(boolean hasJoinedSuccess) {
        this.hasJoinedSuccess = hasJoinedSuccess;
    }

    public UUID premiumUuid() {
        return this.premiumUuid;
    }

    public void setPremiumUuid(UUID premiumUuid) {
        this.premiumUuid = premiumUuid;
    }

    public String premiumName() {
        return this.premiumName;
    }

    public void setPremiumName(String premiumName) {
        this.premiumName = premiumName;
    }

    public GameProfile premiumProfile() {
        return this.premiumProfile;
    }

    public void setPremiumProfile(GameProfile premiumProfile) {
        this.premiumProfile = premiumProfile;
    }

    public boolean premiumProfileApplied() {
        return this.premiumProfileApplied;
    }

    public void setPremiumProfileApplied(boolean premiumProfileApplied) {
        this.premiumProfileApplied = premiumProfileApplied;
    }

    public boolean recentIpGraceApplied() {
        return this.recentIpGraceApplied;
    }

    public void setRecentIpGraceApplied(boolean recentIpGraceApplied) {
        this.recentIpGraceApplied = recentIpGraceApplied;
    }
}
