package io.github.radishsprouts.mapscribble.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.draw.Draft;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * 画面まわりの窓口。ツールの帯の描画と入力、文字入力の画面を開く処理、キー操作、最後に使った値の保存をまとめる。
 * JourneyMap のクラスは参照しない（つなぎは jm/ScribbleBridge）。Minecraft の主スレッドからだけ使う。
 *
 * <p>{@link DrawingState} の口へつなぐもの: 帯の当たり判定、一筆の確定後（文字入力の画面を開く）、
 * 編集ツールのクリック（編集の画面を開く）、一時のお知らせ。
 */
public final class ScribbleUi {
    public static final String NOTICE_DISCARDED = "mapscribble.msg.discarded";

    private final DrawingController controller;
    private final DrawingState state;
    private final ToolBand band;
    private final NoticeTimer notice = new NoticeTimer();
    private final Path prefsFile;
    private String lastSavedPrefs;
    private boolean loadFailed;
    private boolean saveFailed;

    private Memo tooltipMemo;
    private Tool tooltipTool;
    private List<Component> memoTooltip = List.of();

    public ScribbleUi(DrawingController controller, Path prefsFile) {
        this.controller = controller;
        this.state = controller.state();
        this.prefsFile = prefsFile;
        this.band = new ToolBand(state, controller);

        UiPrefs.load(prefsFile, state);
        this.lastSavedPrefs = UiPrefs.toJson(state);

        state.setBandHitTest((x, y) -> state.isModeOn() && BandLayout.contains(x, y));
        state.setNoticeHandler(key -> notice.push(key, nowMs()));
        state.setDraftHandler(this::openNew);
        state.setEditHandler(this::openEdit);
        state.addChangeListener(this::onStateChanged);
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /** メモのファイルを読めなかった（ストアからの通知）。モードを抜けるか、メモが使える状態になるまで説明の行に出す。 */
    public void onLoadFailed() {
        loadFailed = true;
    }

    /** メモの保存に失敗した（true）／失敗の後で保存できた（false）。次に保存が成功するまで説明の行に出す。 */
    public void onSaveStateChanged(boolean failed) {
        saveFailed = failed;
    }

    private void onStateChanged() {
        if (!state.isModeOn()) {
            controller.cancelStroke();
            loadFailed = false;
            band.release();
        }
        persistIfChanged();
    }

    private void persistIfChanged() {
        String json = UiPrefs.toJson(state);
        if (!json.equals(lastSavedPrefs)) {
            lastSavedPrefs = json;
            UiPrefs.save(prefsFile, state);
        }
    }

    // ---- 描画 ----

    /**
     * 全画面マップの描画イベントの中で呼ぶ。手書きモードでなければ何もしない。
     *
     * @param hoverMemo 消しゴム・編集でカーソルの下にあるメモ（無ければ null）
     */
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, Memo hoverMemo) {
        if (!state.isModeOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = nowMs();
        MemoRepository repo = controller.repository();
        if (repo != null) {
            loadFailed = false;
        }
        String noticeKey = notice.activeKey(now);
        HintResolver.Line hint = HintResolver.resolve(new HintResolver.Input(
                loadFailed, saveFailed, repo == null && !loadFailed, noticeKey, !state.isVisible(),
                repo != null && repo.memos().isEmpty() && state.tool() == Tool.ENCLOSE, state.tool()));
        float alpha = noticeKey != null && noticeKey.equals(hint.translationKey()) ? notice.alpha(now) : 1f;
        band.render(g, mc.font, mouseX, mouseY, hint, alpha);

        if (hoverMemo != null && !BandLayout.contains(mouseX, mouseY)) {
            if (hoverMemo != tooltipMemo || state.tool() != tooltipTool) {
                tooltipMemo = hoverMemo;
                tooltipTool = state.tool();
                memoTooltip = List.of(Component.translatable(
                        state.tool() == Tool.ERASER ? "mapscribble.hover.erase" : "mapscribble.hover.edit",
                        describe(hoverMemo)));
            }
            g.setComponentTooltipForNextFrame(mc.font, memoTooltip, mouseX, mouseY);
        }
    }

    /** ツールチップに出すメモの説明（文字の 1 行目。文字が無ければ「文字なしの囲み／線」）。 */
    static Component describe(Memo memo) {
        String text = memo.text() == null ? "" : memo.text().strip();
        int newline = text.indexOf('\n');
        String first = newline < 0 ? text : text.substring(0, newline).strip();
        if (!first.isEmpty()) {
            return Component.literal(first);
        }
        return Component.translatable(memo.kind() == MemoKind.REGION
                ? "mapscribble.note.untitled.enclose" : "mapscribble.note.untitled.line");
    }

    // ---- 入力 ----

    /** 帯へのクリック。帯の上だったら true（地図へ渡さない）。 */
    public boolean onMouseClick(double guiX, double guiY, boolean leftButton) {
        return state.isModeOn() && band.click(guiX, guiY, leftButton);
    }

    public void onMouseRelease() {
        band.release();
    }

    /** 全画面マップでのキー。扱ったら true（地図へ渡さない）。 */
    public boolean onKey(KeyEvent event) {
        if (ScribbleKeys.isToggle(event)) {
            state.toggleMode();
            return true;
        }
        if (!state.isModeOn()) {
            return false;
        }
        Tool tool = ScribbleKeys.toolFor(event);
        if (tool != null) {
            state.setTool(tool);
            return true;
        }
        if (!controller.isActive() || !GuiCompat.isControlDown(event)) {
            return false;
        }
        if (event.key() == InputConstants.KEY_Z) {
            if (event.hasShiftDown()) {
                controller.redo();
            } else {
                controller.undo();
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_Y) {
            controller.redo();
            return true;
        }
        return false;
    }

    // ---- 文字入力・編集の画面 ----

    private void openNew(Draft draft) {
        Screen parent = ScreenCompat.currentScreen();
        if (parent == null) {
            return;
        }
        NoteEditScreen.Spec spec = new NoteEditScreen.Spec(draft.kind(), false, "", draft.color(),
                FillLevel.nearest(draft.fillOpacity()), TextSize.nearest(draft.textScale()), draft.onMinimap());
        ScreenCompat.setScreen(new NoteEditScreen(parent, spec, new NoteEditScreen.Callbacks() {
            @Override
            public void confirm(NoteEditScreen.Result r) {
                float fill = draft.kind() == MemoKind.REGION ? r.fill().opacity() : draft.fillOpacity();
                Draft edited = new Draft(draft.kind(), draft.points(), r.color(), draft.width(), fill,
                        r.size().scale(), r.onMinimap());
                controller.add(edited.toMemo(r.text()));
                // 最後に使った値を、次の新しいメモの既定にする
                state.setColor(r.color());
                if (draft.kind() == MemoKind.REGION) {
                    state.setFillOpacity(r.fill().opacity());
                }
                state.setTextScale(r.size().scale());
                state.setOnMinimap(r.onMinimap());
                persistIfChanged();
            }

            @Override
            public void cancel() {
                state.notice(NOTICE_DISCARDED);
            }

            @Override
            public void delete() {
                // 新規には削除が無い
            }
        }));
    }

    private void openEdit(Memo memo) {
        Screen parent = ScreenCompat.currentScreen();
        if (parent == null) {
            return;
        }
        NoteEditScreen.Spec spec = new NoteEditScreen.Spec(memo.kind(), true, memo.text(), memo.color(),
                FillLevel.nearest(memo.fillOpacity()), TextSize.nearest(memo.textScale()), memo.onMinimap());
        ScreenCompat.setScreen(new NoteEditScreen(parent, spec, new NoteEditScreen.Callbacks() {
            @Override
            public void confirm(NoteEditScreen.Result r) {
                // 段階を触っていなければ、元の値を保つ（段階に無い値を勝手に丸めない）
                float fill = memo.kind() == MemoKind.REGION && r.fill() != FillLevel.nearest(memo.fillOpacity())
                        ? r.fill().opacity() : memo.fillOpacity();
                float scale = r.size() != TextSize.nearest(memo.textScale()) ? r.size().scale() : memo.textScale();
                Memo after = new Memo(memo.id(), memo.kind(), memo.points(), r.color(), memo.width(), fill, r.text(),
                        scale, r.onMinimap(), memo.created(), memo.updated());
                if (!after.equals(memo)) {
                    controller.update(memo, after);
                }
            }

            @Override
            public void cancel() {
                // 編集のキャンセルは何も変えない
            }

            @Override
            public void delete() {
                controller.remove(memo);
            }
        }));
    }
}
