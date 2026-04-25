package cn.alini.safeuuid.mixin.server;

import cn.alini.safeuuid.SafeUUID;
import cn.alini.safeuuid.auth.LoginAuthState;
import cn.alini.safeuuid.auth.LoginAuthStateHolder;
import cn.alini.safeuuid.auth.PlayerAuthStatus;
import cn.alini.safeuuid.auth.PlayerAuthStatusHolder;
import cn.alini.safeuuid.config.SafeUuidConfig;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void safeuuid$notifyPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        LoginAuthState authState = LoginAuthStateHolder.remove(connection);
        if (authState == null) {
            SafeUUID.debugLog("[SafeUUID] no auth state found for player name={} uuid={}", player.getGameProfile().getName(), player.getUUID());
            PlayerAuthStatusHolder.put(player.getUUID(), PlayerAuthStatus.UNKNOWN);
            safeuuid$logInteractionState(player, PlayerAuthStatus.UNKNOWN, null, false);
            return;
        }

        boolean premiumApplied = Boolean.TRUE.equals(authState.clientAuthOk())
                && authState.hasJoinedChecked()
                && authState.hasJoinedSuccess()
                && authState.premiumProfileApplied();
        SafeUUID.debugLog("[SafeUUID] final player profile name={} uuid={} premiumApplied={}", player.getGameProfile().getName(), player.getUUID(), premiumApplied);

        if (premiumApplied) {
            PlayerAuthStatus status = authState.recentIpGraceApplied() ? PlayerAuthStatus.RECENT_IP_GRACE : PlayerAuthStatus.PREMIUM_APPLIED;
            PlayerAuthStatusHolder.put(player.getUUID(), status);
            safeuuid$logInteractionState(player, status, authState, true);
            Component subtitle = Component.literal(SafeUuidConfig.AUTH.onlineShortSubtitle());
            safeuuid$sendShortSubtitle(player, subtitle);
            player.sendSystemMessage(subtitle);
            SafeUUID.LOGGER.info("[SafeUUID] player authenticated name={} uuid={} status={}", player.getGameProfile().getName(), player.getUUID(), status.name());
            return;
        }

        PlayerAuthStatusHolder.put(player.getUUID(), PlayerAuthStatus.OFFLINE_FALLBACK);
        safeuuid$logInteractionState(player, PlayerAuthStatus.OFFLINE_FALLBACK, authState, false);
        Component subtitle = Component.literal(SafeUuidConfig.AUTH.offlineShortSubtitle());
        safeuuid$sendShortSubtitle(player, subtitle);
        player.sendSystemMessage(subtitle);
        player.sendSystemMessage(Component.literal(SafeUuidConfig.AUTH.offlineFallbackMessage()));
        SafeUUID.LOGGER.info("[SafeUUID] player entered with offline fallback name={} uuid={}", player.getGameProfile().getName(), player.getUUID());
    }

    private static void safeuuid$sendShortSubtitle(ServerPlayer player, Component subtitle) {
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    }

    private static void safeuuid$logInteractionState(
            ServerPlayer player,
            PlayerAuthStatus status,
            LoginAuthState authState,
            boolean premiumApplied
    ) {
        Abilities abilities = player.getAbilities();
        SafeUUID.debugLog("[SafeUUID] post-login interaction state begin");
        SafeUUID.debugLog("[SafeUUID] interaction playerName={}", player.getGameProfile().getName());
        SafeUUID.debugLog("[SafeUUID] interaction uuid={}", player.getUUID());
        SafeUUID.debugLog("[SafeUUID] interaction gameMode={}", player.gameMode.getGameModeForPlayer());
        SafeUUID.debugLog("[SafeUUID] interaction previousGameMode={}", player.gameMode.getPreviousGameModeForPlayer());
        SafeUUID.debugLog("[SafeUUID] interaction abilities invulnerable={} flying={} mayfly={} instabuild={} mayBuild={} flyingSpeed={} walkingSpeed={}",
                abilities.invulnerable,
                abilities.flying,
                abilities.mayfly,
                abilities.instabuild,
                abilities.mayBuild,
                abilities.getFlyingSpeed(),
                abilities.getWalkingSpeed());
        SafeUUID.debugLog("[SafeUUID] interaction isSpectator={}", player.isSpectator());
        SafeUUID.debugLog("[SafeUUID] interaction isCreative={}", player.isCreative());
        SafeUUID.debugLog("[SafeUUID] interaction canBuild={}", abilities.mayBuild);
        SafeUUID.debugLog("[SafeUUID] interaction authStatus={}", status.name());
        SafeUUID.debugLog("[SafeUUID] interaction premiumApplied={}", premiumApplied);
        if (authState != null) {
            SafeUUID.debugLog("[SafeUUID] interaction auth clientAuthOk={}", authState.clientAuthOk());
            SafeUUID.debugLog("[SafeUUID] interaction auth hasJoinedChecked={}", authState.hasJoinedChecked());
            SafeUUID.debugLog("[SafeUUID] interaction auth hasJoinedSuccess={}", authState.hasJoinedSuccess());
            SafeUUID.debugLog("[SafeUUID] interaction auth premiumProfileApplied={}", authState.premiumProfileApplied());
            SafeUUID.debugLog("[SafeUUID] interaction auth recentIpGraceApplied={}", authState.recentIpGraceApplied());
            SafeUUID.debugLog("[SafeUUID] interaction branch={}", premiumApplied ? "premium" : "offline_fallback");
        } else {
            SafeUUID.debugLog("[SafeUUID] interaction branch=unknown_no_auth_state");
        }
        SafeUUID.debugLog("[SafeUUID] post-login interaction state end");
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void safeuuid$clearRuntimeState(ServerPlayer player, CallbackInfo ci) {
        PlayerAuthStatusHolder.remove(player.getUUID());
    }
}
