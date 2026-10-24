package io.github.radishsprouts.mapscribble.jm;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.MemoStyle;
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
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * 全画面マップでの自前の描画。JourneyMap の FULLSCREEN_RENDER_EVENT（地図を描いた後・ボタンの前）で、
 * 画面に入るメモだけを滑らかな線・塗り・文字で描く。
 *
 * <p>GuiGraphicsExtractor に多角形を出すメソッドが無いため、矩形の fill と pose（回転・縮小）だけを使う。
 * 線・塗りは物理ピクセルの座標系（pose に 1/GUI倍率 を掛ける）で描き、文字は GUI 座標で描く。
 * 変換済みの画面座標と塗りの帯は、ズーム・中心・メモが変わるまで使い回す。
 */
final class FullscreenRenderer {
    /** 画面外のメモを飛ばす時の余白（物理ピクセル）。線の太さと継ぎ目の分。 */
    private static final double CULL_MARGIN_PIXELS = 12.0;

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

    FullscreenRenderer(DrawingController controller) {
        this.controller = controller;
    }

    void render(FullscreenRenderEvent event) {
        IFullscreen fullscreen = event.getFullscreen();
        Minecraft mc = Minecraft.getInstance();
        GuiGraphicsExtractor gfx = event.getGraphics();
        DrawingState state = controller.state();

        Object dimension = fullscreen.getUiState().dimension;
        if (dimension != lastDimension) {
            if (lastDimension != null) {
                controller.onDimensionChanged();
            }
            lastDimension = dimension;
            cache.clear();
        }

        Projection projection = MapProjection.of(fullscreen);
        if (!projection.equals(lastProjection)) {
            lastProjection = projection;
            projectionVersion++;
        }
        int guiScale = MapProjection.guiScale();
        Font font = mc.font;
        frame++;

        MemoRepository repo = controller.repository();
        List<Memo> memos = repo == null || !state.isVisible() ? List.of() : repo.memos();

        // 形（塗り・輪郭）は物理ピクセルの座標で描く
        Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        pose.scale(1f / guiScale, 1f / guiScale);
        Rect view = projection.visibleWorld().expand(projection.pixelsToWorld(CULL_MARGIN_PIXELS));
        for (Memo memo : memos) {
            Cached c = cached(memo, font);
            c.lastFrame = frame;
            if (memo.kind() == MemoKind.TEXT || !c.bbox.intersects(view)) {
                continue;
            }
            ensureScreenPoints(c, projection);
            drawShape(gfx, c);
        }
        drawActiveStroke(gfx, projection, guiScale);
        pose.popMatrix();

        // 文字は GUI 座標で描く
        double guiW = projection.width() / guiScale;
        double guiH = projection.height() / guiScale;
        boolean showRegionText = state.isRegionTextVisible();
        for (Memo memo : memos) {
            if (memo.kind() == MemoKind.REGION && !showRegionText) {
                continue;
            }
            Cached c = cache.get(memo.id());
            if (c == null || c.lines.length == 0) {
                continue;
            }
            drawText(gfx, font, c, projection, guiScale, guiW, guiH);
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

    /** ズーム・中心が変わった時だけ、画面座標と塗りの帯を作り直す。 */
    private void ensureScreenPoints(Cached c, Projection projection) {
        if (c.projectionVersion == projectionVersion) {
            return;
        }
        List<MapPoint> points = c.memo.points();
        int n = points.size();
        double[] sx = new double[n];
        double[] sy = new double[n];
        for (int i = 0; i < n; i++) {
            sx[i] = projection.toScreenX(points.get(i).x());
            sy[i] = projection.toScreenY(points.get(i).z());
        }
        c.sx = sx;
        c.sy = sy;
        c.fill = null;
        if (c.memo.kind() == MemoKind.REGION && c.memo.fillOpacity() > 0f && n >= 3) {
            c.fill = Scanline.fillRects(sx, sy, n, 0, 0, (int) projection.width(), (int) projection.height());
        }
        c.projectionVersion = projectionVersion;
    }

    private void drawShape(GuiGraphicsExtractor gfx, Cached c) {
        Memo memo = c.memo;
        if (c.fill != null) {
            int fillColor = MemoStyle.argb(memo.color(), memo.fillOpacity());
            for (int[] r : c.fill) {
                gfx.fill(r[0], r[1], r[2], r[3], fillColor);
            }
        }
        int stroke = MemoStyle.argb(memo.color(), 1f);
        int thickness = Math.round(MemoStyle.strokePixels(memo.width()));
        drawPolyline(gfx, c.sx, c.sy, c.sx.length, memo.kind() == MemoKind.REGION, thickness, stroke);
    }

    /** 描いている最中の一筆。確定したメモと同じ描き方で、囲みは指の位置から始点へ半透明の細い線を引く。 */
    private void drawActiveStroke(GuiGraphicsExtractor gfx, Projection projection, int guiScale) {
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
            // 離すと閉じることを見せる: 今の指の位置から始点へ、同じ色・不透明度 50%・太さ 1（GUI ピクセル）
            drawSegment(gfx, sx[total - 1], sy[total - 1], sx[0], sy[0], guiScale, MemoStyle.argb(state.color(), 0.5f));
        }
    }

    /** 折れ線を、線分ごとの回転した細い矩形と、点ごとの小さな正方形（継ぎ目）で描く。座標は物理ピクセル。 */
    private static void drawPolyline(GuiGraphicsExtractor gfx, double[] sx, double[] sy, int n, boolean closed,
                                     int thickness, int argb) {
        int count = closed ? n : n - 1;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % n;
            drawSegment(gfx, sx[i], sy[i], sx[j], sy[j], thickness, argb);
        }
        int half = thickness / 2;
        for (int i = 0; i < n; i++) {
            int x = (int) Math.round(sx[i]);
            int y = (int) Math.round(sy[i]);
            gfx.fill(x - half, y - half, x - half + thickness, y - half + thickness, argb);
        }
    }

    private static void drawSegment(GuiGraphicsExtractor gfx, double x0, double y0, double x1, double y1, int thickness,
                                    int argb) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.hypot(dx, dy);
        if (length < 0.5) {
            return;
        }
        Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        pose.translate((float) x0, (float) y0);
        pose.rotate((float) Math.atan2(dy, dx));
        int half = thickness / 2;
        gfx.fill(0, -half, (int) Math.ceil(length), thickness - half, argb);
        pose.popMatrix();
    }

    private void drawText(GuiGraphicsExtractor gfx, Font font, Cached c, Projection projection, int guiScale,
                          double guiW, double guiH) {
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
        Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        pose.translate((float) gx, (float) gy);
        pose.scale(scale, scale);
        int left = -c.maxLineWidth / 2 - 2;
        int right = left + c.maxLineWidth + 4;
        int top = -totalH / 2 - 2;
        gfx.fill(left, top, right, top + totalH + 4, bg);
        for (int i = 0; i < c.lines.length; i++) {
            gfx.text(font, c.lines[i], -c.lineWidths[i] / 2, -totalH / 2 + i * font.lineHeight, fg, shadow);
        }
        pose.popMatrix();
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
