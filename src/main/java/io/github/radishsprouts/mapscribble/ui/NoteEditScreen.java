package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * 文字入力・編集の画面（画面設計書 3.2）。背後に親の画面（全画面マップ）を描き、黒 50% の幕とパネルを重ねる。
 * 確定・キャンセル・削除の結果は {@link Callbacks} へ渡し、呼び出し側（{@link ScribbleUi}）がメモの追加・変更・削除を行う。
 * 閉じたら親の画面へ戻る。
 */
public final class NoteEditScreen extends Screen {
    /** 画面を開く時の値。 */
    public record Spec(MemoKind kind, boolean edit, String text, int color, FillLevel fill, TextSize size,
                       boolean onMinimap) {
    }

    /** 確定した値。 */
    public record Result(String text, int color, FillLevel fill, TextSize size, boolean onMinimap) {
    }

    /** 画面の結果の受け取り先。 */
    public interface Callbacks {
        void confirm(Result result);

        void cancel();

        /** 編集の時だけ呼ばれる。 */
        void delete();
    }

    static final int MAX_CHARS = 200;
    private static final int CURTAIN = 0x80000000;
    private static final int PANEL_BG = 0xF0202428;
    private static final int PANEL_EDGE = 0xFF50565E;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_LABEL = 0xFFE0E0E0;
    private static final int TEXT_GRAY = 0xFFA0A0A0;
    private static final int TEXT_ERROR = 0xFFFF8080;

    private final Screen parent;
    private final Spec spec;
    private final Callbacks callbacks;

    // 画面を作り直しても（ウィンドウの大きさ変更）値を失わないよう、部品とは別に持つ
    private String text;
    private int colorRgb;
    private FillLevel fill;
    private TextSize size;
    private boolean onMinimap;

    private EditLayout layout;
    private MultiLineEditBox box;
    private Button saveButton;
    /** 親の画面を描けなかった時は、以後バニラの背景に切り替える。 */
    private boolean parentFailed;

    public NoteEditScreen(Screen parent, Spec spec, Callbacks callbacks) {
        super(Component.translatable(titleKey(spec)));
        this.parent = parent;
        this.spec = spec;
        this.callbacks = callbacks;
        this.text = spec.text();
        this.colorRgb = spec.color() & 0xFFFFFF;
        this.fill = spec.fill();
        this.size = spec.size();
        this.onMinimap = spec.onMinimap();
    }

    static String titleKey(Spec spec) {
        String kind = spec.kind().name().toLowerCase(Locale.ROOT);
        // 囲み（REGION）の翻訳キーは設計書で enclose
        if (spec.kind() == MemoKind.REGION) {
            kind = "enclose";
        }
        return "mapscribble.screen.title." + (spec.edit() ? "edit." : "") + kind;
    }

    @Override
    protected void init() {
        layout = new EditLayout(spec.kind(), spec.edit(), height);
        int px = layout.panelX(width);
        int py = layout.panelY();

        box = MultiLineEditBox.builder()
                .setX(px + EditLayout.MARGIN)
                .setY(py + layout.boxY())
                .build(font, EditLayout.BOX_WIDTH, layout.boxHeight(), Component.translatable("mapscribble.screen.label.text"));
        box.setCharacterLimit(MAX_CHARS);
        box.setValueListener(value -> {
            text = value;
            updateSaveButton();
        });
        box.setValue(text);
        addRenderableWidget(box);

        addRenderableWidget(new ColorSwatchRow(px + EditLayout.MARGIN, py + layout.swatchY(), colorRgb,
                rgb -> colorRgb = rgb));

        int row1 = py + layout.cycleRow1Y();
        int left = px + EditLayout.CYCLE_LEFT_X;
        int right = px + EditLayout.CYCLE_RIGHT_X;
        if (layout.hasFill()) {
            addRenderableWidget(CycleButton.<FillLevel>builder(l -> Component.translatable(l.translationKey()), fill)
                    .withValues(FillLevel.values())
                    .create(left, row1, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                            Component.translatable("mapscribble.screen.fill"), (b, v) -> fill = v));
            addRenderableWidget(sizeButton(right, row1));
            addRenderableWidget(minimapButton(left, py + layout.cycleRow2Y()));
        } else {
            addRenderableWidget(sizeButton(left, row1));
            addRenderableWidget(minimapButton(right, row1));
        }

        int buttonsY = py + layout.buttonsY();
        if (spec.edit()) {
            addRenderableWidget(Button.builder(Component.translatable("mapscribble.screen.delete").withColor(0xFF5555),
                            b -> delete())
                    .bounds(px + layout.deleteX(), buttonsY, layout.deleteWidth(), EditLayout.BUTTON_HEIGHT).build());
        }
        saveButton = addRenderableWidget(Button.builder(Component.translatable("mapscribble.screen.save"), b -> confirm())
                .bounds(px + layout.saveX(), buttonsY, layout.footerButtonWidth(), EditLayout.BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> cancel())
                .bounds(px + layout.cancelX(), buttonsY, layout.footerButtonWidth(), EditLayout.BUTTON_HEIGHT).build());

        updateSaveButton();
        setInitialFocus(box);
    }

