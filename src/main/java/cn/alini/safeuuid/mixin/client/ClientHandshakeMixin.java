package cn.alini.safeuuid.mixin.client;

import cn.alini.safeuuid.SafeUUID;
import cn.alini.safeuuid.net.AuthAnswerPayload;
import cn.alini.safeuuid.net.AuthPayload;
import cn.alini.safeuuid.net.NetIds;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakeMixin {
    @Shadow
    private Connection connection;

    @Shadow
    private Minecraft minecraft;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void safeuuid$handleCustomQuery(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
        String step = "enter";
        try {
            step = "read_payload";
            SafeUUID.debugLog("[SafeUUID] client auth query packet class={}", packet.getClass().getName());
            Object payloadObject = packet.payload();
            SafeUUID.debugLog(
                    "[SafeUUID] client auth query payload={}",
                    payloadObject == null ? "null" : payloadObject.getClass().getName() + " :: " + payloadObject
            );
            String serverId;
            if (payloadObject instanceof AuthPayload authPayload) {
                if (!authPayload.id().equals(NetIds.AUTH)) {
                    return;
                }
                step = "read_server_id";
                serverId = authPayload.serverId();
            } else {
                ResourceLocation payloadId = this.safeuuid$readPayloadId(payloadObject);
                if (!NetIds.AUTH.equals(payloadId)) {
                    return;
                }
                step = "read_server_id";
                serverId = this.safeuuid$readServerIdFromPayloadData(payloadObject);
            }

            if (serverId == null) {
                return;
            }
            step = "join_server";
            SafeUUID.debugLog("[SafeUUID] client received auth query serverId={}", serverId);
            boolean ok = false;

            Minecraft minecraft = this.minecraft;

            java.util.UUID profileId = minecraft.getUser().getProfileId();
            String accessToken = minecraft.getUser().getAccessToken();
            SafeUUID.debugLog("[SafeUUID] client joinServer begin profileId={} tokenEmpty={}", profileId, accessToken == null || accessToken.isEmpty());
            MinecraftSessionService sessionService = minecraft.getMinecraftSessionService();

            try {
                sessionService.joinServer(profileId, accessToken, serverId);
                ok = true;
                SafeUUID.debugLog("[SafeUUID] client joinServer success");
            } catch (AuthenticationException | RuntimeException exception) {
                SafeUUID.debugLog("[SafeUUID] client joinServer failed: {}: {}", exception.getClass().getName(), exception.getMessage());
                if (cn.alini.safeuuid.config.SafeUuidConfig.debug()) {
                    SafeUUID.LOGGER.info("[SafeUUID] client joinServer stacktrace", exception);
                }
            }

            step = "send_answer";
            this.connection.send(new ServerboundCustomQueryAnswerPacket(packet.transactionId(), ok ? this.safeuuid$createAnswerPayload() : null));
            SafeUUID.debugLog("[SafeUUID] client sent auth answer ok={}", ok);
            ci.cancel();
        } catch (Throwable throwable) {
            SafeUUID.LOGGER.error(
                    "[SafeUUID] client auth query handling failed stage={} type={} message={}",
                    step,
                    throwable.getClass().getName(),
                    throwable.getMessage(),
                    throwable
            );
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(throwable);
        }
    }

    private ResourceLocation safeuuid$readPayloadId(Object payloadObject) throws ReflectiveOperationException {
        Method idMethod = payloadObject.getClass().getMethod("id");
        Object idObject = idMethod.invoke(payloadObject);
        return idObject instanceof ResourceLocation resourceLocation ? resourceLocation : null;
    }

    private CustomQueryAnswerPayload safeuuid$createAnswerPayload() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(1));
        buffer.writeBoolean(true);

        try {
            Class<?> responseClass = Class.forName("net.fabricmc.fabric.impl.networking.payload.PacketByteBufLoginQueryResponse");
            return (CustomQueryAnswerPayload) responseClass.getConstructor(FriendlyByteBuf.class).newInstance(buffer);
        } catch (ReflectiveOperationException | LinkageError exception) {
            SafeUUID.debugLog("[SafeUUID] fabric login response payload unavailable, using SafeUUID payload: {}", exception.toString());
            buffer.release();
            return new AuthAnswerPayload(true);
        }
    }

    private String safeuuid$readServerIdFromPayloadData(Object payloadObject) throws ReflectiveOperationException {
        Method dataMethod = payloadObject.getClass().getMethod("data");
        Object dataObject = dataMethod.invoke(payloadObject);
        if (dataObject instanceof FriendlyByteBuf friendlyByteBuf) {
            FriendlyByteBuf copy = new FriendlyByteBuf(friendlyByteBuf.copy());
            try {
                return AuthPayload.read(copy).serverId();
            } finally {
                copy.release();
            }
        }

        if (dataObject instanceof ByteBuf byteBuf) {
            FriendlyByteBuf copy = new FriendlyByteBuf(byteBuf.copy());
            try {
                return AuthPayload.read(copy).serverId();
            } finally {
                copy.release();
            }
        }

        throw new IllegalStateException("Unsupported login query payload data type: " + (dataObject == null ? "null" : dataObject.getClass().getName()));
    }
}
