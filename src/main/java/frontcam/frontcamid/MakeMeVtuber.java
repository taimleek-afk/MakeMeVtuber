package frontcam.frontcamid;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MakeMeVtuber.MOD_ID)
public class MakeMeVtuber {
    public static final String MOD_ID = "makemevtuber";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MakeMeVtuber(IEventBus modEventBus) {
        LOGGER.info("[MakeMeVtuber] Main mod initialized.");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MakeMeVtuberClient.init(modEventBus);
        }
    }
}
