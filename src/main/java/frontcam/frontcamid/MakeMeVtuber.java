package frontcam.frontcamid;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MakeMeVtuber implements ModInitializer {
    public static final String MOD_ID = "makemevtuber";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[MakeMeVtuber] Main mod initialized.");
    }
}
