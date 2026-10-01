package com.aura.cornerstone.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CornerstoneClient implements ClientModInitializer {
    public static final String MOD_ID = "cornerstone";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        SaveStore.load();
        SelectionManager.register();
        CommandQueueRunner.register();
        AsyncRegionSaver.register();
        CornerstoneCommands.register();
    }
}
