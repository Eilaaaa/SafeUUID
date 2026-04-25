package cn.alini.safeuuid.command;

import cn.alini.safeuuid.auth.PlayerAuthStatus;
import cn.alini.safeuuid.auth.PlayerAuthStatusHolder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SafeUUIDCommands {
    private SafeUUIDCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("safeuuid")
                .then(Commands.literal("whoami")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PlayerAuthStatus status = PlayerAuthStatusHolder.get(player.getUUID());
                            player.sendSystemMessage(Component.literal("Name: " + player.getGameProfile().getName()));
                            player.sendSystemMessage(Component.literal("UUID: " + player.getUUID()));
                            player.sendSystemMessage(Component.literal("AuthState: " + status.name()));
                            return 1;
                        }));

        LinkCommand.appendToRoot(root);
        dispatcher.register(root);
    }
}
