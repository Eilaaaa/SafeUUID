package cn.alini.safeuuid.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;

// Server -> client authentication request payload.
public record AuthPayload(String serverId) implements CustomQueryPayload {
    public static AuthPayload read(FriendlyByteBuf buffer) {
        return new AuthPayload(buffer.readUtf(255));
    }

    @Override
    public ResourceLocation id() {
        return NetIds.AUTH;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.serverId, 255);
    }
}
