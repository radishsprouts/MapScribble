package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.geom.Rect;
import io.github.radishsprouts.mapscribble.geom.Scanline;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.network.chat.Component;

/**
 * 全画面マップでの手書きメモ描画エンジン。JourneyMap / Xaero's World Map / FTB Chunks で共有する。
 * 画面に入るメモだけをスキャンライン塗り・回転矩形線分・キャッシュ付き文字で高速描画する。
 */
public final class MapCanvasRenderer {
    /** 画面外のメモを飛ばす時の余白（物理ピクセル）。線の太さと継ぎ目の分。 */
    private static final double CULL_MARGIN_PIXELS = 12.0;

    /** 文字メモの印（菱形）の、中心から各方向への大きさ（物理ピクセル）。対角で 8 ピクセル。 */
    private static final int MARK_RADIUS = 4;
    /** 印の縁（黒 80%）。 */
    private static final int MARK_EDGE = 0xCC000000;
    /** 黒のメモの印の縁（白 80%）。 */
    private static final int MARK_EDGE_ON_BLACK = 0xCCFFFFFF;

    /** メモ 1 件の描画用キャッシュ。 */
    private static final class Cached {
        final Memo memo;
        final Rect bbox;
        final MapPoint anchor;
        final String[] lines;
        final int[] lineWidths;
        final int maxLineWidth;
        int projectionVersion = -1;
        double[] sx;
        double[] sy;
        List<int[]> fill;
        long lastFrame;

        Cached(Memo memo, Font font) {
            this.memo = memo;
            this.bbox = Rect.of(memo.points());
            this.anchor = MemoStyle.anchor(memo);
            this.lines = MemoStyle.lines(memo.text());
            this.lineWidths = new int[lines.length];
            int max = 0;
            for (int i = 0; i < lines.length; i++) {
                lineWidths[i] = font.width(lines[i]);
                max = Math.max(max, lineWidths[i]);
            }
            this.maxLineWidth = max;
        }
    }

    private final DrawingController controller;
    private final Map<UUID, Cached> cache = new HashMap<>();
    private Projection lastProjection;
    private int projectionVersion;
    private long frame;
    private Object lastDimension;

    public MapCanvasRenderer(DrawingController controller) {
        this.controller = controller;
    }

    //? if >=26.1 {
    public void render(GuiGraphicsExtractor gfx, Projection projection, Object dimension, int guiScale) {
    //?} else {
    /*public void render(GuiGraphics gfx, Projection projection, Object dimension, int guiScale) {
    *///?}
        Minecraft mc = Minecraft.getInstance();
        DrawingState state = controller.state();

        if (dimension != lastDimension) {
            if (lastDimension != null) {
                controller.onDimensionChanged();
            }
            lastDimension = dimension;
            cache.clear();
        }

        if (!projection.equals(lastProjection)) {
            lastProjection = projection;
            projectionVersion++;
        }
        Font font = mc.font;
        frame++;

        MemoRepository repo = controller.repository();
        List<Memo> memos = repo == null || !state.isVisible() ? List.of() : repo.memos();

        // 文字の表示切替: 囲み・線・文字メモのすべての文字が対象
        boolean showText = state.isTextVisible();

        // 形（塗り・輪郭）は物理ピクセルの座標で描く
        GuiCompat.pushPose(gfx);
        GuiCompat.scale2D(gfx, 1f / guiScale, 1f / guiScale);
        Rect view = projection.visibleWorld().expand(projection.pixelsToWorld(CULL_MARGIN_PIXELS));
        for (Memo memo : memos) {
            Cached c = cached(memo, font);
            c.lastFrame = frame;
            if (!c.bbox.intersects(view)) {
                continue;
            }
            if (memo.kind() == MemoKind.TEXT) {
                // 文字メモは文字が本体。文字を隠している間は、消えたのか不具合なのか分かるよう菱形の印を残す
                if (!showText) {
                    drawTextMark(gfx, projection.toScreenX(c.anchor.x()), projection.toScreenY(c.anchor.z()),
                            memo.color());
                }
                continue;
            }
            ensureScreenPoints(c, projection);
            drawShape(gfx, c);
        }
        drawActiveStroke(gfx, projection, guiScale);
        GuiCompat.popPose(gfx);

        // 文字は GUI 座標で描く
        double guiW = projection.width() / guiScale;
        double guiH = projection.height() / guiScale;
        if (showText) {
            for (Memo memo : memos) {
                Cached c = cached(memo, font);
                c.lastFrame = frame;
                if (c.lines.length == 0 || !c.bbox.intersects(view)) {
                    continue;
                }
                drawText(gfx, font, c, projection, guiScale, guiW, guiH);
            }
        }

        pruneCache(memos.size());
    }

