package cn.alini.safeuuid;

import cn.alini.safeuuid.command.SafeUUIDCommands;
import cn.alini.safeuuid.config.SafeUuidConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(SafeUUID.MOD_ID)
public final class SafeUUID {
    public static final String MOD_ID = "safeuuid";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SafeUUID(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SafeUuidConfig.SPEC, "safeuuid-common.toml");
        modEventBus.addListener(SafeUuidConfig::onLoad);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        LOGGER.info("[SafeUUID] version {} loaded", modContainer.getModInfo().getVersion());
    }

    public static void debugLog(String message, Object... arguments) {
        if (SafeUuidConfig.debug()) {
            LOGGER.info(message, arguments);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        SafeUUIDCommands.register(event.getDispatcher());
    }
}
