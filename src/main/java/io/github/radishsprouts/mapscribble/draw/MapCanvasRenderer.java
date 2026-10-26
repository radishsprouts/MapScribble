package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.geom.CircleClip;
import io.github.radishsprouts.mapscribble.geom.ClipRegion;
import io.github.radishsprouts.mapscribble.geom.MapFrame;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.geom.Rect;
import io.github.radishsprouts.mapscribble.geom.Scanline;
import io.github.radishsprouts.mapscribble.geom.ViewTransform;
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
 * 手書きメモの描画エンジン。全画面マップ（JourneyMap / Xaero's World Map / FTB Chunks）と
 * ミニマップ（Xaero's Minimap）で共有する。
 * 画面に入るメモだけをスキャンライン塗り・回転矩形線分・キャッシュ付き文字で高速描画する。
 *
 * <p>描画条件（回転・切り抜き・ミニマップ限定の絞り込み）は {@link MapFrame} で受ける。
 * 全画面マップは回転なし・切り抜きは描画面いっぱい、ミニマップは回転あり・枠の内側で切り抜き、という違いだけで、
 * 描く処理そのものは共通である。
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
    /** 文字の背景の余白（GUI ピクセル。{@code drawText} の矩形と同じ値）。 */
    private static final double TEXT_PADDING = 4.0;

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
    private ViewTransform lastView;
    private int projectionVersion;
    private long frame;
    private Object lastDimension;

    public MapCanvasRenderer(DrawingController controller) {
        this.controller = controller;
    }

    /** 全画面マップ用（回転なし・切り抜きは描画面いっぱい・すべてのメモ）。 */
    //? if >=26.1 {
    public void render(GuiGraphicsExtractor gfx, Projection projection, Object dimension, int guiScale) {
    //?} else {
    /*public void render(GuiGraphics gfx, Projection projection, Object dimension, int guiScale) {
    *///?}
        render(gfx, MapFrame.fullscreen(projection, guiScale, dimension));
    }

    //? if >=26.1 {
    public void render(GuiGraphicsExtractor gfx, MapFrame mapFrame) {
    //?} else {
    /*public void render(GuiGraphics gfx, MapFrame mapFrame) {
    *///?}
        Minecraft mc = Minecraft.getInstance();
        DrawingState state = controller.state();
        ViewTransform view = mapFrame.view();
        ClipRegion clip = mapFrame.clip();
        int guiScale = mapFrame.guiScale();
        Object dimension = mapFrame.dimension();

        if (dimension != lastDimension) {
            if (lastDimension != null) {
                controller.onDimensionChanged();
            }
            lastDimension = dimension;
            cache.clear();
        }

        // 回転が変わればスクリーン座標を作り直す必要があるので、投影ではなく変換そのものを比べる
        if (!view.equals(lastView)) {
            lastView = view;
            projectionVersion++;
        }
        Font font = mc.font;
        frame++;

        MemoRepository repo = controller.repository();
        List<Memo> memos = repo == null || !state.isVisible() ? List.of() : repo.memos();

        // 文字の表示切替: 囲み・線・文字メモのすべての文字が対象
        boolean showText = state.isTextVisible();

        // 回転があると画面の矩形はワールド上で傾くので、中心からの距離で包含矩形を作る
        double radiusBlocks = view.pixelsToWorld(clip.coverRadius() + CULL_MARGIN_PIXELS);
        Rect visible = new Rect(view.centerX() - radiusBlocks, view.centerZ() - radiusBlocks,
                view.centerX() + radiusBlocks, view.centerZ() + radiusBlocks);

        // 形（塗り・輪郭）は物理ピクセルの座標で描く。例外が出ても pose を必ず戻す
        GuiCompat.pushPose(gfx);
        try {
            GuiCompat.scale2D(gfx, 1f / guiScale, 1f / guiScale);
            for (Memo memo : memos) {
                Cached c = cached(memo, font);
                c.lastFrame = frame;
                if (mapFrame.minimapOnly() && !memo.onMinimap()) {
                    continue;
                }
                if (!c.bbox.intersects(visible)) {
                    continue;
                }
                if (memo.kind() == MemoKind.TEXT) {
                    // 文字メモは文字が本体。文字を隠している間は、消えたのか不具合なのか分かるよう菱形の印を残す
                    if (!showText) {
                        double px = view.screenX(c.anchor.x(), c.anchor.z());
                        double py = view.screenY(c.anchor.x(), c.anchor.z());
                        if (clip.contains(px, py)) {
                            drawTextMark(gfx, px, py, memo.color());
                        }
                    }
                    continue;
                }
                ensureScreenPoints(c, mapFrame);
                drawShape(gfx, c, clip);
            }
            drawActiveStroke(gfx, mapFrame);
        } finally {
            GuiCompat.popPose(gfx);
        }

        // 文字は GUI 座標で描く
        if (showText) {
            for (Memo memo : memos) {
                Cached c = cached(memo, font);
                c.lastFrame = frame;
                if (mapFrame.minimapOnly() && !memo.onMinimap()) {
                    continue;
                }
                if (c.lines.length == 0 || !textVisible(c, font, visible, mapFrame)) {
                    continue;
                }
                drawText(gfx, font, c, mapFrame);
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

    /** 文字の背景の矩形の、中心から横への広がり（物理ピクセル）。 */
    private static double textHalfWidthPhys(Cached c, int guiScale) {
        return (c.maxLineWidth * c.memo.textScale() / 2.0 + TEXT_PADDING) * guiScale;
    }

    /** 文字の背景の矩形の、中心から縦への広がり（物理ピクセル）。 */
    private static double textHalfHeightPhys(Cached c, Font font, int guiScale) {
        return (c.lines.length * font.lineHeight * c.memo.textScale() / 2.0 + TEXT_PADDING) * guiScale;
    }

    /**
     * 文字が画面に残っているか。
     *
     * <p>文字メモの {@code bbox} は錨の点だけなので、点の bbox で判定すると、錨が画面の外へ
     * {@link #CULL_MARGIN_PIXELS} ぶん出た時点で、画面内に文字が残っていても消えてしまう
     * （長い文字ほど目立つ）。文字の実際の広がりをワールドの距離に直して bbox へ足してから判定する。
     * 回転があると文字の矩形はワールドのどの方向へも広がり得るので、対角の長さを使う。
     */
    private static boolean textVisible(Cached c, Font font, Rect visible, MapFrame mapFrame) {
        double halfW = textHalfWidthPhys(c, mapFrame.guiScale());
        double halfH = textHalfHeightPhys(c, font, mapFrame.guiScale());
        double marginBlocks = mapFrame.view().pixelsToWorld(Math.hypot(halfW, halfH));
        return c.bbox.expand(marginBlocks).intersects(visible);
    }

    private void ensureScreenPoints(Cached c, MapFrame mapFrame) {
        if (c.projectionVersion == projectionVersion && c.sx != null) {
            return;
        }
        ViewTransform view = mapFrame.view();
        ClipRegion clip = mapFrame.clip();
        List<MapPoint> points = c.memo.points();
        int n = points.size();
        double[] sx = new double[n];
        double[] sy = new double[n];
        for (int i = 0; i < n; i++) {
            MapPoint p = points.get(i);
            sx[i] = view.screenX(p.x(), p.z());
            sy[i] = view.screenY(p.x(), p.z());
        }
        c.sx = sx;
        c.sy = sy;
        c.fill = null;
        if (c.memo.kind() == MemoKind.REGION && c.memo.fillOpacity() > 0f && n >= 3) {
            c.fill = Scanline.fillRects(sx, sy, n,
                    (int) Math.floor(clip.minX()), (int) Math.floor(clip.minY()),
                    (int) Math.ceil(clip.maxX()), (int) Math.ceil(clip.maxY()),
                    clip.circleX(), clip.circleY(), clip.hasCircle() ? clip.radius() : -1.0);
        }
        c.projectionVersion = projectionVersion;
    }

    //? if >=26.1 {
    private void drawShape(GuiGraphicsExtractor gfx, Cached c, ClipRegion clip) {
    //?} else {
    /*private void drawShape(GuiGraphics gfx, Cached c, ClipRegion clip) {
    *///?}
        if (c.fill != null && !c.fill.isEmpty()) {
            int fillArgb = MemoStyle.argb(c.memo.color(), c.memo.fillOpacity());
            for (int[] r : c.fill) {
                GuiCompat.fill(gfx, r[0], r[1], r[2], r[3], fillArgb);
            }
        }
        int thickness = Math.round(MemoStyle.strokePixels(c.memo.width()));
        drawPolyline(gfx, c.sx, c.sy, c.sx.length, c.memo.kind() == MemoKind.REGION, thickness,
                MemoStyle.argb(c.memo.color(), 1f), clip);
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
    private void drawActiveStroke(GuiGraphicsExtractor gfx, MapFrame mapFrame) {
    //?} else {
    /*private void drawActiveStroke(GuiGraphics gfx, MapFrame mapFrame) {
    *///?}
        DrawingController.ActiveStroke stroke = controller.activeStroke();
        if (stroke == null || stroke.points().isEmpty() || !controller.state().tool().isStrokeTool()) {
            return;
        }
        DrawingState state = controller.state();
        ViewTransform view = mapFrame.view();
        ClipRegion clip = mapFrame.clip();
        List<MapPoint> points = stroke.points();
        int n = points.size();
        MapPoint cursor = stroke.cursor();
        boolean extra = cursor != null && (cursor.x() != points.get(n - 1).x() || cursor.z() != points.get(n - 1).z());
        int total = n + (extra ? 1 : 0);
        double[] sx = new double[total];
        double[] sy = new double[total];
        for (int i = 0; i < n; i++) {
            MapPoint p = points.get(i);
            sx[i] = view.screenX(p.x(), p.z());
            sy[i] = view.screenY(p.x(), p.z());
        }
        if (extra) {
            sx[n] = view.screenX(cursor.x(), cursor.z());
            sy[n] = view.screenY(cursor.x(), cursor.z());
        }
        int thickness = Math.round(MemoStyle.strokePixels(state.width()));
        drawPolyline(gfx, sx, sy, total, false, thickness, MemoStyle.argb(state.color(), 1f), clip);
        if (stroke.kind() == MemoKind.REGION && total >= 2) {
            drawSegment(gfx, sx[total - 1], sy[total - 1], sx[0], sy[0], mapFrame.guiScale(),
                    MemoStyle.argb(state.color(), 0.5f), clip);
        }
    }

    //? if >=26.1 {
    private static void drawPolyline(GuiGraphicsExtractor gfx, double[] sx, double[] sy, int n, boolean closed,
                                     int thickness, int argb, ClipRegion clip) {
    //?} else {
    /*private static void drawPolyline(GuiGraphics gfx, double[] sx, double[] sy, int n, boolean closed,
                                     int thickness, int argb, ClipRegion clip) {
    *///?}
        int count = closed ? n : n - 1;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % n;
            drawSegment(gfx, sx[i], sy[i], sx[j], sy[j], thickness, argb, clip);
        }
        int half = thickness / 2;
        for (int i = 0; i < n; i++) {
            // 継ぎ目の四角は回転させずに置くので、円の外にある分はここで落とす
            if (clip.hasCircle()
                    && !CircleClip.contains(sx[i], sy[i], clip.circleX(), clip.circleY(), clip.radius())) {
                continue;
            }
            int x = (int) Math.round(sx[i]);
            int y = (int) Math.round(sy[i]);
            GuiCompat.fill(gfx, x - half, y - half, x - half + thickness, y - half + thickness, argb);
        }
    }

    //? if >=26.1 {
    private static void drawSegment(GuiGraphicsExtractor gfx, double x0, double y0, double x1, double y1, int thickness,
                                    int argb, ClipRegion clip) {
    //?} else {
    /*private static void drawSegment(GuiGraphics gfx, double x0, double y0, double x1, double y1, int thickness,
                                    int argb, ClipRegion clip) {
    *///?}
        if (clip.hasCircle()) {
            // 円形のミニマップでは、矩形の scissor では落ちない角の部分を線分の側で切る
            double[] cut = CircleClip.clipSegment(x0, y0, x1, y1, clip.circleX(), clip.circleY(), clip.radius());
            if (cut == null) {
                return;
            }
            x0 = cut[0];
            y0 = cut[1];
            x1 = cut[2];
            y1 = cut[3];
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.hypot(dx, dy);
        if (length < 0.5) {
            return;
        }
        GuiCompat.pushPose(gfx);
        try {
            GuiCompat.translate2D(gfx, (float) x0, (float) y0);
            GuiCompat.rotate2D(gfx, (float) Math.toDegrees(Math.atan2(dy, dx)));
            int half = thickness / 2;
            GuiCompat.fill(gfx, 0, -half, (int) Math.ceil(length), thickness - half, argb);
        } finally {
            GuiCompat.popPose(gfx);
        }
    }

    //? if >=26.1 {
    private void drawText(GuiGraphicsExtractor gfx, Font font, Cached c, MapFrame mapFrame) {
    //?} else {
    /*private void drawText(GuiGraphics gfx, Font font, Cached c, MapFrame mapFrame) {
    *///?}
        Memo memo = c.memo;
        ViewTransform view = mapFrame.view();
        ClipRegion clip = mapFrame.clip();
        int guiScale = mapFrame.guiScale();
        double px = view.screenX(c.anchor.x(), c.anchor.z());
        double py = view.screenY(c.anchor.x(), c.anchor.z());
        double halfWPhys = textHalfWidthPhys(c, guiScale);
        double halfHPhys = textHalfHeightPhys(c, font, guiScale);
        if (px + halfWPhys < clip.minX() || px - halfWPhys > clip.maxX()
                || py + halfHPhys < clip.minY() || py - halfHPhys > clip.maxY()) {
            return;
        }
        if (clip.hasCircle() && !cornersInsideCircle(clip, px, py, halfWPhys, halfHPhys)) {
            // 円形のミニマップで背景の矩形が円からはみ出すときは、文字を途中で切らずに菱形の印へ置き換える
            if (clip.contains(px, py)) {
                GuiCompat.pushPose(gfx);
                try {
                    GuiCompat.scale2D(gfx, 1f / guiScale, 1f / guiScale);
                    drawTextMark(gfx, px, py, memo.color());
                } finally {
                    GuiCompat.popPose(gfx);
                }
            }
            return;
        }
        double gx = px / guiScale;
        double gy = py / guiScale;
        float scale = memo.textScale();
        int totalH = c.lines.length * font.lineHeight;
        int bg = MemoStyle.argb(MemoStyle.textBackgroundRgb(memo.color()), MemoStyle.TEXT_BACKGROUND_OPACITY);
        int fg = 0xFF000000 | (memo.color() & 0xFFFFFF);
        boolean shadow = MemoStyle.textShadow(memo.color());
        GuiCompat.pushPose(gfx);
        try {
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
        } finally {
            GuiCompat.popPose(gfx);
        }
    }

    /** 文字の背景の矩形の 4 隅がすべて円の内側か。 */
    private static boolean cornersInsideCircle(ClipRegion clip, double px, double py, double halfW, double halfH) {
        double cx = clip.circleX();
        double cy = clip.circleY();
        double r = clip.radius();
        return CircleClip.contains(px - halfW, py - halfH, cx, cy, r)
                && CircleClip.contains(px + halfW, py - halfH, cx, cy, r)
                && CircleClip.contains(px - halfW, py + halfH, cx, cy, r)
                && CircleClip.contains(px + halfW, py + halfH, cx, cy, r);
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
