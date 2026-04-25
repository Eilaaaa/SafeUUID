package cn.alini.safeuuid.mixin.protocol;

import cn.alini.safeuuid.SafeUUID;
import cn.alini.safeuuid.net.AuthPayload;
import cn.alini.safeuuid.net.NetIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ClientboundCustomQueryPacket.class)
public abstract class ClientboundCustomQueryPacketMixin {
    /**
     * @author OpenAI
     * @reason Decode SafeUUID login query payload instead of discarding it as unknown.
     */
    @Overwrite
    private static CustomQueryPayload readPayload(ResourceLocation id, FriendlyByteBuf buffer) {
        if (NetIds.AUTH.equals(id)) {
            SafeUUID.debugLog("[SafeUUID] protocol decode auth query payload");
            return AuthPayload.read(buffer);
        }

        int readableBytes = buffer.readableBytes();
        if (readableBytes >= 0 && readableBytes <= 1048576) {
            buffer.skipBytes(readableBytes);
            return new DiscardedQueryPayload(id);
        }

        throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
    }
}