    private Cached cached(Memo memo, Font font) {
        Cached c = cache.get(memo.id());
        if (c == null || c.memo != memo) {
            c = new Cached(memo, font);
            cache.put(memo.id(), c);
        }
        return c;
    }

    private void ensureScreenPoints(Cached c, Projection projection) {
        if (c.projectionVersion == projectionVersion && c.sx != null) {
            return;
        }
        List<MapPoint> points = c.memo.points();
        int n = points.size();
        double[] sx = new double[n];
        double[] sy = new double[n];
        for (int i = 0; i < n; i++) {
            MapPoint p = points.get(i);
            sx[i] = projection.toScreenX(p.x());
            sy[i] = projection.toScreenY(p.z());
        }
        c.sx = sx;
        c.sy = sy;
        c.fill = null;
        if (c.memo.kind() == MemoKind.REGION && c.memo.fillOpacity() > 0f && n >= 3) {
            c.fill = Scanline.fillRects(sx, sy, n, 0, 0, (int) projection.width(), (int) projection.height());
        }
        c.projectionVersion = projectionVersion;
    }

    //? if >=26.1 {
    private void drawShape(GuiGraphicsExtractor gfx, Cached c) {
    //?} else {
    /*private void drawShape(GuiGraphics gfx, Cached c) {
    *///?}
        if (c.fill != null && !c.fill.isEmpty()) {
            int fillArgb = MemoStyle.argb(c.memo.color(), c.memo.fillOpacity());
            for (int[] r : c.fill) {
                GuiCompat.fill(gfx, r[0], r[1], r[2], r[3], fillArgb);
            }
        }
        int thickness = Math.round(MemoStyle.strokePixels(c.memo.width()));
        drawPolyline(gfx, c.sx, c.sy, c.sx.length, c.memo.kind() == MemoKind.REGION, thickness,
                MemoStyle.argb(c.memo.color(), 1f));
    }

    /**
     * 文字を隠している間の文字メモの印（菱形）。物理ピクセルの座標で描くので、地図のズーム・GUI 倍率に関わらず
     * 同じ大きさになる（中心から各方向 {@link #MARK_RADIUS} ピクセル、縁 1 ピクセル）。
     * 色はメモの色。縁は黒 80%（黒のメモだけ白 80%）。
     */
    //? if >=26.1 {
    private static void drawTextMark(GuiGraphicsExtractor gfx, double sx, double sy, int rgb) {
    //?} else {
    /*private static void drawTextMark(GuiGraphics gfx, double sx, double sy, int rgb) {
    *///?}
        int cx = (int) Math.round(sx);
        int cy = (int) Math.round(sy);
        int edge = MemoStyle.isBlack(rgb) ? MARK_EDGE_ON_BLACK : MARK_EDGE;
        fillDiamond(gfx, cx, cy, MARK_RADIUS + 1, edge);
        fillDiamond(gfx, cx, cy, MARK_RADIUS, 0xFF000000 | (rgb & 0xFFFFFF));
    }

    /** 中心 (cx, cy) から各方向 radius ピクセルの塗りつぶした菱形を、1 ピクセル高の横長の矩形で描く。 */
    //? if >=26.1 {
    private static void fillDiamond(GuiGraphicsExtractor gfx, int cx, int cy, int radius, int argb) {
    //?} else {
    /*private static void fillDiamond(GuiGraphics gfx, int cx, int cy, int radius, int argb) {
    *///?}
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            GuiCompat.fill(gfx, cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
        }
    }

    //? if >=26.1 {
    private void drawActiveStroke(GuiGraphicsExtractor gfx, Projection projection, int guiScale) {
    //?} else {
    /*private void drawActiveStroke(GuiGraphics gfx, Projection projection, int guiScale) {
    *///?}
        DrawingController.ActiveStroke stroke = controller.activeStroke();
        if (stroke == null || stroke.points().isEmpty() || !controller.state().tool().isStrokeTool()) {
            return;
        }
        DrawingState state = controller.state();
        List<MapPoint> points = stroke.points();
        int n = points.size();
        MapPoint cursor = stroke.cursor();
        boolean extra = cursor != null && (cursor.x() != points.get(n - 1).x() || cursor.z() != points.get(n - 1).z());
        int total = n + (extra ? 1 : 0);
        double[] sx = new double[total];
        double[] sy = new double[total];
        for (int i = 0; i < n; i++) {
            sx[i] = projection.toScreenX(points.get(i).x());
            sy[i] = projection.toScreenY(points.get(i).z());
        }
        if (extra) {
            sx[n] = projection.toScreenX(cursor.x());
            sy[n] = projection.toScreenY(cursor.z());
        }
        int thickness = Math.round(MemoStyle.strokePixels(state.width()));
        drawPolyline(gfx, sx, sy, total, false, thickness, MemoStyle.argb(state.color(), 1f));
        if (stroke.kind() == MemoKind.REGION && total >= 2) {
            drawSegment(gfx, sx[total - 1], sy[total - 1], sx[0], sy[0], guiScale, MemoStyle.argb(state.color(), 0.5f));
        }
    }

