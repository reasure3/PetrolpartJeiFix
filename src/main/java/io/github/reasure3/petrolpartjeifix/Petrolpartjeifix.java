package io.github.reasure3.petrolpartjeifix;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value = Petrolpartjeifix.MOD_ID, dist = Dist.CLIENT)
public final class Petrolpartjeifix {
    public static final String MOD_ID = "petrolpapetrolpartjeifix";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Petrolpartjeifix(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Loaded Petrolpark 1.4.36 / JEI 19.39.0.368 compatibility patch");
    }
}
