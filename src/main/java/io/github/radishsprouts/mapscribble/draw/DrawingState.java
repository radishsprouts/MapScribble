package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.model.Memo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 手書きモードの状態。画面（ツールの帯・文字入力の画面。作業6）がここを書き換え、
 * JourneyMap 連携（入力・描画）がここを読む。Minecraft の主スレッドからだけ使う。
 *
 * <p>外から差し込む口:
 * <ul>
 *   <li>{@link #setBandHitTest}: 「この GUI 座標はツールの帯の上か」。帯の上のクリック・ドラッグは地図へ渡さない</li>
 *   <li>{@link #setDraftHandler}: 一筆を確定した後の処理。既定は文字なしでメモを追加する</li>
 *   <li>{@link #setEditHandler}: 編集ツールでメモをクリックした時の処理（文字入力の画面を開く）</li>
 *   <li>{@link #setNoticeHandler}: 一時のお知らせ（翻訳キー。帯の説明の行に出す）</li>
 * </ul>
 */
public final class DrawingState {
    /** 画面（GUI）座標の点が帯の上かを答える。 */
    @FunctionalInterface
    public interface ScreenHit {
        boolean contains(double guiX, double guiY);
    }

    /** 画面設計書 3.4 の既定の色（赤）。 */
    public static final int DEFAULT_COLOR = 0xFF6E61;

    private boolean modeOn;
    private Tool tool = Tool.ENCLOSE;
    private int color = DEFAULT_COLOR;
    /** 線の太さの段階（1〜3）。既定は中。 */
    private int width = 2;
    private boolean visible = true;
    /** 新しいメモの既定（文字入力の画面が変えて、最後に使った値を覚える）。 */
    private float fillOpacity = 0.30f;
    private float textScale = 1.5f;
    private boolean onMinimap = true;

    private ScreenHit bandHitTest = (x, y) -> false;
    private Consumer<Draft> draftHandler;
    private Consumer<Memo> editHandler = memo -> {
    };
    private Consumer<String> noticeHandler = key -> {
    };
    private final List<Runnable> listeners = new ArrayList<>();

    private static DrawingState shared;

    /** MOD 全体で共有する状態。 */
    public static DrawingState shared() {
        if (shared == null) {
            shared = new DrawingState();
        }
        return shared;
    }

    public boolean isModeOn() {
        return modeOn;
    }

    public void setModeOn(boolean modeOn) {
        if (this.modeOn != modeOn) {
            this.modeOn = modeOn;
            changed();
        }
    }

    public void toggleMode() {
        setModeOn(!modeOn);
    }

    public Tool tool() {
        return tool;
    }

    public void setTool(Tool tool) {
        if (this.tool != tool) {
            this.tool = tool;
            changed();
        }
    }

    public int color() {
        return color;
    }

    public void setColor(int color) {
        int rgb = color & 0xFFFFFF;
        if (this.color != rgb) {
            this.color = rgb;
            changed();
        }
    }

    public int width() {
        return width;
    }

    public void setWidth(int width) {
        int clamped = Math.max(1, Math.min(3, width));
        if (this.width != clamped) {
            this.width = clamped;
            changed();
        }
    }

    /** 自分のメモを地図に出しているか（全体の切替）。 */
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            changed();
        }
    }

    public float fillOpacity() {
        return fillOpacity;
    }

    public void setFillOpacity(float fillOpacity) {
        this.fillOpacity = Math.max(0f, Math.min(1f, fillOpacity));
    }

    public float textScale() {
        return textScale;
    }

    public void setTextScale(float textScale) {
        this.textScale = textScale;
    }

    public boolean onMinimap() {
        return onMinimap;
    }

    public void setOnMinimap(boolean onMinimap) {
        this.onMinimap = onMinimap;
    }

    public void setBandHitTest(ScreenHit hit) {
        this.bandHitTest = hit == null ? (x, y) -> false : hit;
    }

    /** GUI 座標の点がツールの帯の上か。 */
    public boolean isOverBand(double guiX, double guiY) {
        return bandHitTest.contains(guiX, guiY);
    }

    /** 一筆を確定した後の処理を差し替える。null で既定（文字なしで追加）へ戻す。 */
    public void setDraftHandler(Consumer<Draft> handler) {
        this.draftHandler = handler;
    }

    /** 差し替えが無ければ null（その場合は {@link DrawingController} の既定の処理）。 */
    Consumer<Draft> draftHandler() {
        return draftHandler;
    }

    public void setEditHandler(Consumer<Memo> handler) {
        this.editHandler = handler == null ? memo -> {
        } : handler;
    }

    void requestEdit(Memo memo) {
        editHandler.accept(memo);
    }

    public void setNoticeHandler(Consumer<String> handler) {
        this.noticeHandler = handler == null ? key -> {
        } : handler;
    }

    /** 一時のお知らせを出す（翻訳キー）。 */
    public void notice(String translationKey) {
        noticeHandler.accept(translationKey);
    }

    /** モード・ツール・色・太さ・表示が変わった時に呼ばれる。 */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private void changed() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