    private CycleButton<TextSize> sizeButton(int x, int y) {
        return CycleButton.<TextSize>builder(s -> Component.translatable(s.translationKey()), size)
                .withValues(TextSize.values())
                .create(x, y, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                        Component.translatable("mapscribble.screen.size"), (b, v) -> size = v);
    }

    private CycleButton<Boolean> minimapButton(int x, int y) {
        return CycleButton.<Boolean>builder(
                        shown -> Component.translatable(shown ? "mapscribble.minimap.shown" : "mapscribble.minimap.hidden"),
                        onMinimap)
                .withValues(Arrays.asList(Boolean.TRUE, Boolean.FALSE))
                .create(x, y, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                        Component.translatable("mapscribble.screen.minimap"), (b, v) -> onMinimap = v);
    }

    // ---- 確定・取り消し ----

    /** 文字のメモは文字が要る（空白だけも不可）。囲み・線は空でも確定できる。 */
    boolean canConfirm() {
        return spec.kind() != MemoKind.TEXT || !text.isBlank();
    }

    private void updateSaveButton() {
        if (saveButton != null) {
            saveButton.active = canConfirm();
        }
    }

    private void confirm() {
        if (!canConfirm()) {
            return;
        }
        callbacks.confirm(new Result(text.isBlank() ? "" : text, colorRgb, fill, size, onMinimap));
        close();
    }

    private void cancel() {
        callbacks.cancel();
        close();
    }

    private void delete() {
        callbacks.delete();
        close();
    }

    private void close() {
        ScreenCompat.setScreen(parent);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && GuiCompat.isControlDown(event)) {
            confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void resize(int newWidth, int newHeight) {
        // 背後の親の画面も、新しい大きさに合わせておく
        parent.resize(newWidth, newHeight);
        super.resize(newWidth, newHeight);
    }

    // ---- 描画 ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!renderParent(g, partialTick)) {
            super.extractBackground(g, mouseX, mouseY, partialTick);
        }
        g.fill(0, 0, width, height, CURTAIN);
        int px = layout.panelX(width);
        int py = layout.panelY();
        g.fill(px, py, px + EditLayout.PANEL_WIDTH, py + layout.panelHeight(), PANEL_BG);
        g.outline(px, py, EditLayout.PANEL_WIDTH, layout.panelHeight(), PANEL_EDGE);
    }

    /** 親の画面を、マウスの位置に画面外を渡して（ホバー表示を出さずに）描く。描けなければ false。 */
    private boolean renderParent(GuiGraphicsExtractor g, float partialTick) {
        if (parentFailed) {
            return false;
        }
        try {
            parent.extractRenderState(g, -1, -1, partialTick);
            return true;
        } catch (RuntimeException e) {
            parentFailed = true;
            MapScribble.LOGGER.warn("MapScribble: 親の全画面マップを背後に描けなかったため、バニラの背景に切り替えます", e);
            return false;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int px = layout.panelX(width);
        int py = layout.panelY();
        int left = px + EditLayout.MARGIN;

        g.centeredText(font, title, px + EditLayout.PANEL_WIDTH / 2, py + layout.titleY(), TEXT_WHITE);
        g.text(font, Component.translatable("mapscribble.screen.label.text"), left, py + layout.labelY(), TEXT_LABEL, false);

        int hintY = py + layout.hintY();
        if (spec.kind() == MemoKind.TEXT && text.isBlank()) {
            g.text(font, Component.translatable("mapscribble.screen.error.text_required"), left, hintY, TEXT_ERROR, false);
        } else {
            g.text(font, Component.translatable("mapscribble.screen.hint.keys"), left, hintY, TEXT_GRAY, false);
        }
        Component counter = Component.translatable("mapscribble.screen.counter", text.length(), MAX_CHARS);
        g.text(font, counter, left + EditLayout.BOX_WIDTH - font.width(counter), hintY, TEXT_GRAY, false);

        g.text(font, Component.translatable("mapscribble.screen.label.color", colorName()), left,
                py + layout.colorLabelY(), TEXT_LABEL, false);
    }

    private Component colorName() {
        NoteColor c = NoteColor.of(colorRgb);
        if (c != null) {
            return Component.translatable(c.translationKey());
        }
        return Component.literal(String.format(Locale.ROOT, "#%06X", colorRgb));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 検証用: 今の文字の欄の値。 */
    public @Nullable String currentText() {
        return box == null ? null : box.getValue();
    }
}
