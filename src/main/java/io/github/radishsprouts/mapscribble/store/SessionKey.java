package io.github.radishsprouts.mapscribble.store;

import java.nio.file.Path;

/**
 * 今どのサーバー・ディメンションのメモを持っているかの鍵。JourneyMap が返す値をそのまま使い、独自の判定はしない。
 *
 * @param dataPath  JourneyMap のアドオン用データフォルダ（world id ごとに別になる）
 * @param worldId   JourneyMap の world id
 * @param dimension ディメンション（例: minecraft:overworld）
 */
public record SessionKey(Path dataPath, String worldId, String dimension) {
    /** 手持ちの鍵と今の鍵を比べ、読み直す必要があるか。今の鍵が取れない間（null）は何もしない。 */
    public static boolean needsSwitch(SessionKey current, SessionKey now) {
        return now != null && !now.equals(current);
    }

    public Path file() {
        return MemoFile.resolve(dataPath, dimension);
    }
}
