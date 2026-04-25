package cn.alini.safeuuid.net;

import cn.alini.safeuuid.SafeUUID;
import net.minecraft.resources.ResourceLocation;

public final class NetIds {
    // Server -> client authentication challenge payload id.
    public static final ResourceLocation AUTH = ResourceLocation.fromNamespaceAndPath(SafeUUID.MOD_ID, "auth");
    // Client -> server authentication answer payload id.
    public static final ResourceLocation AUTH_ANSWER = ResourceLocation.fromNamespaceAndPath(SafeUUID.MOD_ID, "auth_answer");

    private NetIds() {
    }
}
