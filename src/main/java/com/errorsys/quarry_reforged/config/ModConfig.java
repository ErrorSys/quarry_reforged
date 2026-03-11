package com.errorsys.quarry_reforged.config;

import com.errorsys.quarry_reforged.QuarryReforged;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "quarry_reforged.json";

    public static Config DATA = new Config();

    private ModConfig() {}

    public static void loadOrCreate() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                DATA = GSON.fromJson(r, Config.class);
                if (DATA == null) DATA = new Config();
                DATA.normalize();
                // Persist normalized/new fields into existing config files.
                save();
                QuarryReforged.LOGGER.info("Loaded config {}", path.toAbsolutePath());
                return;
            } catch (Exception e) {
                QuarryReforged.LOGGER.error("Failed reading config {}, using defaults", path, e);
            }
        }

        DATA = new Config();
        DATA.normalize();
        try {
            save();
        } catch (IOException e) {
            QuarryReforged.LOGGER.error("Failed writing default config {}", path, e);
        }
    }

    public static void save() throws IOException {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try (Writer w = Files.newBufferedWriter(path)) {
            GSON.toJson(DATA, w);
        }
    }

    public static class Config {
        public int maxQuarrySize = 64;
        public List<String> blacklist = new ArrayList<>();

        public boolean allowDefaultArea = true;
        public int defaultAreaSize = 9;

        public long energyCapacity = 2000000;
        public long maxEnergyInput = 8192;
        public long energyPerFrame = 25;
        public long energyPerBlock = 400;
        public float hardnessEnergyScale = 25.0f;

        public int ticksPerBlock = 20;

        public boolean enableChunkloadingUpgrade = true;
        public int chunkloadingUpgradeRadius = 1;
        public int chunkTicketLevel = 2;

        public int rediscoveryScanIntervalTicks = 20;
        public boolean autoFrameRepair = true;
        public int frameRebuildScanInterval = 40;
        public boolean debug = false;
        public boolean enableStateDebugLogging = false;

        public void normalize() {
            maxQuarrySize = Math.max(1, maxQuarrySize);
            if (blacklist == null) blacklist = new ArrayList<>();
            defaultAreaSize = Math.max(1, defaultAreaSize);
            energyCapacity = Math.max(1L, energyCapacity);
            maxEnergyInput = Math.max(0L, maxEnergyInput);
            energyPerFrame = Math.max(0L, energyPerFrame);
            energyPerBlock = Math.max(0L, energyPerBlock);
            hardnessEnergyScale = Math.max(0.0f, hardnessEnergyScale);
            ticksPerBlock = Math.max(1, ticksPerBlock);
            chunkloadingUpgradeRadius = Math.max(0, chunkloadingUpgradeRadius);
            chunkTicketLevel = Math.max(0, chunkTicketLevel);
            rediscoveryScanIntervalTicks = Math.max(1, rediscoveryScanIntervalTicks);
            frameRebuildScanInterval = Math.max(1, frameRebuildScanInterval);
        }
    }
}
