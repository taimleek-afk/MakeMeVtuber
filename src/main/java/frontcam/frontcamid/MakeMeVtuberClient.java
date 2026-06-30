package frontcam.frontcamid;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class MakeMeVtuberClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("make-me-vtuber-client");

    private static boolean renderWindowActive = false;
    private static MakeMeVtuberRenderer renderer = null;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[MakeMeVtuber] Client mod initialized. Use /mmv to open render window.");

        // Register client command
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("mmv")
                .executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    MinecraftClient client = MinecraftClient.getInstance();
                    ClientPlayerEntity player = client.player;

                    if (player == null) {
                        source.sendFeedback(Text.literal("[MakeMeVtuber] Player not available."));
                        return 0;
                    }

                    String info = buildBodyPartInfo(player);
                    source.sendFeedback(Text.literal(info));

                    if (renderer == null) {
                        renderer = new MakeMeVtuberRenderer();
                    }
                    renderer.open();
                    renderWindowActive = true;

                    MakeMeVtuberSettingsWindow.getInstance().open();

                    source.sendFeedback(Text.literal("§a[MakeMeVtuber] Render window opened. Transmitting body part data..."));
                    return 1;
                })
            );
        });

        // Register client tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (renderWindowActive && renderer != null) {
                renderer.tick(client);
            }
        });
    }

    private static String buildBodyPartInfo(ClientPlayerEntity player) {
        StringBuilder sb = new StringBuilder();
        sb.append("§a[MakeMeVtuber] Body Part Rotations:\n");
        sb.append("§f Head Yaw: ").append(String.format("%.2f", player.getHeadYaw() - player.bodyYaw)).append("°\n");
        sb.append("§f Head Pitch: ").append(String.format("%.2f", player.getPitch())).append("°\n");
        sb.append("§f Body Yaw: ").append(String.format("%.2f", player.bodyYaw)).append("°\n");
        sb.append("§f Skin Model: ").append(
            player.getSkinTextures().model().getName()
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
