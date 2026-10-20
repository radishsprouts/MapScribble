package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * ツールの帯（画面設計書 3.1）。手書きモード中だけ、全画面マップの描画イベントの中で描く。
 * 配置は {@link BandLayout}（固定の座標・作ってある一覧）で、毎フレームの計算はマウスの位置から要素を引くだけ。
 * ホバー判定は、マウスが動いた時だけやり直す。画像は素材が揃うまで無い場合があり、その間は
 * Minecraft の「素材なし」の市松模様が出る（絵をコードで描いて埋めない）。
 */
public final class ToolBand {
    static final int BG = 0xCC101418;
    static final int EDGE = 0x33FFFFFF;
    static final int SEPARATOR = 0x40FFFFFF;
    static final int SELECTED_FACE = 0xFF2E7DD7;
    static final int WHITE = 0xFFFFFFFF;

    private static final int FACE_NORMAL = 0x26FFFFFF;
    private static final int FACE_HOVER = 0x4DFFFFFF;
    private static final int FACE_PRESSED = 0x73FFFFFF;
    private static final int FACE_DISABLED = 0x0FFFFFFF;
    private static final int BORDER_NORMAL = 0x73FFFFFF;
    private static final int BORDER_HOVER = 0xB3FFFFFF;
    private static final int BORDER_DISABLED = 0x26FFFFFF;
    private static final int ICON_NORMAL = 0xFFE6E6E6;
    private static final int ICON_DISABLED = 0x59E6E6E6;
    private static final int SWATCH_BORDER = 0x99000000;
    private static final int SWATCH_HOVER_RING = 0xFFA0A0A0;
    private static final int GRAY = 0xA0A0A0;

    private static final Identifier[] TOOL_ICONS = {
            GuiCompat.texture("textures/gui/tool/enclose.png"),
            GuiCompat.texture("textures/gui/tool/line.png"),
            GuiCompat.texture("textures/gui/tool/text.png"),
            GuiCompat.texture("textures/gui/tool/eraser.png"),
            GuiCompat.texture("textures/gui/tool/edit.png")};
    private static final Identifier UNDO_ICON = GuiCompat.texture("textures/gui/tool/undo.png");
    private static final Identifier REDO_ICON = GuiCompat.texture("textures/gui/tool/redo.png");
    private static final Identifier VISIBLE_ICON = GuiCompat.texture("textures/gui/tool/visible.png");
    private static final Identifier HIDDEN_ICON = GuiCompat.texture("textures/gui/tool/hidden.png");

    private final DrawingState state;
    private final DrawingController controller;

    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private BandLayout.Cell hover;
    private BandLayout.Cell pressed;

    private BandLayout.Cell tooltipCell;
    private boolean tooltipUndo;
    private boolean tooltipRedo;
    private boolean tooltipVisible;
    private String tooltipKeyLabel;
    private List<Component> tooltip = List.of();

    /** 説明の行の、はみ出しを切った文字列の使い回し。 */
    private String hintSource;
    private String hintShown;
    private boolean hintVisible;
    private boolean widthsChecked;

    public ToolBand(DrawingState state, DrawingController controller) {
        this.state = state;
        this.controller = controller;
    }

    // ---- 入力 ----

    /**
     * 帯の上のクリック。帯の上なら true を返し、地図へ渡さない。左ボタンだけが操作になる。
     *
     * @param gx GUI 座標
     */
    public boolean click(double gx, double gy, boolean leftButton) {
        if (!BandLayout.contains(gx, gy)) {
            return false;
        }
        if (!leftButton) {
            return true;
        }
        BandLayout.Cell cell = BandLayout.cellAt(gx, gy);
        if (cell == null || !isEnabled(cell)) {
            return true;
        }
        pressed = cell;
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
        switch (cell.kind()) {
            case TOOL -> state.setTool(Tool.values()[cell.index()]);
            case UNDO -> controller.undo();
            case REDO -> controller.redo();
            case VISIBILITY -> state.setVisible(!state.isVisible());
            case WIDTH -> state.setWidth(cell.index());
            case COLOR -> state.setColor(NoteColor.values()[cell.index()].rgb());
        }
        return true;
    }

    /** マウスのボタンを離した。押下中の見た目をやめる。 */
    public void release() {
        pressed = null;
    }

    private boolean isEnabled(BandLayout.Cell cell) {
        return switch (cell.kind()) {
            case UNDO -> controller.canUndo();
            case REDO -> controller.canRedo();
            default -> true;
        };
    }

    // ---- 描画 ----