    //? if >=26.1 {
    private static void drawPolyline(GuiGraphicsExtractor gfx, double[] sx, double[] sy, int n, boolean closed,
                                     int thickness, int argb) {
    //?} else {
    /*private static void drawPolyline(GuiGraphics gfx, double[] sx, double[] sy, int n, boolean closed,
                                     int thickness, int argb) {
    *///?}
        int count = closed ? n : n - 1;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % n;
            drawSegment(gfx, sx[i], sy[i], sx[j], sy[j], thickness, argb);
        }
        int half = thickness / 2;
        for (int i = 0; i < n; i++) {
            int x = (int) Math.round(sx[i]);
            int y = (int) Math.round(sy[i]);
            GuiCompat.fill(gfx, x - half, y - half, x - half + thickness, y - half + thickness, argb);
        }
    }

    //? if >=26.1 {
    private static void drawSegment(GuiGraphicsExtractor gfx, double x0, double y0, double x1, double y1, int thickness,
                                    int argb) {
    //?} else {
    /*private static void drawSegment(GuiGraphics gfx, double x0, double y0, double x1, double y1, int thickness,
                                    int argb) {
    *///?}
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.hypot(dx, dy);
        if (length < 0.5) {
            return;
        }
        GuiCompat.pushPose(gfx);
        GuiCompat.translate2D(gfx, (float) x0, (float) y0);
        GuiCompat.rotate2D(gfx, (float) Math.toDegrees(Math.atan2(dy, dx)));
        int half = thickness / 2;
        GuiCompat.fill(gfx, 0, -half, (int) Math.ceil(length), thickness - half, argb);
        GuiCompat.popPose(gfx);
    }

    //? if >=26.1 {
    private void drawText(GuiGraphicsExtractor gfx, Font font, Cached c, Projection projection, int guiScale,
                          double guiW, double guiH) {
    //?} else {
    /*private void drawText(GuiGraphics gfx, Font font, Cached c, Projection projection, int guiScale,
                          double guiW, double guiH) {
    *///?}
        Memo memo = c.memo;
        double gx = projection.toScreenX(c.anchor.x()) / guiScale;
        double gy = projection.toScreenY(c.anchor.z()) / guiScale;
        float scale = memo.textScale();
        double halfW = c.maxLineWidth * scale / 2.0 + 4;
        double halfH = c.lines.length * font.lineHeight * scale / 2.0 + 4;
        if (gx + halfW < 0 || gx - halfW > guiW || gy + halfH < 0 || gy - halfH > guiH) {
            return;
        }
        int totalH = c.lines.length * font.lineHeight;
        int bg = MemoStyle.argb(MemoStyle.textBackgroundRgb(memo.color()), MemoStyle.TEXT_BACKGROUND_OPACITY);
        int fg = 0xFF000000 | (memo.color() & 0xFFFFFF);
        boolean shadow = MemoStyle.textShadow(memo.color());
        GuiCompat.pushPose(gfx);
        GuiCompat.translate2D(gfx, (float) gx, (float) gy);
        GuiCompat.scale2D(gfx, scale, scale);
        int left = -c.maxLineWidth / 2 - 2;
        int right = left + c.maxLineWidth + 4;
        int top = -totalH / 2 - 2;
        GuiCompat.fill(gfx, left, top, right, top + totalH + 4, bg);
        for (int i = 0; i < c.lines.length; i++) {
            GuiCompat.text(gfx, font, Component.literal(c.lines[i]), -c.lineWidths[i] / 2,
                    -totalH / 2 + i * font.lineHeight, fg, shadow);
        }
        GuiCompat.popPose(gfx);
    }

    /** 画面に出ていない・消えたメモのキャッシュを捨てる。 */
    private void pruneCache(int liveMemos) {
        if (cache.size() <= liveMemos + 16) {
            return;
        }
        Iterator<Cached> it = cache.values().iterator();
        while (it.hasNext()) {
            if (it.next().lastFrame != frame) {
                it.remove();
            }
        }
    }
}
