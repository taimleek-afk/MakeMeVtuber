package frontcam.frontcamid;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class MakeMeVtuberClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("make-me-vtuber-client");

    private static boolean renderWindowActive = false;
    private static MakeMeVtuberRenderer renderer = null;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (renderWindowActive && renderer != null) {
                renderer.tick(client);
            }
        });

        LOGGER.info("[MakeMeVtuber] Client mod initialized. Use /mmv to open render window.");
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandManager.literal("mmv")
            .executes(context -> {
                FabricClientCommandSource source = context.getSource();
                Minecraft client = Minecraft.getInstance();
                LocalPlayer player = client.player;

                if (player == null) {
                    source.sendFeedback(Component.literal("[MakeMeVtuber] Player not available."));
                    return 0;
                }

                String info = buildBodyPartInfo(player);
                source.sendFeedback(Component.literal(info));

                if (renderer == null) {
                    renderer = new MakeMeVtuberRenderer();
                }
                renderer.open();
                renderWindowActive = true;

                MakeMeVtuberSettingsWindow.getInstance().open();

                source.sendFeedback(Component.literal("§a[MakeMeVtuber] Render window opened. Transmitting body part data..."));
                return 1;
            })
        );
    }

    private String buildBodyPartInfo(LocalPlayer player) {
        StringBuilder sb = new StringBuilder();
        sb.append("§a[MakeMeVtuber] Body Part Rotations:\n");
        sb.append("§f Head Yaw: ").append(String.format("%.2f", player.getYHeadRot() - player.yBodyRot)).append("°\n");
        sb.append("§f Head Pitch: ").append(String.format("%.2f", player.getXRot())).append("°\n");
        sb.append("§f Body Yaw: ").append(String.format("%.2f", player.yBodyRot)).append("°\n");
        sb.append("§f Skin Model: ").append(
            ((net.minecraft.client.player.AbstractClientPlayer) player).getSkin().model().id()
        ).append("\n");
        sb.append("§7(Live data streaming to render window)");
        return sb.toString();
    }

    public static void stopRenderer() {
        renderWindowActive = false;
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        MakeMeVtuberSettingsWindow.getInstance().close();
    }
}