    /**
     * @param hint      説明の行（文字列は翻訳キー）
     * @param hintAlpha 説明の行の不透明度（一時のお知らせの最後の 0.5 秒で下がる）
     */
    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, HintResolver.Line hint,
                       float hintAlpha) {
        updateHover(mouseX, mouseY);
        checkHintWidths(font);

        g.fill(BandLayout.X, BandLayout.Y, BandLayout.X + BandLayout.WIDTH, BandLayout.Y + BandLayout.HEIGHT, BG);
        g.outline(BandLayout.X, BandLayout.Y, BandLayout.WIDTH, BandLayout.HEIGHT, EDGE);
        for (BandLayout.Separator s : BandLayout.separators()) {
            g.fill(s.x(), s.y(), s.x() + s.w(), s.y() + s.h(), SEPARATOR);
        }

        int rgb = 0xFF000000 | state.color();
        for (BandLayout.Cell cell : BandLayout.cells()) {
            switch (cell.kind()) {
                case TOOL -> drawButton(g, cell, state.tool().ordinal() == cell.index(), true,
                        TOOL_ICONS[cell.index()]);
                case UNDO -> drawButton(g, cell, false, controller.canUndo(), UNDO_ICON);
                case REDO -> drawButton(g, cell, false, controller.canRedo(), REDO_ICON);
                case VISIBILITY -> drawButton(g, cell, false, true, state.isVisible() ? VISIBLE_ICON : HIDDEN_ICON);
                case WIDTH -> drawWidth(g, cell, rgb);
                case COLOR -> drawSwatch(g, cell);
            }
        }

        drawHint(g, font, hint, hintAlpha);
        showTooltip(g, font, mouseX, mouseY);
    }

    /** 開発環境だけ、最初の描画で帯に出る全文言の幅を 1 回測り、長すぎるものを警告に出す（本番では測らない）。 */
    private void checkHintWidths(Font font) {
        if (widthsChecked) {
            return;
        }
        widthsChecked = true;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        for (String key : HintFit.bandKeys()) {
            int width = font.width(I18n.get(key));
            if (HintFit.tooWide(width)) {
                MapScribble.LOGGER.warn("帯の説明が長すぎます key={} 幅={}（上限 {}、行の幅 {}）",
                        key, width, HintFit.WARN_WIDTH, BandLayout.HINT_MAX_WIDTH);
            }
        }
    }

    private void updateHover(int mouseX, int mouseY) {
        if (mouseX == lastMouseX && mouseY == lastMouseY) {
            return;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hover = BandLayout.cellAt(mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor g, BandLayout.Cell cell, boolean selected, boolean enabled,
                            Identifier icon) {
        int face = buttonFace(cell, selected, enabled);
        int border = buttonBorder(cell, selected, enabled);
        int tint = buttonIcon(cell, selected, enabled);
        g.fill(cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        g.outline(cell.x(), cell.y(), cell.w(), cell.h(), border);
        GuiCompat.blitIcon(g, icon, cell.x() + 2, cell.y() + 2, 16, tint);
    }

    private void drawWidth(GuiGraphicsExtractor g, BandLayout.Cell cell, int rgb) {
        boolean selected = state.width() == cell.index();
        int face = buttonFace(cell, selected, true);
        int border = buttonBorder(cell, selected, true);
        g.fill(cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        g.outline(cell.x(), cell.y(), cell.w(), cell.h(), border);
        // 長さ 12・太さ 1 / 2 / 3 の横棒を、選択中の色で塗る（画像は使わない）
        int thickness = cell.index();
        int top = cell.y() + (cell.h() - thickness) / 2;
        g.fill(cell.x() + 4, top, cell.x() + 16, top + thickness, rgb);
    }

    private void drawSwatch(GuiGraphicsExtractor g, BandLayout.Cell cell) {
        NoteColor color = NoteColor.list().get(cell.index());
        boolean selected = (state.color() & 0xFFFFFF) == color.rgb();
        boolean hovered = hover == cell;
        if (selected) {
            // 外側 1px の白い枠（隣との 1px の隙間に収まる）
            g.outline(cell.x() - 1, cell.y() - 1, cell.w() + 2, cell.h() + 2, WHITE);
        } else if (hovered) {
            g.outline(cell.x() - 1, cell.y() - 1, cell.w() + 2, cell.h() + 2, SWATCH_HOVER_RING);
        }
        g.fill(cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), 0xFF000000 | color.rgb());
        g.outline(cell.x(), cell.y(), cell.w(), cell.h(), selected ? 0xFF000000 : SWATCH_BORDER);
    }

    private int buttonFace(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return SELECTED_FACE;
        }
        if (!enabled) {
            return FACE_DISABLED;
        }
        if (pressed == cell && hover == cell) {
            return FACE_PRESSED;
        }
        return hover == cell ? FACE_HOVER : FACE_NORMAL;
    }

    private int buttonBorder(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return WHITE;
        }
        if (!enabled) {
            return BORDER_DISABLED;
        }
        if (pressed == cell && hover == cell) {
            return WHITE;
        }
        return hover == cell ? BORDER_HOVER : BORDER_NORMAL;
    }

    private int buttonIcon(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return WHITE;
        }
        if (!enabled) {
            return ICON_DISABLED;
        }
        return hover == cell ? WHITE : ICON_NORMAL;
    }

    private void drawHint(GuiGraphicsExtractor g, Font font, HintResolver.Line hint, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        hintVisible = a > 4;
        if (!hintVisible) {
            return;
        }
        String text = I18n.get(hint.translationKey());
        if (!text.equals(hintSource)) {
            hintSource = text;
            hintShown = fit(font, text);
        }
        g.text(font, hintShown, BandLayout.HINT_X, BandLayout.HINT_Y, (a << 24) | hint.rgb(), true);
    }

    /** 幅に収まらない時は、末尾を「…」で切る。 */
    private static String fit(Font font, String text) {
        if (font.width(text) <= BandLayout.HINT_MAX_WIDTH) {
            return text;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, BandLayout.HINT_MAX_WIDTH - font.width(ellipsis)) + ellipsis;
    }

    // ---- ツールチップ ----

    private void showTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        BandLayout.Cell cell = hover;
        if (cell == null) {
            if (hintVisible && HintFit.showFullText(hintSource, hintShown, mouseX, mouseY)) {
                g.setComponentTooltipForNextFrame(font, List.of(Component.literal(hintSource)), mouseX, mouseY);
            }
            return;
        }
        boolean undo = controller.canUndo();
        boolean redo = controller.canRedo();
        boolean visible = state.isVisible();
        // 道具キーを操作設定で変えた直後も古い名前を出さないよう、キーの表示名も作り直しの条件に入れる
        String keyLabel = cell.kind() == BandLayout.Kind.TOOL ? ScribbleKeys.toolKeyLabel(Tool.values()[cell.index()]) : null;
        if (cell != tooltipCell || undo != tooltipUndo || redo != tooltipRedo || visible != tooltipVisible
                || !java.util.Objects.equals(keyLabel, tooltipKeyLabel)) {
            tooltipKeyLabel = keyLabel;
            tooltipCell = cell;
            tooltipUndo = undo;
            tooltipRedo = redo;
            tooltipVisible = visible;
            tooltip = buildTooltip(cell, undo, redo, visible);
        }
        g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    private static List<Component> buildTooltip(BandLayout.Cell cell, boolean canUndo, boolean canRedo,
                                                boolean visible) {
        List<Component> lines = new ArrayList<>(2);
        switch (cell.kind()) {
            case TOOL -> {
                Tool tool = Tool.values()[cell.index()];
                String key = HintResolver.toolKey(tool);
                String keyLabel = ScribbleKeys.toolKeyLabel(tool);
                lines.add(keyLabel == null ? Component.translatable(key)
                        : Component.translatable("mapscribble.tool.tooltip", Component.translatable(key), keyLabel));
                lines.add(gray(Component.translatable(key + ".desc")));
            }
            case UNDO -> {
                lines.add(Component.translatable("mapscribble.undo"));
                if (!canUndo) {
                    lines.add(gray(Component.translatable("mapscribble.undo.none")));
                }
            }
            case REDO -> {
                lines.add(Component.translatable("mapscribble.redo"));
                if (!canRedo) {
                    lines.add(gray(Component.translatable("mapscribble.redo.none")));
                }
            }
            case VISIBILITY -> lines.add(Component.translatable(
                    visible ? "mapscribble.visibility.hide" : "mapscribble.visibility.show"));
            case WIDTH -> lines.add(Component.translatable(switch (cell.index()) {
                case 1 -> "mapscribble.width.thin";
                case 2 -> "mapscribble.width.medium";
                default -> "mapscribble.width.thick";
            }));
            case COLOR -> lines.add(Component.translatable("mapscribble.color.tooltip",
                    Component.translatable(NoteColor.values()[cell.index()].translationKey())));
        }
        return lines;
    }

    private static Component gray(Component c) {
        return c.copy().withColor(GRAY);
    }
}
