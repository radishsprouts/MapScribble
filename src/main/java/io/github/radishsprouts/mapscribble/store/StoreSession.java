package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 地図MOD非依存のメモ保存セッション管理。
 * 保存先は .minecraft/mapscribble/saves/<ワールド名>/ または servers/<ホスト_ポート>/。
 * 旧 JourneyMap フォルダからの自動データ移行にも対応する。
 */
public final class StoreSession {
    private static final MemoStore STORE = new MemoStore();
    private static final SessionController CONTROLLER = new SessionController(STORE);

    private StoreSession() {
    }

    public static MemoRepository repository() {
        return STORE;
    }

    public static void setNotifier(Consumer<String> notifier) {
        STORE.setProblemHandler(notifier);
    }

    public static void setSaveNotifier(Consumer<Boolean> notifier) {
        STORE.setSaveStateHandler(failed -> Minecraft.getInstance().execute(() -> notifier.accept(failed)));
    }

    /** ディメンション切り替え時や接続時に呼ぶ。 */
    public static void updateDimension(ResourceKey<Level> dimension) {
        if (dimension == null) {
            CONTROLLER.stopped();
            return;
        }
        SessionKey key = createKey(dimension);
        if (key != null) {
            CONTROLLER.started(key);
        } else {
            CONTROLLER.stopped();
        }
    }

    public static void flush() {
        CONTROLLER.flush();
    }

    public static void shutdown() {
        CONTROLLER.flush();
        STORE.shutdown();
    }

    private static SessionKey createKey(ResourceKey<Level> dimension) {
        Minecraft mc = Minecraft.getInstance();
        Path baseDir = ScreenCompat.gameDirectory().resolve("mapscribble");
        String worldFolder;
        try {
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                worldFolder = "saves/" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).getParent().getFileName().toString();
            } else if (mc.getCurrentServer() != null) {
                String ip = mc.getCurrentServer().ip.replace(':', '_').replaceAll("[^a-zA-Z0-9._-]", "_");
                worldFolder = "servers/" + ip;
            } else {
                worldFolder = "saves/unknown";
            }
        } catch (Exception e) {
            worldFolder = "saves/unknown";
        }

        Path saveDir = baseDir.resolve(worldFolder);
        String dimId = ScreenCompat.dimensionId(dimension);

        // 旧 JourneyMap データからの自動移行（もし存在すれば）
        migrateFromJourneyMapIfNeeded(saveDir, dimId);

        return new SessionKey(saveDir, worldFolder, dimId);
    }

    private static void migrateFromJourneyMapIfNeeded(Path targetDir, String dimId) {
        try {
            Path jmAddonDir = ScreenCompat.gameDirectory().resolve("journeymap").resolve("addon-data").resolve(MapScribble.MOD_ID);
            if (!Files.exists(jmAddonDir)) {
                return;
            }
            String fileName = MemoFile.fileName(dimId);
            Path oldFile = jmAddonDir.resolve(fileName);
            Path newFile = targetDir.resolve(fileName);
            if (Files.exists(oldFile) && !Files.exists(newFile)) {
                Files.createDirectories(targetDir);
                Files.copy(oldFile, newFile, StandardCopyOption.COPY_ATTRIBUTES);
                MapScribble.LOGGER.info("Migrated legacy MapScribble note from {} to {}", oldFile, newFile);
            }
        } catch (Exception e) {
            MapScribble.LOGGER.warn("Failed to check or migrate legacy notes: {}", e.getMessage());
        }
    }
}
