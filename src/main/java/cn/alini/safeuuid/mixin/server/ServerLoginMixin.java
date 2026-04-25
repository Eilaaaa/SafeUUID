package cn.alini.safeuuid.mixin.server;

import cn.alini.safeuuid.SafeUUID;
import cn.alini.safeuuid.auth.LoginAuthState;
import cn.alini.safeuuid.auth.LoginAuthStateHolder;
import cn.alini.safeuuid.auth.PremiumNameRegistry;
import cn.alini.safeuuid.auth.RecentIpGraceRegistry;
import cn.alini.safeuuid.config.SafeUuidConfig;
import cn.alini.safeuuid.net.AuthAnswerPayload;
import cn.alini.safeuuid.net.AuthPayload;
import cn.alini.safeuuid.net.NetIds;
import cn.alini.safeuuid.util.NetworkAddressUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginMixin {
    @Shadow
    private Connection connection;

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    private String requestedUsername;

    @Shadow
    @Nullable
    private GameProfile authenticatedProfile;

    @Shadow
    public abstract void disconnect(Component reason);

    @Unique
    private LoginAuthState safeuuid$authState;

    @Unique
    private boolean safeuuid$authQuerySent;

    @Unique
    private boolean safeuuid$authQueryCompleted;

    @Invoker("finishLoginAndWaitForClient")
    protected abstract void safeuuid$invokeFinishLoginAndWaitForClient(GameProfile profile);

    @Inject(method = "tick", at = @At("HEAD"))
    private void safeuuid$handleAuthTimeout(CallbackInfo ci) {
        if (this.safeuuid$authState == null || this.safeuuid$authQueryCompleted) {
            return;
        }

        long elapsedMillis = System.currentTimeMillis() - this.safeuuid$authState.createdAtMillis();
        if (elapsedMillis < SafeUuidConfig.AUTH.timeoutMs()) {
            return;
        }

        this.safeuuid$authState.setClientAuthOk(false);
        if (!SafeUuidConfig.AUTH.allowOfflineOnTimeout()) {
            SafeUUID.LOGGER.info("[SafeUUID] authentication timeout, disconnecting name={}", this.requestedUsername);
            this.disconnect(Component.literal(SafeUuidConfig.AUTH.timeoutKickMessage()));
            return;
        }

        if (this.safeuuid$denyOfflineFallback("timeout")) {
            return;
        }

        SafeUUID.debugLog("[SafeUUID] auth state recorded after timeout fallback");
        this.safeuuid$authQueryCompleted = true;
        this.safeuuid$invokeFinishLoginAndWaitForClient(this.authenticatedProfile);
    }

    @Inject(method = "finishLoginAndWaitForClient", at = @At("HEAD"), cancellable = true)
    private void safeuuid$gateFinishLogin(GameProfile profile, CallbackInfo ci) {
        if (!this.safeuuid$authQuerySent) {
            int txId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            String serverId = UUID.randomUUID().toString().replace("-", "");
            this.safeuuid$authState = new LoginAuthState(txId, serverId);
            this.safeuuid$authQuerySent = true;

            SafeUUID.debugLog("[SafeUUID] send auth query txId={} serverId={}", txId, serverId);
            this.connection.send(new ClientboundCustomQueryPacket(txId, new AuthPayload(serverId)));
            ci.cancel();
            return;
        }

        if (!this.safeuuid$authQueryCompleted) {
            ci.cancel();
        }
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void safeuuid$handleCustomQueryPacket(ServerboundCustomQueryAnswerPacket packet, CallbackInfo ci) {
        if (this.safeuuid$authState == null || packet.transactionId() != this.safeuuid$authState.txId()) {
            return;
        }

        boolean ok = false;
        Object payloadObject = packet.payload();
        try {
            if (payloadObject == null) {
                ok = false;
            } else if (payloadObject instanceof AuthAnswerPayload authAnswerPayload) {
                ok = authAnswerPayload.ok();
            } else {
                ResourceLocation payloadId = this.safeuuid$readPayloadId(payloadObject);
                if (NetIds.AUTH_ANSWER.equals(payloadId) || payloadId == null) {
                    Boolean decodedOk = this.safeuuid$readAnswerOkFromPayloadData(payloadObject);
                    ok = decodedOk != null ? decodedOk : true;
                } else {
                    ok = true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            SafeUUID.LOGGER.error("[SafeUUID] failed to decode auth answer payload name={} txId={}", this.requestedUsername, packet.transactionId(), exception);
            ok = payloadObject != null;
        }

        this.safeuuid$authState.setClientAuthOk(ok);
        SafeUUID.debugLog("[SafeUUID] server received auth answer ok={}", ok);
        if (ok) {
            this.safeuuid$checkHasJoined();
            if (!this.safeuuid$authState.hasJoinedSuccess() && this.safeuuid$denyOfflineFallback("failure")) {
                ci.cancel();
                return;
            }
        } else if (this.safeuuid$denyOfflineFallback("failure")) {
            ci.cancel();
            return;
        }

        SafeUUID.debugLog("[SafeUUID] auth state recorded");
        this.safeuuid$authQueryCompleted = true;
        this.safeuuid$invokeFinishLoginAndWaitForClient(this.authenticatedProfile);
        ci.cancel();
    }

    @Unique
    private void safeuuid$checkHasJoined() {
        if (this.safeuuid$authState == null || this.safeuuid$authState.hasJoinedChecked()) {
            return;
        }

        String playerName = this.requestedUsername;
        String serverId = this.safeuuid$authState.serverId();
        this.safeuuid$authState.setHasJoinedChecked(true);
        SafeUUID.debugLog("[SafeUUID] hasJoined begin playerName={} serverId={}", playerName, serverId);

        try {
            ProfileResult profileResult = this.server.getSessionService().hasJoinedServer(playerName, serverId, this.safeuuid$getSessionServiceAddress());
            if (profileResult == null) {
                this.safeuuid$authState.setHasJoinedSuccess(false);
                SafeUUID.LOGGER.info("[SafeUUID] authentication failed: hasJoined returned empty name={}", playerName);
                return;
            }

            GameProfile profile = profileResult.profile();
            this.safeuuid$authState.setHasJoinedSuccess(true);
            this.safeuuid$authState.setPremiumUuid(profile.getId());
            this.safeuuid$authState.setPremiumName(profile.getName());
            this.safeuuid$authState.setPremiumProfile(profile);
            PremiumNameRegistry.recordPremiumName(profile.getName(), profile.getId());
            RecentIpGraceRegistry.recordSuccess(profile.getName(), NetworkAddressUtil.remoteInetAddress(this.connection));
            SafeUUID.LOGGER.info("[SafeUUID] authentication success name={} premiumUuid={}", profile.getName(), profile.getId());
        } catch (AuthenticationException | RuntimeException exception) {
            this.safeuuid$authState.setHasJoinedSuccess(false);
            SafeUUID.LOGGER.info("[SafeUUID] authentication failed name={} serverId={} error={}", playerName, serverId, exception.toString());
        }
    }

    @Unique
    private boolean safeuuid$denyOfflineFallback(String reason) {
        boolean knownPremium = PremiumNameRegistry.isKnownPremiumName(this.requestedUsername);
        if (knownPremium && RecentIpGraceRegistry.hasGrace(this.requestedUsername, NetworkAddressUtil.remoteInetAddress(this.connection))) {
            Optional<UUID> premiumUuid = PremiumNameRegistry.findPremiumUuid(this.requestedUsername);
            if (premiumUuid.isPresent()) {
                this.safeuuid$applyRecentIpGraceProfile(premiumUuid.get());
                return false;
            }
            SafeUUID.debugLog("[SafeUUID] recentIpGrace miss name={} ip={}", this.requestedUsername, NetworkAddressUtil.format(NetworkAddressUtil.remoteInetAddress(this.connection)));
        }

        if (!"timeout".equals(reason) && !SafeUuidConfig.AUTH.allowOfflineOnFailure()) {
            SafeUUID.LOGGER.info("[SafeUUID] offline fallback denied by config name={}", this.requestedUsername);
            this.disconnect(Component.literal("账号校验失败，服务器已禁止离线模式兜底"));
            return true;
        }

        if (knownPremium && (SafeUuidConfig.AUTH.knownPremiumDenyOffline() || SafeUuidConfig.AUTH.allowOfflineForUnknownOnly())) {
            SafeUUID.LOGGER.info("[SafeUUID] name is known premium, deny offline fallback");
            this.disconnect(Component.literal("该名字已绑定正版身份，当前认证失败，禁止以离线身份进入"));
            return true;
        }

        SafeUUID.LOGGER.info("[SafeUUID] offline fallback allowed for unknown name name={} reason={}", this.requestedUsername, reason);
        return false;
    }

    @Unique
    private void safeuuid$applyRecentIpGraceProfile(UUID premiumUuid) {
        if (this.safeuuid$authState == null) {
            return;
        }

        this.safeuuid$authState.setClientAuthOk(true);
        this.safeuuid$authState.setHasJoinedChecked(true);
        this.safeuuid$authState.setHasJoinedSuccess(true);
        this.safeuuid$authState.setPremiumUuid(premiumUuid);
        this.safeuuid$authState.setPremiumName(this.requestedUsername);
        this.safeuuid$authState.setPremiumProfile(new GameProfile(premiumUuid, this.requestedUsername));
        this.safeuuid$authState.setRecentIpGraceApplied(true);
        SafeUUID.LOGGER.info("[SafeUUID] recentIpGrace apply premium profile name={} uuid={}", this.requestedUsername, premiumUuid);
    }

    @Unique
    @Nullable
    private InetAddress safeuuid$getSessionServiceAddress() {
        SocketAddress socketAddress = this.connection.getRemoteAddress();
        return this.server.getPreventProxyConnections() && socketAddress instanceof InetSocketAddress inetSocketAddress
                ? inetSocketAddress.getAddress()
                : null;
    }

    @Unique
    @Nullable
    private ResourceLocation safeuuid$readPayloadId(Object payloadObject) throws ReflectiveOperationException {
        try {
            Method idMethod = payloadObject.getClass().getMethod("id");
            Object idObject = idMethod.invoke(payloadObject);
            return idObject instanceof ResourceLocation resourceLocation ? resourceLocation : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    @Unique
    @Nullable
    private Boolean safeuuid$readAnswerOkFromPayloadData(Object payloadObject) throws ReflectiveOperationException {
        Method dataMethod = payloadObject.getClass().getMethod("data");
        Object dataObject = dataMethod.invoke(payloadObject);
        if (dataObject instanceof FriendlyByteBuf friendlyByteBuf) {
            FriendlyByteBuf copy = new FriendlyByteBuf(friendlyByteBuf.copy());
            try {
                return AuthAnswerPayload.read(copy).ok();
            } finally {
                copy.release();
            }
        }

        if (dataObject instanceof ByteBuf byteBuf) {
            FriendlyByteBuf copy = new FriendlyByteBuf(byteBuf.copy());
            try {
                return AuthAnswerPayload.read(copy).ok();
            } finally {
                copy.release();
            }
        }

        return null;
    }

    @ModifyVariable(method = "finishLoginAndWaitForClient", at = @At("HEAD"), argsOnly = true)
    private GameProfile safeuuid$applyPremiumProfile(GameProfile profile) {
        if (this.safeuuid$authState == null
                || !Boolean.TRUE.equals(this.safeuuid$authState.clientAuthOk())
                || !this.safeuuid$authState.hasJoinedChecked()
                || !this.safeuuid$authState.hasJoinedSuccess()
                || this.safeuuid$authState.premiumUuid() == null) {
            SafeUUID.debugLog("[SafeUUID] keep offline profile");
            return profile;
        }

        try {
            SafeUUID.debugLog("[SafeUUID] apply premium profile begin uuid={} name={}", this.safeuuid$authState.premiumUuid(), this.safeuuid$authState.premiumName());
            if (this.safeuuid$authState.recentIpGraceApplied()) {
                SafeUUID.LOGGER.info("[SafeUUID] apply premium profile via recentIpGrace");
            }

            String premiumName = this.safeuuid$authState.premiumName();
            GameProfile premiumProfile = new GameProfile(
                    this.safeuuid$authState.premiumUuid(),
                    premiumName != null && !premiumName.isBlank() ? premiumName : profile.getName()
            );

            GameProfile resolvedProfile = this.safeuuid$authState.premiumProfile();
            if (resolvedProfile != null) {
                premiumProfile.getProperties().putAll(resolvedProfile.getProperties());
            }

            this.authenticatedProfile = premiumProfile;
            this.safeuuid$authState.setPremiumProfileApplied(true);
            SafeUUID.LOGGER.info("[SafeUUID] premium profile applied name={} uuid={}", premiumProfile.getName(), premiumProfile.getId());
            return premiumProfile;
        } catch (RuntimeException exception) {
            SafeUUID.LOGGER.warn("[SafeUUID] failed to apply premium profile name={} uuid={} error={}", this.safeuuid$authState.premiumName(), this.safeuuid$authState.premiumUuid(), exception.toString());
            SafeUUID.debugLog("[SafeUUID] keep offline profile after premium profile failure");
            return profile;
        }
    }

    @Inject(method = "handleLoginAcknowledgement", at = @At("HEAD"))
    private void safeuuid$handoffAuthState(net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket packet, CallbackInfo ci) {
        if (this.safeuuid$authState != null) {
            LoginAuthStateHolder.put(this.connection, this.safeuuid$authState);
        }
    }
}
