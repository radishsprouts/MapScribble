package io.github.radishsprouts.mapscribble.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.compat.Gfx;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

/**
 * メモの保存先の画面の、メモ帳の一覧（設計書 3.7「一覧の行」）。行の高さ 14。クリックでその行に切り替える。
 * スクロールはホイールと ↑↓ で行う。行の見た目・位置は {@link NotebookLayout} の値に合わせる。
 */
public final class NotebookList extends AbstractWidget {
    /**
     * 一覧の 1 行。件数・日付は表示用の文字列で受け取る。name は表示名（名前が空なら「（名前なし）」）、
     * untitled はその名前が空だったことの印（灰色で描く。使用中の行は #E0E0E0）。
     */
    public record Row(String id, String name, String count, String date, boolean inUse, boolean untitled) {
    }

    private static final int FACE_HOVER = 0x1FFFFFFF;
    private static final int FACE_IN_USE = 0xFF1F5FA8;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int GRAY_HOVER = 0xFFC0C0C0;
    private static final int GRAY_IN_USE = 0xFFE0E0E0;
    private static final int GRAY_EMPTY = 0xFFA0A0A0;
    private static final int SCROLLBAR = 0x80FFFFFF;
    private static final int EDGE = 0xFF50565E;

    private final Font font;
    private final int visibleRows;
    private final Consumer<String> onPick;

    private List<Row> rows = List.of();
    private Component emptyLine1;
    private Component emptyLine2;
    private boolean pickable = true;
    /** 分けていない接続先の、押せない灰色の行（ホバーの面も出さない）。pickable とは別に持つ。 */
    private boolean dimmed;
    private boolean renamingInUse;
    private int scroll;
    private int keySelected = -1;
    private final String inUseLabel;

    public NotebookList(Font font, int x, int y, int width, int visibleRows, Consumer<String> onPick) {
        super(x, y, width, visibleRows * NotebookLayout.ROW_HEIGHT + 2,
                Component.translatable("mapscribble.notebook.list"));
        this.font = font;
        this.visibleRows = visibleRows;
        this.onPick = onPick;
        this.inUseLabel = "［" + Component.translatable("mapscribble.notebook.in_use").getString() + "］";
    }

    /** 一覧の中身を入れ替える。行が減ったら、スクロールとキーの選択を範囲内へ戻す。 */
    public void setRows(List<Row> newRows) {
        this.rows = newRows;
        clampScroll();
        if (keySelected >= rows.size()) {
            keySelected = rows.isEmpty() ? -1 : rows.size() - 1;
        }
    }

    /** 行が無い時に中に出す灰色の 2 行（無ければ null）。 */
    public void setEmptyLines(Component line1, Component line2) {
        this.emptyLine1 = line1;
        this.emptyLine2 = line2;
    }

    /** 行を押して切り替えられるか。 */
    public void setPickable(boolean pickable) {
        this.pickable = pickable;
    }

    /** 行を灰色にして押せない見た目にするか（分けていない接続先）。真なら名前・件数・日付が灰色で、ホバーの面も出ない。 */
    public void setDimmed(boolean dimmed) {
        this.dimmed = dimmed;
    }

    /** 名前を変えている間は、使用中の行の名前を描かない（上に入力欄が重なる）。 */
    public void setRenamingInUse(boolean renaming) {
        this.renamingInUse = renaming;
    }

