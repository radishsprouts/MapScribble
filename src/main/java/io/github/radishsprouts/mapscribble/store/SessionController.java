package io.github.radishsprouts.mapscribble.store;

import java.util.function.Consumer;

/** 切り替わりの順序（書き切る → 捨てる → 読み直す）を決める部分。地図MODに依存せず、試験できる。 */
public final class SessionController {
    private final MemoStore store;
    private SessionKey current;

    public SessionController(MemoStore store) {
        this.store = store;
    }

    public SessionKey current() {
        return current;
    }

    /** 今の鍵と比べて、読み直しが要るか。 */
    public boolean needsSwitch(SessionKey now) {
        return SessionKey.needsSwitch(current, now);
    }

    /**
     * 毎 tick 呼ばれる経路。鍵が変わった時だけ切り替え、変わっていなければ何もしない。
     * 鍵が変わっていないのに {@link #started} を呼ぶと、そのたびにファイルを読み直して
     * メモの実体が入れ替わる（描画側のキャッシュが毎 tick 外れる）ので、ここで弾く。
     *
     * @param now        今の鍵。null なら停止（すでに停止していれば何もしない）
     * @param beforeOpen 切り替えが要る時に、ファイルを開く直前へ割り込む処理（古いデータの移行など）。null 可
     * @return 切り替えた（または停止した）なら true
     */
    public boolean update(SessionKey now, Consumer<SessionKey> beforeOpen) {
        if (now == null) {
            if (current == null) {
                return false;
            }
            stopped();
            return true;
        }
        if (!needsSwitch(now)) {
            return false;
        }
        if (beforeOpen != null) {
            beforeOpen.accept(now);
        }
        started(now);
        return true;
    }

    /** 切り替わりの実行。鍵が取れなければ（保存先なし）停止として扱う。 */
    public void started(SessionKey key) {
        if (key == null) {
            stopped();
            return;
        }
        store.open(key.file());
        current = key;
    }

    /** 停止（切断・ワールドから出た）。書き切って手持ちを捨て、リスナーへ通知する。 */
    public void stopped() {
        store.close();
        current = null;
    }

    /** 切断・ゲーム終了。予約分を書き切るだけで、手持ちは残す。 */
    public void flush() {
        store.flush();
    }
}
