package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.network.chat.Component;

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

        void delete();
    }

    private static final int MAX_CHARS = 1000;
    private static final int CURTAIN = 0x80000000;
    private static final int PANEL_BG = 0xEE181818;
    private static final int PANEL_EDGE = 0xFF555555;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFA0A0A0;
    private static final int TEXT_ERROR = 0xFFFF5555;
    private static final int TEXT_LABEL = 0xFFDDDDDD;

    private final Screen parent;
    private final Spec spec;
    private final Callbacks callbacks;

    private EditLayout layout;
    private MultiLineEditBox box;
    private Button saveButton;
    private boolean parentFailed;

    private String text;
    private int colorRgb;
    private FillLevel fill;
    private TextSize size;
    private boolean onMinimap;

    public NoteEditScreen(Screen parent, Spec spec, Callbacks callbacks) {
        super(Component.translatable(spec.edit()
                ? "mapscribble.screen.title.edit"
                : (spec.kind() == MemoKind.TEXT
                ? "mapscribble.screen.title.new_text"
                : (spec.kind() == MemoKind.REGION ? "mapscribble.screen.title.new_enclose" : "mapscribble.screen.title.new_line"))));
        this.parent = parent;
        this.spec = spec;
        this.callbacks = callbacks;
        this.text = spec.text() == null ? "" : spec.text();
        this.colorRgb = spec.color();
        this.fill = spec.fill();
        this.size = spec.size();
        this.onMinimap = spec.onMinimap();
    }

    @Override
    protected void init() {
        layout = new EditLayout(spec.kind(), spec.edit(), height);
        int px = layout.panelX(width);
        int py = layout.panelY();

        //? if >=1.21.11 {
        box = MultiLineEditBox.builder()
                .setX(px + EditLayout.MARGIN)
                .setY(py + layout.boxY())
                .build(font, EditLayout.BOX_WIDTH, layout.boxHeight(), Component.translatable("mapscribble.screen.label.text"));
        //?} else {
        /*box = new MultiLineEditBox(font, px + EditLayout.MARGIN, py + layout.boxY(), EditLayout.BOX_WIDTH, layout.boxHeight(), Component.empty(), Component.translatable("mapscribble.screen.label.text"));
        *///?}
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
            //? if >=1.21.11 {
            addRenderableWidget(CycleButton.<FillLevel>builder(l -> Component.translatable(l.translationKey()), fill)
                    .withValues(FillLevel.values())
                    .create(left, row1, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                            Component.translatable("mapscribble.screen.fill"), (b, v) -> fill = v));
            //?} else {
            /*addRenderableWidget(CycleButton.<FillLevel>builder(l -> Component.translatable(l.translationKey()))
                    .withValues(FillLevel.values())
                    .withInitialValue(fill)
                    .create(left, row1, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                            Component.translatable("mapscribble.screen.fill"), (b, v) -> fill = v));
            *///?}
            addRenderableWidget(sizeButton(right, row1));
            addRenderableWidget(minimapButton(left, py + layout.cycleRow2Y()));
        } else {
            addRenderableWidget(sizeButton(left, row1));
            addRenderableWidget(minimapButton(right, row1));
        }

        int buttonsY = py + layout.buttonsY();
        if (spec.edit()) {
            addRenderableWidget(Button.builder(GuiCompat.withColor(Component.translatable("mapscribble.screen.delete"), 0xFF5555),
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
        //? if >=1.21.11 {
        return CycleButton.<TextSize>builder(s -> Component.translatable(s.translationKey()), size)
                .withValues(TextSize.values())
                .create(x, y, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                        Component.translatable("mapscribble.screen.size"), (b, v) -> size = v);
        //?} else {
        /*return CycleButton.<TextSize>builder(s -> Component.translatable(s.translationKey()))
                .withValues(TextSize.values())
                .withInitialValue(size)
                .create(x, y, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                        Component.translatable("mapscribble.screen.size"), (b, v) -> size = v);
        *///?}
    }

    private CycleButton<Boolean> minimapButton(int x, int y) {
        //? if >=1.21.11 {
        return CycleButton.<Boolean>builder(
                        shown -> Component.translatable(shown ? "mapscribble.minimap.shown" : "mapscribble.minimap.hidden"),
                        onMinimap)
                .withValues(Arrays.asList(Boolean.TRUE, Boolean.FALSE))
                .create(x, y, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                        Component.translatable("mapscribble.screen.minimap"), (b, v) -> onMinimap = v);
        //?} else {
        /*return CycleButton.<Boolean>builder(
                        shown -> Component.translatable(shown ? "mapscribble.minimap.shown" : "mapscribble.minimap.hidden"))
                .withValues(Arrays.asList(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(onMinimap)
                .create(x, y, EditLayout.CYCLE_WIDTH, EditLayout.BUTTON_HEIGHT,
                        Component.translatable("mapscribble.screen.minimap"), (b, v) -> onMinimap = v);
        *///?}
    }

    // ---- 確定・取り消し ----

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
        callbacks.confirm(new Result(text, colorRgb, fill, size, onMinimap));
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

    @Override
    public void onClose() {
        cancel();
    }

    private void close() {
        ScreenCompat.setScreen(parent);
    }

    //? if >=1.21.11 {
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && GuiCompat.isControlDown(event)) {
            confirm();
            return true;
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && GuiCompat.isControlDown(modifiers)) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}

    //? if >=1.21.11 {
    @Override
    public void resize(int newWidth, int newHeight) {
        parent.resize(newWidth, newHeight);
        super.resize(newWidth, newHeight);
    }
    //?} else {
    /*@Override
    public void resize(Minecraft mc, int newWidth, int newHeight) {
        parent.resize(mc, newWidth, newHeight);
        super.resize(mc, newWidth, newHeight);
    }
    *///?}

    // ---- 描画 ----

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        renderCurtainAndPanelBg(g, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        renderPanelForeground(g);
    }
    //?} elif >=1.21 {
    /*@Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderCurtainAndPanelBg(g, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderPanelForeground(g);
    }
    *///?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        renderCurtainAndPanelBg(g, 0f);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderPanelForeground(g);
    }
    *///?}

    //? if >=26.1 {
    private void renderCurtainAndPanelBg(GuiGraphicsExtractor g, float partialTick) {
    //?} else {
    /*private void renderCurtainAndPanelBg(GuiGraphics g, float partialTick) {
    *///?}
        if (!renderParent(g, partialTick)) {
            //? if >=26.1 {
            super.extractBackground(g, -1, -1, partialTick);
            //?} elif >=1.21 {
            /*super.renderBackground(g, -1, -1, partialTick);*/
            //?} else {
            /*super.renderBackground(g);*/
            //?}
        }
        GuiCompat.nextLayer(g);
        GuiCompat.fill(g, 0, 0, width, height, CURTAIN);
        int px = layout.panelX(width);
        int py = layout.panelY();
        GuiCompat.fill(g, px, py, px + EditLayout.PANEL_WIDTH, py + layout.panelHeight(), PANEL_BG);
        GuiCompat.outline(g, px, py, EditLayout.PANEL_WIDTH, layout.panelHeight(), PANEL_EDGE);
    }

    //? if >=26.1 {
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
    //?} else {
    /*private boolean renderParent(GuiGraphics g, float partialTick) {
        if (parentFailed) {
            return false;
        }
        try {
            parent.render(g, -1, -1, partialTick);
            return true;
        } catch (RuntimeException e) {
            parentFailed = true;
            MapScribble.LOGGER.warn("MapScribble: 親の全画面マップを背後に描けなかったため、バニラの背景に切り替えます", e);
            return false;
        }
    }
    *///?}

    //? if >=26.1 {
    private void renderPanelForeground(GuiGraphicsExtractor g) {
    //?} else {
    /*private void renderPanelForeground(GuiGraphics g) {
    *///?}
        int px = layout.panelX(width);
        int py = layout.panelY();
        int left = px + EditLayout.MARGIN;

        GuiCompat.centeredText(g, font, title, px + EditLayout.PANEL_WIDTH / 2, py + layout.titleY(), TEXT_WHITE, false);
        GuiCompat.text(g, font, Component.translatable("mapscribble.screen.label.text"), left, py + layout.labelY(), TEXT_LABEL, false);

        int hintY = py + layout.counterY();
        if (spec.kind() == MemoKind.TEXT && text.isBlank()) {
            GuiCompat.text(g, font, Component.translatable("mapscribble.screen.error.text_required"), left, hintY, TEXT_ERROR, false);
        } else {
            GuiCompat.text(g, font, Component.translatable("mapscribble.screen.hint.keys"), left, hintY, TEXT_GRAY, false);
        }

        GuiCompat.text(g, font, Component.translatable("mapscribble.screen.label.color", colorName()), left,
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

    public String currentText() {
        return box == null ? null : box.getValue();
    }
}
