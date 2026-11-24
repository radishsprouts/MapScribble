package io.github.radishsprouts.mapscribble.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.compat.Gfx;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 帯のメニューのボタンから開く縦の一覧（設計書 3.6）。開閉・選んでいる行・描画・入力を持つ。
 * 位置と大きさは {@link MenuLayout}。メニューの中にメニューは作らない（項目を押すとその機能の画面を開く）。
 */
public final class BandMenu {
    /** メニューの 1 項目。{@code note} は右の灰色の補足。押せない項目は {@code tooltip} でその理由を出す。 */
    public record Item(Component label, String note, boolean enabled, Component tooltip, Runnable action) {
    }

    private static final int BG = 0xF0202428;
    private static final int EDGE = 0xFF50565E;
    private static final int FACE_HOVER = 0x1FFFFFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int NOTE = 0xFFA0A0A0;
    private static final int NOTE_HOVER = 0xFFC0C0C0;
    private static final int DISABLED = 0xFF80868E;
    /** ラベルと補足の間に空ける幅。 */
    private static final int LABEL_NOTE_GAP = 8;

    /**
     * 補足に使える幅。行の幅 150 から左右の余白 10・ラベルの幅・間 8 を引いた残り（設計書 3.6）。
     * 補足のうち固定の文言（分けていない時・シングルプレイの時）はこの幅の内側で作る。幅の試験もこの式を使う。
     */
    public static int noteRoom(int labelWidth) {
        return MenuLayout.WIDTH - MenuLayout.TEXT_PAD * 2 - labelWidth - LABEL_NOTE_GAP;
    }

    private boolean open;
    private int selected = -1;

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
        selected = -1;
    }

    public void close() {
        open = false;
        selected = -1;
    }

    // ---- 描画 ----

    public void render(Gfx g, Font font, int mouseX, int mouseY, int bandX, int bandY, int screenW, int screenH,
                       List<Item> items) {
        if (!open || items.isEmpty()) {
            return;
        }
        MenuLayout.Rect rect = MenuLayout.place(bandX, bandY, items.size(), screenW, screenH);
        GuiCompat.nextLayer(g);
        GuiCompat.fill(g, rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), BG);
        GuiCompat.outline(g, rect.x(), rect.y(), rect.w(), rect.h(), EDGE);

        int hovered = MenuLayout.rowAt(rect, items.size(), mouseX, mouseY);
        Component tooltip = null;
        for (int i = 0; i < items.size() && i < MenuLayout.MAX_ITEMS; i++) {
            Item item = items.get(i);
            int rowY = MenuLayout.rowY(rect, i);
            boolean hover = i == hovered && item.enabled();
            boolean keyed = i == selected;
            if (hover || keyed) {
                GuiCompat.fill(g, rect.x() + 1, rowY, rect.x() + rect.w() - 1, rowY + MenuLayout.ROW_HEIGHT, FACE_HOVER);
            }
            if (keyed) {
                GuiCompat.outline(g, rect.x() + 1, rowY, rect.w() - 2, MenuLayout.ROW_HEIGHT, WHITE);
            }
            int textY = rowY + (MenuLayout.ROW_HEIGHT - font.lineHeight) / 2 + 1;
            int labelColor = item.enabled() ? WHITE : DISABLED;
            GuiCompat.text(g, font, item.label(), rect.x() + MenuLayout.TEXT_PAD, textY, labelColor, item.enabled());

            String note = item.note();
            if (note != null && !note.isEmpty()) {
                int room = noteRoom(font.width(item.label()));
                String shown = NotebookPanel.ellipsize(note, Math.max(0, room), font::width);
                int noteColor = !item.enabled() ? DISABLED : (hover || keyed ? NOTE_HOVER : NOTE);
                GuiCompat.text(g, font, Component.literal(shown),
                        rect.x() + rect.w() - MenuLayout.TEXT_PAD - font.width(shown), textY, noteColor, false);
                if (i == hovered && !shown.equals(note) && item.tooltip() == null) {
                    tooltip = Component.literal(note);
                }
            }
            if (i == hovered && item.tooltip() != null) {
                tooltip = item.tooltip();
            }
        }
        if (tooltip != null) {
            GuiCompat.drawWrappedTooltip(g, font, screenW, List.of(tooltip), mouseX, mouseY);
        }
    }

    // ---- 入力 ----

    /**
     * 開いている間のクリック。どのボタンのクリックでも消費する（閉じるつもりのクリックで地図に描いたり、
     * 帯のボタンが押されたりしないため）。押せる項目の上なら閉じてからその項目の処理を行う。メニューの外なら閉じるだけ。
     *
     * @return 消費したか（開いていなければ false）
     */
    public boolean click(double mouseX, double mouseY, boolean leftButton, int bandX, int bandY, int screenW,
                         int screenH, List<Item> items) {
        if (!open) {
            return false;
        }
        MenuLayout.Rect rect = MenuLayout.place(bandX, bandY, items.size(), screenW, screenH);
        int row = leftButton ? MenuLayout.rowAt(rect, items.size(), mouseX, mouseY) : -1;
        if (row >= 0 && items.get(row).enabled()) {
            close();
            GuiCompat.playButtonClickSound();
            items.get(row).action().run();
        } else if (rect.contains(mouseX, mouseY)) {
            // 押せない項目・行の外の余白: 閉じずに受けるだけ
            return true;
        } else {
            close();
        }
        return true;
    }

    /**
     * 開いている間のキー。↑↓ で行を選び、Enter で開く。Esc で閉じる。
     *
     * @return このキーを処理した（メニューが受けた）か
     */
    public boolean key(int keyCode, List<Item> items) {
        if (!open) {
            return false;
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        boolean[] enabled = new boolean[items.size()];
        for (int i = 0; i < enabled.length; i++) {
            enabled[i] = items.get(i).enabled();
        }
        if (keyCode == InputConstants.KEY_DOWN) {
            selected = MenuLayout.moveSelection(enabled, selected, 1);
            return true;
        }
        if (keyCode == InputConstants.KEY_UP) {
            selected = MenuLayout.moveSelection(enabled, selected, -1);
            return true;
        }
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            if (selected >= 0 && selected < items.size() && items.get(selected).enabled()) {
                Item item = items.get(selected);
                close();
                item.action().run();
            }
            return true;
        }
        return false;
    }
}
