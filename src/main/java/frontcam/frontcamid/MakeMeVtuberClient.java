package frontcam.frontcamid;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OnlyIn(Dist.CLIENT)
public class MakeMeVtuberClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("make-me-vtuber-client");

    private static boolean renderWindowActive = false;
    private static MakeMeVtuberRenderer renderer = null;

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(MakeMeVtuberClient.class);
        LOGGER.info("[MakeMeVtuber] Client mod initialized. Use /mmv to open render window.");
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("mmv")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                Minecraft client = Minecraft.getInstance();
                LocalPlayer player = client.player;

                if (player == null) {
                    source.sendSuccess(() -> Component.literal("[MakeMeVtuber] Player not available."), false);
                    return 0;
                }

                String info = buildBodyPartInfo(player);
                source.sendSuccess(() -> Component.literal(info), false);

                if (renderer == null) {
                    renderer = new MakeMeVtuberRenderer();
                }
                renderer.open();
                renderWindowActive = true;

                MakeMeVtuberSettingsWindow.getInstance().open();

                source.sendSuccess(() -> Component.literal("§a[MakeMeVtuber] Render window opened. Transmitting body part data..."), false);
                return 1;
            })
        );
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (renderWindowActive && renderer != null) {
            Minecraft client = Minecraft.getInstance();
            renderer.tick(client);
        }
    }

    private static String buildBodyPartInfo(LocalPlayer player) {
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
