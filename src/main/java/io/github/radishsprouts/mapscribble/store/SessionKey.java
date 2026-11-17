package io.github.radishsprouts.mapscribble.store;

import java.nio.file.Path;

/**
 * 今どのサーバー・ディメンションのメモを持っているかの鍵。JourneyMap が返す値をそのまま使い、独自の判定はしない。
 *
 * @param dataPath  メモを置くフォルダ（アドレスのフォルダ、またはシングルのワールドのフォルダ）
 * @param worldId   world id
 * @param dimension ディメンション（例: minecraft:overworld）
 * @param notebook  メモ帳のフォルダ名（例: nb-1a2b…）。null なら直下（今までのとおり）
 */
public record SessionKey(Path dataPath, String worldId, String dimension, String notebook) {
    /** メモ帳なし（直下）の鍵。 */
    public SessionKey(Path dataPath, String worldId, String dimension) {
        this(dataPath, worldId, dimension, null);
    }

    /** 手持ちの鍵と今の鍵を比べ、読み直す必要があるか。今の鍵が取れない間（null）は何もしない。 */
    public static boolean needsSwitch(SessionKey current, SessionKey now) {
        return now != null && !now.equals(current);
    }

    /** 書き込み先（主ファイル）。メモ帳があればその中、無ければ直下。 */
    public Path file() {
        if (notebook == null) {
            return MemoFile.resolve(dataPath, dimension);
        }
        return MemoFile.resolve(dataPath.resolve(notebook), dimension);
    }

    /** 分ける前のファイル。メモ帳を開いている時だけ（直下の同じディメンションのファイル）。無ければ null。 */
    public Path legacyFile() {
        return notebook == null ? null : MemoFile.resolve(dataPath, dimension);
    }
}
