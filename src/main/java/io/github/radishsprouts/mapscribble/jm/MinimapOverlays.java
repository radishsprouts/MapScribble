package io.github.radishsprouts.mapscribble.jm;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.JmCompat;
import io.github.radishsprouts.mapscribble.draw.MemoStyle;
import io.github.radishsprouts.mapscribble.geom.BlockPoints;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * ミニマップへの表示。JourneyMap の PolygonOverlay（ブロック単位）で出す。全画面マップは自前で描くので、
 * 表示先はミニマップだけにする。メモを変えた時だけ show / remove を呼ぶ（再描画は JourneyMap が行う）。
 *
 * <p>囲みは多角形、線は「行って戻る」点列で塗りの濃さ 0 の多角形、文字は透明な 1 ブロックの多角形のラベル。
 * メモの id とオーバーレイの対応表を持ち、一覧の変化を差分で反映する。ディメンションが変わった時は removeAll する。
 */
final class MinimapOverlays {
    /** JourneyMap の多角形の y。地図は y を使わないので固定。 */
    private static final int Y = 64;

    private static final class Entry {
        final Memo memo;
        final List<PolygonOverlay> overlays;

        Entry(Memo memo, List<PolygonOverlay> overlays) {
            this.memo = memo;
            this.overlays = overlays;
        }
    }

    private final IClientAPI api;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private ResourceKey<Level> dimension;

    MinimapOverlays(IClientAPI api) {
        this.api = api;
    }

    /**
     * 表示を、今あるべき姿へ合わせる。
     *
     * @param memos     出すべきメモ（表示の全体切替がオフ・メモが使えない時は空にして呼ぶ）
     * @param dimension 今のディメンション。変わっていれば全部作り直す。ワールドの外なら null
     * @return ディメンションが切り替わっていたら true（取り消しの履歴を捨てる合図）
     */
    boolean sync(List<Memo> memos, ResourceKey<Level> dimension) {
        boolean switched = this.dimension != dimension;
        if (switched) {
            api.removeAll(MapScribble.MOD_ID);
            entries.clear();
            this.dimension = dimension;
        }
        if (dimension == null) {
            return switched;
        }

        Map<UUID, Memo> wanted = new HashMap<>();
        for (Memo memo : memos) {
            if (memo.onMinimap()) {
                wanted.put(memo.id(), memo);
            }
        }

        Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> e = it.next();
            Memo now = wanted.get(e.getKey());
            if (now == null || !now.equals(e.getValue().memo)) {
                for (PolygonOverlay overlay : e.getValue().overlays) {
                    api.remove(overlay);
                }
                it.remove();
            }
        }

        int order = 0;
        for (Memo memo : memos) {
            order++;
            if (!memo.onMinimap() || entries.containsKey(memo.id())) {
                continue;
            }
            List<PolygonOverlay> overlays = build(memo, dimension, order);
            List<PolygonOverlay> shown = new ArrayList<>();
            for (PolygonOverlay overlay : overlays) {
                try {
                    api.show(overlay);
                    shown.add(overlay);
                } catch (Exception ex) {
                    MapScribble.LOGGER.warn("ミニマップへのメモの表示に失敗しました id={}", memo.id(), ex);
                }
            }
            entries.put(memo.id(), new Entry(memo, shown));
        }
        return switched;
    }

    /** 全部消す（MOD の終了・ワールドを出る時）。 */
    void clear() {
        api.removeAll(MapScribble.MOD_ID);
        entries.clear();
        dimension = null;
    }

    int overlayCount() {
        int n = 0;
        for (Entry e : entries.values()) {
            n += e.overlays.size();
        }
        return n;
    }

    private List<PolygonOverlay> build(Memo memo, ResourceKey<Level> dim, int order) {
        List<PolygonOverlay> result = new ArrayList<>();
        boolean hasText = !memo.text().isBlank();
        switch (memo.kind()) {
            case REGION: {
                List<int[]> blocks = BlockPoints.round(memo.points(), true);
                if (blocks.size() >= 3) {
                    ShapeProperties shape = shape(memo, memo.fillOpacity());
                    PolygonOverlay overlay = polygon(dim, shape, blocks, order);
                    if (hasText) {
                        overlay.setLabel(label(memo.text()));
                        overlay.setTextProperties(textProperties(memo));
                    }
                    result.add(overlay);
                }
                break;
            }
            case LINE: {
                List<int[]> blocks = BlockPoints.round(memo.points(), false);
                List<int[]> loop = BlockPoints.outAndBack(blocks);
                if (loop.size() >= 3) {
                    result.add(polygon(dim, shape(memo, 0f), loop, order));
                }
                if (hasText) {
                    result.add(labelOverlay(memo, dim, order));
                }
                break;
            }
            case TEXT:
            default:
                if (hasText) {
                    result.add(labelOverlay(memo, dim, order));
                }
                break;
        }
        return result;
    }

    private static ShapeProperties shape(Memo memo, float fillOpacity) {
        return new ShapeProperties()
                .setStrokeColor(memo.color() & 0xFFFFFF)
                .setFillColor(memo.color() & 0xFFFFFF)
                .setStrokeOpacity(1f)
                .setFillOpacity(fillOpacity)
                .setStrokeWidth(MemoStyle.strokePixels(memo.width()));
    }

    /** 文字の位置に置く、線も塗りも透明な 1 ブロック四方の多角形。ラベルだけが見える。 */
    private static PolygonOverlay labelOverlay(Memo memo, ResourceKey<Level> dim, int order) {
        MapPoint anchor = MemoStyle.anchor(memo);
        int x = (int) Math.floor(anchor.x());
        int z = (int) Math.floor(anchor.z());
        List<int[]> square = List.of(new int[] {x, z}, new int[] {x + 1, z}, new int[] {x + 1, z + 1}, new int[] {x, z + 1});
        ShapeProperties invisible = new ShapeProperties().setStrokeOpacity(0f).setFillOpacity(0f).setStrokeWidth(0f);
        PolygonOverlay overlay = polygon(dim, invisible, square, order);
        overlay.setLabel(label(memo.text()));
        overlay.setTextProperties(textProperties(memo));
        return overlay;
    }

    private static PolygonOverlay polygon(ResourceKey<Level> dim, ShapeProperties shape, List<int[]> blocks, int order) {
        List<BlockPos> positions = new ArrayList<>(blocks.size());
        for (int[] b : blocks) {
            positions.add(new BlockPos(b[0], Y, b[1]));
        }
        PolygonOverlay overlay = new PolygonOverlay(MapScribble.MOD_ID, dim, shape, new MapPolygon(positions));
        JmCompat.showOnMinimapOnly(overlay);
        overlay.setDisplayOrder(order);
        return overlay;
    }

    private static TextProperties textProperties(Memo memo) {
        return new TextProperties()
                .setScale(memo.textScale())
                .setColor(memo.color() & 0xFFFFFF)
                .setOpacity(1f)
                .setBackgroundColor(MemoStyle.textBackgroundRgb(memo.color()))
                .setBackgroundOpacity(MemoStyle.TEXT_BACKGROUND_OPACITY)
                .setFontShadow(MemoStyle.textShadow(memo.color()));
    }

    /** JourneyMap のラベルは 1 行。改行は空白にする。 */
    private static String label(String text) {
        return text.replace('\n', ' ').strip();
    }
}
