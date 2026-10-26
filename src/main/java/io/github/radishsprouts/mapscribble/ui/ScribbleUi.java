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
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.network.chat.Component;

/**
 * 画面まわりの窓口。ツールの帯の描画と入力、文字入力の画面を開く処理、キー操作、最後に使った値の保存をまとめる。
 * 地図MODのクラスは直接参照しない。Minecraft の主スレッドからだけ使う。
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

        state.setBandHitTest((x, y) -> state.isModeOn() && BandLayout.contains(x, y, state.bandX(), state.bandY()));
        state.setNoticeHandler(key -> notice.push(key, nowMs()));
        state.setDraftHandler(this::openNew);
        state.setEditHandler(this::openEdit);
        state.addChangeListener(this::onStateChanged);
    }

    public ToolBand band() {
        return band;
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    public void onLoadFailed() {
        loadFailed = true;
    }

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

    //? if >=26.1 {
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, Memo hoverMemo) {
        renderInternal(g, mouseX, mouseY, hoverMemo);
    }
    //?} else {
    /*public void render(GuiGraphics g, int mouseX, int mouseY, Memo hoverMemo) {
        renderInternal(g, mouseX, mouseY, hoverMemo);
    }
    *///?}

    //? if >=26.1 {
    private void renderInternal(GuiGraphicsExtractor g, int mouseX, int mouseY, Memo hoverMemo) {
    //?} else {
    /*private void renderInternal(GuiGraphics g, int mouseX, int mouseY, Memo hoverMemo) {
    *///?}
        if (!state.isModeOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        band.drag(mouseX, mouseY, screenW, screenH);
        keepBandOnScreen(screenW, screenH);
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

        if (hoverMemo != null && !BandLayout.contains(mouseX, mouseY, state.bandX(), state.bandY())) {
            if (hoverMemo != tooltipMemo || state.tool() != tooltipTool) {
                tooltipMemo = hoverMemo;
                tooltipTool = state.tool();
                memoTooltip = List.of(Component.translatable(
                        state.tool() == Tool.ERASER ? "mapscribble.hover.erase" : "mapscribble.hover.edit",
                        describe(hoverMemo)));
            }
            GuiCompat.drawTooltip(g, mc.font, memoTooltip, mouseX, mouseY);
        }
    }

    /**
     * 帯が画面の外へはみ出さないようにする。帯の幅を広げた（終了ボタンの追加）ため、旧バージョンで保存した
     * 右寄りの位置や、画面を小さくした後の位置だと右端が切れて、出口の終了ボタンが見えなくなる。
     */
    private void keepBandOnScreen(int screenW, int screenH) {
        if (band.isDragging()) {
            return;
        }
        int x = Math.max(0, Math.min(Math.max(0, screenW - BandLayout.WIDTH), state.bandX()));
        int y = Math.max(0, Math.min(Math.max(0, screenH - BandLayout.HEIGHT), state.bandY()));
        if (x != state.bandX() || y != state.bandY()) {
            state.setBandPosition(x, y);
        }
    }

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

    public boolean onMouseClick(double guiX, double guiY, int button) {
        return state.isModeOn() && band.click(guiX, guiY, button == 0);
    }

    public boolean onMouseClick(double guiX, double guiY, boolean leftButton) {
        return state.isModeOn() && band.click(guiX, guiY, leftButton);
    }

    public boolean onMouseRelease(double guiX, double guiY, int button) {
        band.release();
        persistIfChanged();
        return true;
    }

    public void onMouseRelease() {
        band.release();
        persistIfChanged();
    }

    //? if >=1.21.11 {
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
    //?} else {
    /*public boolean onKey(int keyCode, int scanCode, int modifiers) {
        if (ScribbleKeys.isToggle(keyCode, scanCode)) {
            state.toggleMode();
            return true;
        }
        if (!state.isModeOn()) {
            return false;
        }
        Tool tool = ScribbleKeys.toolFor(keyCode, scanCode);
        if (tool != null) {
            state.setTool(tool);
            return true;
        }
        if (!controller.isActive() || !GuiCompat.isControlDown(modifiers)) {
            return false;
        }
        // GLFW_KEY_Z = 90, GLFW_KEY_Y = 89
        if (keyCode == 90) {
            if (GuiCompat.isShiftDown(modifiers)) {
                controller.redo();
            } else {
                controller.undo();
            }
            return true;
        }
        if (keyCode == 89) {
            controller.redo();
            return true;
        }
        return false;
    }
    *///?}

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
            }

            @Override
            public void delete() {
                controller.remove(memo);
            }
        }));
    }
}
