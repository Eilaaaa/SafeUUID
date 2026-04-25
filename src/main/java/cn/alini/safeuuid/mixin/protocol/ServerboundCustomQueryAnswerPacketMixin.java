package cn.alini.safeuuid.mixin.protocol;

import cn.alini.safeuuid.net.AuthAnswerPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ServerboundCustomQueryAnswerPacket.class)
public abstract class ServerboundCustomQueryAnswerPacketMixin {
    /**
     * @author OpenAI
     * @reason Decode SafeUUID login query answer using the packet's nullable payload format.
     */
    @Overwrite
    private static CustomQueryAnswerPayload readPayload(int transactionId, FriendlyByteBuf buffer) {
        int readableBytes = buffer.readableBytes();
        if (readableBytes == 0) {
            return DiscardedQueryAnswerPayload.INSTANCE;
        }

        if (readableBytes <= 2) {
            return buffer.readNullable(AuthAnswerPayload::read);
        }

        if (readableBytes <= 1048576) {
            buffer.skipBytes(readableBytes);
            return DiscardedQueryAnswerPayload.INSTANCE;
        }

        throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
    }
}
