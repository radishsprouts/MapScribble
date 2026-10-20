package io.github.radishsprouts.mapscribble.store;

/** 切り替わりの順序（書き切る → 捨てる → 読み直す）を決める部分。JourneyMap に依存せず、試験できる。 */
public final class SessionController {
    private final MemoStore store;
    private SessionKey current;

    public SessionController(MemoStore store) {
        this.store = store;
    }

    public SessionKey current() {
        return current;
    }

    /** MAPPING_STARTED。鍵が取れなければ（保存先なし）停止として扱う。 */
    public void started(SessionKey key) {
        if (key == null) {
            stopped();
            return;
        }
        store.open(key.file());
        current = key;
    }

    /** MAPPING_STOPPED。書き切って手持ちを捨て、リスナーへ通知する。 */
    public void stopped() {
        store.close();
        current = null;
    }

    /** 保険の比べ直し。鍵が変わっていれば STOPPED → STARTED と同じ処理をする。 */
    public void recheck(SessionKey now) {
        if (SessionKey.needsSwitch(current, now)) {
            started(now);
        }
    }

    /** 切断・ゲーム終了。予約分を書き切るだけで、手持ちは残す。 */
    public void flush() {
        store.flush();
    }
}
