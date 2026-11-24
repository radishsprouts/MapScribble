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
    /** キーのカーソルの行（一覧の中の位置。-1 は無し）。フォーカス中で押せる時は必ず 0 以上になる。 */
    private int cursor = -1;
    /** カーソルの行のメモ帳の id。一覧の中身が変わっても id で追う。 */
    private String cursorId;
    /** 行を押した処理（onClick）が済み、バニラのフォーカスの取得（setFocused）が続く間だけ真。取得時の初期化で押した行を上書きしない。 */
    private boolean clickedRow;
    /** {@link #restore} がカーソルを戻した直後の、続くフォーカスの取得（setFocused）で、カーソルを初期化しないための一度だけの目印。 */
    private boolean keepCursorOnFocus;
    private final String inUseLabel;
    private static final int CURSOR_OVER = 0x1FFFFFFF;

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
        if (cursor >= 0) {
            // カーソルは id で追う。行が無くなったら範囲内へ丸める
            int found = indexOfId(cursorId);
            if (found >= 0) {
                cursor = found;
            } else {
                cursor = rows.isEmpty() ? -1 : Math.min(cursor, rows.size() - 1);
            }
            cursorId = cursor >= 0 ? rows.get(cursor).id() : null;
        }
        // 行が届いた時（読み込み中→行あり）に、フォーカス中で押せるのにカーソルが無ければ置く（設計書 3.7）
        ensureCursor();
    }

    /** 作り直しをまたいで保つ一覧の状態（設計書 3.7「画面の作り直しで保つもの」）。cursorId が null ならカーソルは無かった。 */
    public record Snapshot(String cursorId, int cursorIndex, int scroll) {
    }

    /** 作り直しの前の状態を覚える。カーソルは、フォーカス中で押せる時だけ覚える。 */
    public Snapshot snapshot() {
        boolean keep = isFocused() && cursorActive() && cursor >= 0 && cursor < rows.size();
        return new Snapshot(keep ? rows.get(cursor).id() : null, keep ? cursor : -1, scroll);
    }

    /**
     * 作り直した後の新しい一覧へ状態を戻す（行を入れた後に呼ぶ）。スクロールは新しい範囲へ丸め、
     * カーソルは id で追う（行が無くなっていたら範囲内へ丸める）。続くフォーカスの取得でカーソルを初期化しない。
     */
    public void restore(Snapshot s) {
        scroll = s.scroll();
        clampScroll();
        if (s.cursorId() != null && cursorActive() && !rows.isEmpty()) {
            int found = indexOfId(s.cursorId());
            int index = found >= 0 ? found : Math.max(0, Math.min(s.cursorIndex(), rows.size() - 1));
            setCursor(index);
            scrollTo(index);
            keepCursorOnFocus = true;
        }
    }

    // ---- 画面の確認（単体試験・client gametest）用の読み取り口。状態は変えない ----

    /** キーのカーソルの行のメモ帳の id（カーソルが無ければ null）。 */
    public String cursorRowId() {
        return cursor >= 0 && cursor < rows.size() ? rows.get(cursor).id() : null;
    }

    /** 一番上に見えている行の位置。 */
    public int scrollOffset() {
        return scroll;
    }

    /** 一覧に見せる行数（窓の大きさで 3 または 6）。 */
    public int visibleRowCount() {
        return visibleRows;
    }

    /** 今入っている行の数。 */
    public int rowCount() {
        return rows.size();
    }

    /** フォーカス中で押せて、行があるのにカーソルが無い時、フォーカスが入った時と同じ決まりでカーソルを置く。 */
    private void ensureCursor() {
        if (isFocused() && cursor < 0 && !rows.isEmpty() && cursorActive()) {
            placeCursorInitially();
        }
    }

    private int indexOfId(String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void setCursor(int index) {
        cursor = index;
        cursorId = index >= 0 && index < rows.size() ? rows.get(index).id() : null;
    }

    /** カーソルを使用中の行へ（無ければ先頭へ）置き、見えなければスクロールする。 */
    private void placeCursorInitially() {
        if (rows.isEmpty()) {
            setCursor(-1);
            return;
        }
        int target = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).inUse()) {
                target = i;
                break;
            }
        }
        setCursor(target);
        scrollTo(target);
    }

    /** 行を押してカーソルが描かれ、キーで切り替えられる状態か（分けていない接続先・移している間は不可）。 */
    private boolean cursorActive() {
        return pickable && !dimmed;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean gained = focused && !isFocused();
        super.setFocused(focused);
        if (gained) {
            if (keepCursorOnFocus) {
                keepCursorOnFocus = false;
            } else if (clickedRow) {
                clickedRow = false;
            } else if (cursorActive()) {
                placeCursorInitially();
            } else {
                // 押せない間はカーソルを持たない。押せるようになった時に置く（setPickable / setDimmed / setRows）
                setCursor(-1);
            }
        }
    }

    /** 行が無い時に中に出す灰色の 2 行（無ければ null）。 */
    public void setEmptyLines(Component line1, Component line2) {
        this.emptyLine1 = line1;
        this.emptyLine2 = line2;
    }

    /** 行を押して切り替えられるか。 */
    public void setPickable(boolean pickable) {
        boolean was = cursorActive();
        this.pickable = pickable;
        activeChanged(was);
    }

    /** 行を灰色にして押せない見た目にするか（分けていない接続先）。真なら名前・件数・日付が灰色で、ホバーの面も出ない。 */
    public void setDimmed(boolean dimmed) {
        boolean was = cursorActive();
        this.dimmed = dimmed;
        activeChanged(was);
    }

    /**
     * 押せる・押せないが変わった時。押せなくなったらカーソルは覚えない（戻らない）。押せるようになった時は、
     * フォーカス中なら、フォーカスが入った時と同じ決まりでカーソルを置く。
     */
    private void activeChanged(boolean was) {
        boolean now = cursorActive();
        if (was && !now) {
            setCursor(-1);
        } else if (!was && now) {
            ensureCursor();
        }
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
        // 行を押した処理とフォーカスの取得は同じイベントの中で済むので、描画の時点では目印を残さない
        clickedRow = false;
        keepCursorOnFocus = false;
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
            drawRow(g, rows.get(index), v, index == hovered, cursorActive() && isFocused() && index == cursor);
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
        boolean inUseKeyed = row.inUse() && keyed;
        if (row.inUse()) {
            GuiCompat.fill(g, x, y, x + w, y + h, FACE_IN_USE);
            if (keyed) {
                GuiCompat.fill(g, x, y, x + w, y + h, CURSOR_OVER);
            }
            GuiCompat.outline(g, x, y, w, h, WHITE);
        } else if (hovered || keyed) {
            GuiCompat.fill(g, x, y, x + w, y + h, FACE_HOVER);
            if (keyed) {
                GuiCompat.outline(g, x, y, w, h, WHITE);
            }
        }
        if (keyed) {
            // カーソルの縁は白い 2px（外周 1px と、その内側 1px）
            GuiCompat.outline(g, x + 1, y + 1, w - 2, h - 2, WHITE);
        }
        int textY = y + (h - font.lineHeight) / 2 + 1;
        if (!(row.inUse() && renamingInUse)) {
            int nameColor = dimmed ? GRAY
                    : (inUseKeyed ? WHITE
                    : (row.untitled() ? (row.inUse() ? GRAY_IN_USE : GRAY) : WHITE));
            GuiCompat.text(g, font, Component.literal(nameShown(row)), x + NotebookLayout.NAME_X, textY, nameColor,
                    !dimmed && !row.untitled());
        }
        int sub = dimmed ? GRAY : (inUseKeyed ? WHITE
                : (row.inUse() ? GRAY_IN_USE : (hovered || keyed ? GRAY_HOVER : GRAY)));
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

    /** 行を押した。押した行にカーソルを置いて（続くフォーカスの取得で上書きされない）、切り替える。 */
    void click(int index) {
        // 押せない間（移している間・灰色の行）はカーソルを持たないので、押してもカーソルを動かさない
        if (index >= 0 && cursorActive()) {
            setCursor(index);
            clickedRow = true;
        }
        pick(index);
    }

    //? if >=1.21.11 {
    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        click(rowIndexAt(event.x(), event.y()));
    }
    //?} else {
    /*@Override
    public void onClick(double mouseX, double mouseY) {
        click(rowIndexAt(mouseX, mouseY));
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
    boolean handleKey(int keyCode) {
        if (!isFocused()) {
            return false;
        }
        boolean down = keyCode == InputConstants.KEY_DOWN;
        boolean up = keyCode == InputConstants.KEY_UP;
        boolean enter = keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;
        if (rows.isEmpty() || !cursorActive()) {
            // 押せない時・行が無い時は、カーソルを描かない。↑↓は行の数に関係なく一覧が受け取り（フォーカスを外へ出さない）、
            // 行が表示の数を超える時だけ1行ずつスクロールする。Enter は何もしない
            if (down || up) {
                if (rows.size() > visibleRows) {
                    scroll += down ? 1 : -1;
                    clampScroll();
                }
                return true;
            }
            return false;
        }
        if (cursor < 0) {
            // 通常は ensureCursor が先に置く。通った時は、カーソルを置くだけで動かさない（最初の ↓ で使用中の行を飛ばさない）
            placeCursorInitially();
            if (down || up) {
                return true;
            }
        }
        if (down || up) {
            // 折り返さない。ホイールで動いた表示から、カーソルの行が見える位置へ戻す
            setCursor(down ? Math.min(rows.size() - 1, cursor + 1) : Math.max(0, cursor - 1));
            scrollTo(cursor);
            return true;
        }
        if (enter) {
            // 使用中の行で Enter を押しても何も起きない（同じメモ帳のため）
            if (!rows.get(cursor).inUse()) {
                pick(cursor);
            }
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
