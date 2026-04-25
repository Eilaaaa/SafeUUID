package cn.alini.safeuuid.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;

// Client -> server authentication result payload.
public record AuthAnswerPayload(boolean ok) implements CustomQueryAnswerPayload {
    public static AuthAnswerPayload read(FriendlyByteBuf buffer) {
        return new AuthAnswerPayload(buffer.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.ok);
    }
}