    /** 使用中の行が見えるまでスクロールする（新しいメモ帳を作った時など）。 */
    public void scrollToInUse() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).inUse()) {
                scrollTo(i);
                return;
            }
        }
    }

    /** 使用中の行の、一覧の左上からの位置と幅（名前を変える入力欄を重ねるため）。行が見えていなければ null。 */
    public int[] inUseRowBounds() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).inUse()) {
                int visible = i - scroll;
                if (visible < 0 || visible >= visibleRows) {
                    return null;
                }
                return new int[] {getX() + NotebookLayout.ROW_INNER_PAD,
                        getY() + NotebookLayout.LIST_PAD + visible * NotebookLayout.ROW_HEIGHT,
                        getWidth() - NotebookLayout.ROW_INNER_PAD * 2, NotebookLayout.ROW_HEIGHT};
            }
        }
        return null;
    }

    /** マウスの下の行の、省略していない全名（名前を「…」で切っている時だけ。それ以外は null）。 */
    public String truncatedNameAt(double mouseX, double mouseY) {
        int index = rowIndexAt(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        Row row = rows.get(index);
        String shown = nameShown(row);
        return shown.equals(fullName(row)) ? null : row.name();
    }

    private int rowIndexAt(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() + NotebookLayout.LIST_PAD) {
            return -1;
        }
        int visible = (int) ((mouseY - getY() - NotebookLayout.LIST_PAD) / NotebookLayout.ROW_HEIGHT);
        if (visible < 0 || visible >= visibleRows) {
            return -1;
        }
        int index = scroll + visible;
        return index < rows.size() ? index : -1;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows)));
    }

    private void scrollTo(int index) {
        if (index < scroll) {
            scroll = index;
        } else if (index >= scroll + visibleRows) {
            scroll = index - visibleRows + 1;
        }
        clampScroll();
    }

    private String fullName(Row row) {
        return row.inUse() ? row.name() + "　" + inUseLabel : row.name();
    }

    /**
     * 名前の幅（名前と［使用中］で、件数の左端 − 6 まで。最大 160）に収めた表示。
     * 収まらない時は名前の末尾を「…」で切る。列の位置は日付・件数の実際の幅から出す。
     */
    private String nameShown(Row row) {
        String suffix = row.inUse() ? "　" + inUseLabel : "";
        int maxWidth = NotebookLayout.nameMaxWidth(font.width(row.date()), font.width(row.count()));
        int room = maxWidth - font.width(suffix);
        String name = NotebookPanel.ellipsize(row.name(), Math.max(0, room), font::width);
        return name + suffix;
    }

    // ---- 描画 ----

    //? if >=26.1 {
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        renderList(Gfx.of(g), mouseX, mouseY);
    }
    //?} else {
    /*@Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderList(Gfx.of(g), mouseX, mouseY);
    }
    *///?}

    private void renderList(Gfx g, int mouseX, int mouseY) {
        // 一覧の枠
        GuiCompat.outline(g, getX(), getY(), getWidth(), getHeight(), isFocused() ? WHITE : EDGE);
        if (rows.isEmpty()) {
            int y = getY() + 8;
            if (emptyLine1 != null) {
                GuiCompat.centeredText(g, font, emptyLine1, getX() + getWidth() / 2, y, GRAY_EMPTY, false);
            }
            if (emptyLine2 != null) {
                GuiCompat.centeredText(g, font, emptyLine2, getX() + getWidth() / 2, y + 14, GRAY_EMPTY, false);
            }
            return;
        }
        boolean scrollbar = rows.size() > visibleRows;
        int hovered = pickable && !dimmed ? rowIndexAt(mouseX, mouseY) : -1;
        for (int v = 0; v < visibleRows; v++) {
            int index = scroll + v;
            if (index >= rows.size()) {
                break;
            }
            drawRow(g, rows.get(index), v, index == hovered, !dimmed && isFocused() && index == keySelected);
        }
        if (scrollbar) {
            int trackH = visibleRows * NotebookLayout.ROW_HEIGHT;
            int barH = Math.max(6, trackH * visibleRows / rows.size());
            int maxScroll = rows.size() - visibleRows;
            int barY = getY() + NotebookLayout.LIST_PAD + (trackH - barH) * scroll / Math.max(1, maxScroll);
            int barX = getX() + NotebookLayout.ROW_INNER_PAD + NotebookLayout.SCROLLBAR_X;
            GuiCompat.fill(g, barX, barY, barX + NotebookLayout.SCROLLBAR_WIDTH, barY + barH, SCROLLBAR);
        }
    }

    private void drawRow(Gfx g, Row row, int visibleIndex, boolean hovered, boolean keyed) {
        int x = getX() + NotebookLayout.ROW_INNER_PAD;
        int y = getY() + NotebookLayout.LIST_PAD + visibleIndex * NotebookLayout.ROW_HEIGHT;
        int w = getWidth() - NotebookLayout.ROW_INNER_PAD * 2;
        int h = NotebookLayout.ROW_HEIGHT;
        if (row.inUse()) {
            GuiCompat.fill(g, x, y, x + w, y + h, FACE_IN_USE);
            GuiCompat.outline(g, x, y, w, h, WHITE);
        } else if (hovered || keyed) {
            GuiCompat.fill(g, x, y, x + w, y + h, FACE_HOVER);
            if (keyed) {
                GuiCompat.outline(g, x, y, w, h, WHITE);
            }
        }
        int textY = y + (h - font.lineHeight) / 2 + 1;
        if (!(row.inUse() && renamingInUse)) {
            int nameColor = dimmed ? GRAY
                    : (row.untitled() ? (row.inUse() ? GRAY_IN_USE : GRAY) : WHITE);
            GuiCompat.text(g, font, Component.literal(nameShown(row)), x + NotebookLayout.NAME_X, textY, nameColor,
                    !dimmed && !row.untitled());
        }
        int sub = dimmed ? GRAY : (row.inUse() ? GRAY_IN_USE : (hovered || keyed ? GRAY_HOVER : GRAY));
        // 列の位置は日付の右端（274）から逆算する。件数の右端 = 日付の左端 − 8
        int dateWidth = font.width(row.date());
        int countWidth = font.width(row.count());
        GuiCompat.text(g, font, Component.literal(row.date()), x + NotebookLayout.DATE_RIGHT_X - dateWidth, textY, sub,
                false);
        GuiCompat.text(g, font, Component.literal(row.count()), x + NotebookLayout.countRightX(dateWidth) - countWidth,
                textY, sub, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    // ---- 入力 ----

    private void pick(int index) {
        if (pickable && index >= 0 && index < rows.size()) {
            onPick.accept(rows.get(index).id());
        }
    }

    //? if >=1.21.11 {
    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        pick(rowIndexAt(event.x(), event.y()));
    }
    //?} else {
    /*@Override
    public void onClick(double mouseX, double mouseY) {
        pick(rowIndexAt(mouseX, mouseY));
    }
    *///?}

    /** ホイールでのスクロール。行が表示の数以下なら何もしない。 */
    private boolean scrollBy(double amount) {
        if (rows.size() <= visibleRows || amount == 0) {
            return false;
        }
        scroll += amount > 0 ? -1 : 1;
        clampScroll();
        return true;
    }

    //? if >=1.20.5 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return isMouseOver(mouseX, mouseY) && scrollBy(scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isMouseOver(mouseX, mouseY) && scrollBy(delta);
    }
    *///?}

    /** ↑↓ で選ぶ行を移し、Enter で切り替える。処理したら true。 */
    private boolean handleKey(int keyCode) {
        if (!isFocused() || rows.isEmpty()) {
            return false;
        }
        if (keyCode == InputConstants.KEY_DOWN) {
            keySelected = Math.min(rows.size() - 1, keySelected + 1);
            scrollTo(keySelected);
            return true;
        }
        if (keyCode == InputConstants.KEY_UP) {
            keySelected = Math.max(0, keySelected < 0 ? 0 : keySelected - 1);
            scrollTo(keySelected);
            return true;
        }
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) && keySelected >= 0) {
            pick(keySelected);
            return true;
        }
        return false;
    }

    //? if >=1.21.11 {
    @Override
    public boolean keyPressed(KeyEvent event) {
        return handleKey(event.key());
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return handleKey(keyCode);
    }
    *///?}
}
