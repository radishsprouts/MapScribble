package io.github.radishsprouts.mapscribble.test;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.jm.ScribbleBridge;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoFile;
import io.github.radishsprouts.mapscribble.store.MemoStore;
import io.github.radishsprouts.mapscribble.store.NotebookAccess;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.BandLayout;
import io.github.radishsprouts.mapscribble.ui.FillLevel;
import io.github.radishsprouts.mapscribble.ui.HintResolver;
import io.github.radishsprouts.mapscribble.ui.MenuLayout;
import io.github.radishsprouts.mapscribble.ui.NoteColor;
import io.github.radishsprouts.mapscribble.ui.NoticeTimer;
import io.github.radishsprouts.mapscribble.ui.NotebookScreen;
import io.github.radishsprouts.mapscribble.ui.ScribbleUi;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * 帯のメニュー（設計書 3.6）・保存先の画面（3.7）・説明の行の新しいお知らせ（3.9）の撮影。
 * 全画面の地図で手書きをオンにし、帯のメニュー（≡）から保存先の画面を開く本物の経路で撮る。
 * マルチプレイの接続先は gametest では作れないので、保存先の窓口は偽物（{@link NotebookScreenClientGameTest.FakeAccess}）を
 * {@code ScribbleUi.replaceNotebookAccessForTest} で差し込む（実際のマルチプレイの接続での見た目は未確認）。
 * 判定はしない（画像の良し悪しは Dev-QA と Dev-UI-Designer が見る）。場面が失敗しても、ログに残して次へ進む。
 * 経過は標準出力へ「[MS-SHOT] 」を付けて出す。写真の名前は {@code <言語>_gui<倍率>_<幅>x<高さ>_<2桁の番号>_<場面>.png}。
 */
@SuppressWarnings("UnstableApiUsage")
final class MenuNotebookShots {
    private static final String LONG_NAME = "とても長い名前をつけたメモ帳のサンプル（一覧で切れ方を見る）";

    private MenuNotebookShots() {
    }

    private static void log(String message) {
        System.out.println("[MS-SHOT] " + message);
    }

    /** 場面を1つ撮る。失敗はログに残して数える（次の場面へ進むため、画面を地図へ戻す）。 */
    private interface Scene {
        void run() throws Exception;
    }

    private static int failures;

    private static void scene(ClientGameTestContext ctx, Object[] holder, String prefix, String name, Scene scene) {
        try {
            scene.run();
        } catch (Throwable t) {
            failures++;
            log(prefix + ": 場面 " + name + " の撮影に失敗: " + t);
        } finally {
            try {
                backToMap(ctx, holder);
            } catch (Throwable t) {
                failures++;
                log(prefix + ": 場面 " + name + " の後に地図へ戻せない: " + t);
            }
        }
    }

    // ---- 入口 ----

    /**
     * 今の言語・画面の大きさ・倍率で、メニューと保存先の画面とお知らせを撮る。
     *
     * @return 失敗した場面の数
     */
    static int shoot(ClientGameTestContext ctx, Object[] holder, DrawingState state, String lang, int w, int h,
                     int scale) {
        failures = 0;
        String prefix = lang + "_gui" + scale + "_" + w + "x" + h;
        ScribbleUi ui = ctx.computeOnClient(client -> ScribbleBridge.instance().uiForTest());
        int[] originalBand = ctx.computeOnClient(client -> new int[] {state.bandX(), state.bandY()});
        Runnable[] restore = new Runnable[1];
        try {
            ScreenshotClientGameTest.reopenFullscreen(ctx, holder);
            ScreenshotClientGameTest.setMode(ctx, state, true, Tool.ENCLOSE);
            ctx.runOnClient(client -> state.setBandPosition(28, 44));
            ctx.waitTicks(3);
            NotebookScreenClientGameTest.FakeExtras plain = new NotebookScreenClientGameTest.FakeExtras();
            restore[0] = ctx.computeOnClient(client -> ui.replaceNotebookAccessForTest(() -> null, () -> plain));

            shootMenus(ctx, holder, state, ui, prefix);
            shootNotebooks(ctx, holder, state, ui, prefix, w, h);
            shootPreSplit(ctx, holder, state, prefix);
            shootNotices(ctx, holder, state, prefix);
        } finally {
            if (restore[0] != null) {
                Runnable r = restore[0];
                ctx.runOnClient(client -> r.run());
            }
            try {
                backToMap(ctx, holder);
                ctx.runOnClient(client -> {
                    state.setBandPosition(originalBand[0], originalBand[1]);
                    NoticeTimer.shared().clear();
                });
                ScreenshotClientGameTest.setMode(ctx, state, false, null);
            } catch (Throwable t) {
                failures++;
                log(prefix + ": メニュー・保存先の撮影の後始末に失敗: " + t);
            }
        }
        return failures;
    }

    // ---- メニュー（AC128〜137） ----

    private static void shootMenus(ClientGameTestContext ctx, Object[] holder, DrawingState state, ScribbleUi ui,
                                   String prefix) {
        // 分けている接続先（使用中のメモ帳の名前が補足）
        scene(ctx, holder, prefix, "20_menu_split", () -> {
            useAccess(ctx, ui, new NotebookScreenClientGameTest.FakeAccess(
                    NotebookScreenClientGameTest.splitState("id0", 4)), new NotebookScreenClientGameTest.FakeExtras());
            openMenu(ctx, holder, state, ui);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "20_menu_split");
            // 項目ホバー
            hoverRow(ctx, state);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "21_menu_hover_item", true);
            // ↓ キーで枠が付いた状態（マウスは外へ）
            ScreenshotClientGameTest.parkCursor(ctx);
            ctx.getInput().pressKey(InputConstants.KEY_DOWN);
            ctx.waitTicks(3);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "22_menu_key_selected");
            closeMenu(ctx, ui);
        });
        // 分けていない接続先
        scene(ctx, holder, prefix, "23_menu_unsplit", () -> {
            useAccess(ctx, ui, new NotebookScreenClientGameTest.FakeAccess(new NotebookAccess.State(
                    NotebookAccess.Mode.OFF, true, false, null, false, 0L, NotebookScreenClientGameTest.infos(0))),
                    new NotebookScreenClientGameTest.FakeExtras());
            openMenu(ctx, holder, state, ui);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "23_menu_unsplit");
            closeMenu(ctx, ui);
        });
        // シングルプレイ（灰色の項目と「このワールド専用」の補足。ホバーで理由の文）
        scene(ctx, holder, prefix, "24_menu_single", () -> {
            ctx.runOnClient(client -> ui.setNotebookAccess(() -> null, () -> new NotebookScreenClientGameTest.FakeExtras()));
            openMenu(ctx, holder, state, ui);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "24_menu_single");
            hoverRow(ctx, state);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "25_menu_single_hover", true);
            closeMenu(ctx, ui);
        });
        // 帯を端へ動かしてから開く（分けている接続先で）
        useAccess(ctx, ui, new NotebookScreenClientGameTest.FakeAccess(NotebookScreenClientGameTest.splitState("id0", 4)),
                new NotebookScreenClientGameTest.FakeExtras());
        int[] gui = guiSize(ctx);
        int[][] spots = {
                {28, gui[1] - BandLayout.HEIGHT},
                {0, 44},
                {gui[0] - BandLayout.WIDTH, 44}};
        String[] names = {"26_menu_band_bottom", "27_menu_band_left", "28_menu_band_right"};
        for (int i = 0; i < spots.length; i++) {
            int[] spot = spots[i];
            String name = names[i];
            scene(ctx, holder, prefix, name, () -> {
                ctx.runOnClient(client -> state.setBandPosition(spot[0], spot[1]));
                ctx.waitTicks(3);
                openMenu(ctx, holder, state, ui);
                ScreenshotClientGameTest.snap(ctx, holder, prefix, name);
                closeMenu(ctx, ui);
            });
        }
        ctx.runOnClient(client -> state.setBandPosition(28, 44));
        ctx.waitTicks(3);
    }

    // ---- 保存先の画面（AC138〜213） ----

    private static void shootNotebooks(ClientGameTestContext ctx, Object[] holder, DrawingState state, ScribbleUi ui,
                                       String prefix, int w, int h) {
        // 分けている通常（使用中の行）
        scene(ctx, holder, prefix, "30_notebook_split", () -> {
            NotebookScreen s = openNotebooks(ctx, holder, state, ui, access(NotebookScreenClientGameTest.splitState("id0", 4)),
                    new NotebookScreenClientGameTest.FakeExtras());
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "30_notebook_split", true);
            // 使用中の行にカーソル
            NotebookScreenClientGameTest.tabTo(ctx, s, "LIST");
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "31_notebook_cursor_in_use", true);
            // 名前の変更
            closeNotebook(ctx);
        });
        scene(ctx, holder, prefix, "32_notebook_rename", () -> {
            NotebookScreen s = openNotebooks(ctx, holder, state, ui, access(NotebookScreenClientGameTest.splitState("id0", 4)),
                    new NotebookScreenClientGameTest.FakeExtras());
            NotebookScreenClientGameTest.tabTo(ctx, s, "RENAME");
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "32_notebook_rename", true);
            // 別のメモ帳と同じ名前（検証の失敗）
            ctx.getInput().typeChars("メモ帳 1");
            ctx.waitTicks(2);
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "33_notebook_rename_duplicate", true);
            // 空欄
            for (int i = 0; i < 12; i++) {
                ctx.getInput().pressKey(InputConstants.KEY_BACKSPACE);
            }
            ctx.waitTicks(2);
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "34_notebook_rename_empty", true);
        });
        // 分けていない（0件）、分けていない（灰色の行あり）
        scene(ctx, holder, prefix, "35_notebook_unsplit_empty", () -> {
            openNotebooks(ctx, holder, state, ui, access(new NotebookAccess.State(NotebookAccess.Mode.OFF, true, false, null,
                    false, 0L, NotebookScreenClientGameTest.infos(0))), new NotebookScreenClientGameTest.FakeExtras());
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "35_notebook_unsplit_empty", true);
        });
        scene(ctx, holder, prefix, "36_notebook_unsplit_rows", () -> {
            openNotebooks(ctx, holder, state, ui, access(new NotebookAccess.State(NotebookAccess.Mode.OFF, true, false, null,
                    false, 0L, NotebookScreenClientGameTest.infos(8))), new NotebookScreenClientGameTest.FakeExtras());
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "36_notebook_unsplit_rows", true);
        });
        // 分けている接続先でワールドを見分けられない
        scene(ctx, holder, prefix, "37_notebook_no_world_id", () -> {
            openNotebooks(ctx, holder, state, ui, access(NotebookScreenClientGameTest.splitState(null, 4)),
                    new NotebookScreenClientGameTest.FakeExtras());
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "37_notebook_no_world_id", true);
        });
        // 移す確認画面（全部・見えている分）
        scene(ctx, holder, prefix, "38_notebook_confirm", () -> {
            NotebookScreenClientGameTest.FakeExtras extras = new NotebookScreenClientGameTest.FakeExtras();
            NotebookScreen s = openNotebooks(ctx, holder, state, ui,
                    access(NotebookScreenClientGameTest.splitState("id0", 4)), extras);
            NotebookScreenClientGameTest.tabTo(ctx, s, "MOVE_ALL");
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "38_notebook_confirm_all", true);
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_ESCAPE);
            NotebookScreenClientGameTest.tabTo(ctx, s, "MOVE_VISIBLE");
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "39_notebook_confirm_visible", true);
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_ESCAPE);
        });
        // 移している・移した後・失敗
        scene(ctx, holder, prefix, "40_notebook_moving", () -> {
            NotebookScreenClientGameTest.FakeExtras extras = new NotebookScreenClientGameTest.FakeExtras();
            NotebookScreen s = openNotebooks(ctx, holder, state, ui,
                    access(NotebookScreenClientGameTest.splitState("id0", 4)), extras);
            NotebookScreenClientGameTest.tabTo(ctx, s, "MOVE_ALL");
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "40_notebook_moving", true);
            ctx.runOnClient(client -> extras.finishMove());
            ctx.waitTicks(4);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "41_notebook_moved", true);
        });
        scene(ctx, holder, prefix, "42_notebook_move_failed", () -> {
            NotebookScreenClientGameTest.FakeExtras extras = new NotebookScreenClientGameTest.FakeExtras();
            NotebookScreen s = openNotebooks(ctx, holder, state, ui,
                    access(NotebookScreenClientGameTest.splitState("id0", 4)), extras);
            NotebookScreenClientGameTest.tabTo(ctx, s, "MOVE_ALL");
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            NotebookScreenClientGameTest.key(ctx, InputConstants.KEY_RETURN);
            ctx.runOnClient(client -> extras.failMove());
            ctx.waitTicks(4);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "42_notebook_move_failed", true);
        });
        // 読み込み中・読み込めない
        scene(ctx, holder, prefix, "43_notebook_loading", () -> {
            NotebookScreenClientGameTest.FakeExtras extras = new NotebookScreenClientGameTest.FakeExtras();
            extras.loading = true;
            openNotebooks(ctx, holder, state, ui, access(NotebookScreenClientGameTest.splitState("id0", 4)), extras);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "43_notebook_loading", true);
        });
        scene(ctx, holder, prefix, "44_notebook_load_failed", () -> {
            openNotebooks(ctx, holder, state, ui, access(new NotebookAccess.State(NotebookAccess.Mode.USER, false, false,
                    null, false, 0L, List.of())), new NotebookScreenClientGameTest.FakeExtras());
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "44_notebook_load_failed", true);
        });
        // 長い名前・999+ の件数
        scene(ctx, holder, prefix, "45_notebook_long_names", () -> {
            NotebookScreenClientGameTest.FakeExtras extras = new NotebookScreenClientGameTest.FakeExtras();
            extras.noteCount = 1500;
            extras.legacyTotal = 1500;
            extras.legacyHere = 1200;
            extras.legacyOther = 300;
            extras.legacyVisible = 1000;
            extras.address = "とても長い名前のサーバーの接続先.example.com:25565";
            List<NotebookAccess.Info> list = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                list.add(new NotebookAccess.Info("id" + i, i == 0 ? LONG_NAME : "メモ帳 " + i,
                        Instant.parse("2026-11-01T00:00:00Z"), "auto", List.of()));
            }
            openNotebooks(ctx, holder, state, ui, access(new NotebookAccess.State(NotebookAccess.Mode.USER, true, false,
                    "id0", true, 5L, list)), extras);
            ScreenshotClientGameTest.snap(ctx, holder, prefix, "45_notebook_long_names", true);
        });
    }

    // ---- 分ける前のメモの見分け（AC158・AC159。設計書 3.8） ----

    /**
     * 分ける前のメモ（分ける前のファイルにあるメモ）の、ホバーのツールチップの2行目と、編集の画面の見出しを撮る。
     * 本物のリポジトリ（{@link StoreSession#repository()}）を、使い捨ての主ファイルと分ける前のファイルで開き直して
     * {@code isLegacy} が true のメモを作り、終わったら元の保存先へ開き直す（撮影の間だけ差し替える）。
     */
    private static void shootPreSplit(ClientGameTestContext ctx, Object[] holder, DrawingState state, String prefix) {
        MemoStore store = (MemoStore) StoreSession.repository();
        Path origPrimary = store.primaryFileForTest();
        Path origLegacy = store.legacyFileForTest();
        Path dir = null;
        try {
            ScreenshotClientGameTest.reopenFullscreen(ctx, holder);
            Projection p = ScreenshotClientGameTest.projection(ctx, holder[0]);
            Memo legacyMemo = ScreenshotClientGameTest.memo(MemoKind.REGION,
                    ScreenshotClientGameTest.points(p, ScreenshotClientGameTest.REGION_A), NoteColor.RED.rgb(), 2,
                    FillLevel.NORMAL.opacity(), "村の倉庫", 1.5f);
            dir = Files.createTempDirectory("mapscribble-presplit");
            Path primaryFile = dir.resolve("primary.json");
            Path legacyFile = dir.resolve("legacy.json");
            Files.writeString(primaryFile, MemoFile.toJson(List.of()));
            Files.writeString(legacyFile, MemoFile.toJson(List.of(legacyMemo)));
            ctx.runOnClient(client -> store.open(primaryFile, legacyFile));
            ctx.waitTicks(6);
        } catch (Throwable t) {
            failures++;
            log(prefix + ": 分ける前のメモの撮影の準備に失敗: " + t);
            restoreStore(ctx, store, origPrimary, origLegacy, dir);
            return;
        }
        try {
            scene(ctx, holder, prefix, "58_presplit_hover_eraser", () -> {
                double[] inside = ScreenshotClientGameTest.regionInside(ScreenshotClientGameTest.projection(ctx, holder[0]));
                ScreenshotClientGameTest.setMode(ctx, state, true, Tool.ERASER);
                ctx.getInput().setCursorPos(inside[0], inside[1]);
                ctx.waitTicks(5);
                ScreenshotClientGameTest.snap(ctx, holder, prefix, "58_presplit_hover_eraser", true);
            });
            scene(ctx, holder, prefix, "59_presplit_edit_title", () -> {
                double[] inside = ScreenshotClientGameTest.regionInside(ScreenshotClientGameTest.projection(ctx, holder[0]));
                ScreenshotClientGameTest.setMode(ctx, state, true, Tool.EDIT);
                ctx.getInput().setCursorPos(inside[0], inside[1]);
                ctx.waitTicks(5);
                ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
                ctx.waitTicks(10);
                if (!ScreenshotClientGameTest.screenOf(ctx).endsWith(".NoteEditScreen")) {
                    throw new IllegalStateException("分ける前のメモを編集ツールで押しても編集の画面が開かない（画面: "
                            + ScreenshotClientGameTest.screenOf(ctx) + "）");
                }
                ScreenshotClientGameTest.snap(ctx, holder, prefix, "59_presplit_edit_title");
            });
        } finally {
            restoreStore(ctx, store, origPrimary, origLegacy, dir);
        }
    }

    /** 撮影の間だけ差し替えたリポジトリを、元の保存先へ開き直し、使い捨てのファイルを消す。 */
    private static void restoreStore(ClientGameTestContext ctx, MemoStore store, Path origPrimary, Path origLegacy,
                                     Path dir) {
        try {
            ctx.runOnClient(client -> {
                if (origPrimary != null) {
                    store.open(origPrimary, origLegacy);
                } else {
                    store.close();
                }
            });
            ctx.waitTicks(6);
        } catch (Throwable t) {
            failures++;
            log("分ける前のメモの撮影の後に、元の保存先へ開き直せない: " + t);
        }
        if (dir != null) {
            try (var files = Files.list(dir)) {
                for (Path f : files.toList()) {
                    Files.deleteIfExists(f);
                }
                Files.deleteIfExists(dir);
            } catch (IOException e) {
                log("使い捨てのファイルを消せない: " + dir + " " + e);
            }
        }
    }

    // ---- 説明の行の新しいお知らせ（AC160〜163。設計書 3.9） ----

    private static void shootNotices(ClientGameTestContext ctx, Object[] holder, DrawingState state, String prefix) {
        String[][] notices = {
                {"50_notice_notebook_switched", HintResolver.NOTICE_NOTEBOOK_SWITCHED, "メモ帳 1"},
                {"51_notice_notebook_switched_long", HintResolver.NOTICE_NOTEBOOK_SWITCHED, LONG_NAME},
                {"52_notice_split_started", HintResolver.NOTICE_SPLIT_STARTED, null},
                {"53_notice_no_world_id", HintResolver.NOTICE_NO_WORLD_ID, null},
                {"54_notice_moved", HintResolver.NOTICE_MOVED, "12"},
                {"55_notice_move_undone", HintResolver.NOTICE_MOVE_UNDONE, null},
                {"56_notice_move_failed", HintResolver.NOTICE_MOVE_FAILED, null},
                {"57_notice_jm010_imported", HintResolver.NOTICE_JM010_IMPORTED, null}};
        for (String[] n : notices) {
            scene(ctx, holder, prefix, n[0], () -> {
                ScreenshotClientGameTest.setMode(ctx, state, true, Tool.ENCLOSE);
                ctx.runOnClient(client -> {
                    NoticeTimer timer = NoticeTimer.shared();
                    timer.clear();
                    if (n[2] == null) {
                        timer.push(n[1], NoticeTimer.nowMs());
                    } else {
                        timer.push(n[1], NoticeTimer.nowMs(), n[2]);
                    }
                });
                ctx.waitTicks(4);
                ScreenshotClientGameTest.snap(ctx, holder, prefix, n[0]);
            });
        }
        ctx.runOnClient(client -> NoticeTimer.shared().clear());
    }

    // ---- 操作の部品 ----

    private static NotebookScreenClientGameTest.FakeAccess access(NotebookAccess.State st) {
        return new NotebookScreenClientGameTest.FakeAccess(st);
    }

    private static void useAccess(ClientGameTestContext ctx, ScribbleUi ui,
                                  NotebookScreenClientGameTest.FakeAccess access,
                                  NotebookScreenClientGameTest.FakeExtras extras) {
        ctx.runOnClient(client -> ui.setNotebookAccess(() -> access, () -> extras));
    }

    private static int[] guiSize(ClientGameTestContext ctx) {
        return ctx.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight()});
    }

    /** 地図の画面（手書きオン・メニューは閉じている）へ戻す。 */
    private static void backToMap(ClientGameTestContext ctx, Object[] holder) {
        for (int i = 0; i < 4; i++) {
            String name = ScreenshotClientGameTest.screenOf(ctx);
            if (name.endsWith(".Fullscreen")) {
                break;
            }
            if (name.equals("null")) {
                ScreenshotClientGameTest.ensureFullscreen(ctx, holder);
                break;
            }
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(4);
        }
        if (!ScreenshotClientGameTest.screenOf(ctx).endsWith(".Fullscreen")) {
            ScreenshotClientGameTest.reopenFullscreen(ctx, holder);
        }
        ScribbleUi ui = ctx.computeOnClient(client -> ScribbleBridge.instance().uiForTest());
        if (ctx.computeOnClient(client -> ui.isMenuOpen())) {
            closeMenu(ctx, ui);
        }
    }

    private static void openMenu(ClientGameTestContext ctx, Object[] holder, DrawingState state, ScribbleUi ui) {
        ScreenshotClientGameTest.setMode(ctx, state, true, Tool.ENCLOSE);
        ScreenshotClientGameTest.waitNoticeExpire(ctx);
        int[] band = ctx.computeOnClient(client -> new int[] {state.bandX(), state.bandY()});
        BandLayout.Cell cell = BandLayout.menuCell(band[0], band[1]);
        ScreenshotClientGameTest.hoverGui(ctx, cell.x() + cell.w() / 2.0, cell.y() + cell.h() / 2.0);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
        if (!ctx.computeOnClient(client -> ui.isMenuOpen())) {
            throw new IllegalStateException("メニューのボタン（≡）を押してもメニューが開かない");
        }
    }

    private static void closeMenu(ClientGameTestContext ctx, ScribbleUi ui) {
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        if (ctx.computeOnClient(client -> ui.isMenuOpen())) {
            // Esc がメニューへ届かなかった時は、メニューの外（画面の中ほど）をクリックして閉じる
            int[] size = ctx.computeOnClient(client -> new int[] {client.getWindow().getWidth(),
                    client.getWindow().getHeight()});
            ctx.getInput().setCursorPos(size[0] * 0.5, size[1] * 0.6);
            ctx.waitTicks(2);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(3);
        }
    }

    /** 開いているメニューの先頭の項目の上へマウスを置く。 */
    private static void hoverRow(ClientGameTestContext ctx, DrawingState state) {
        int[] band = ctx.computeOnClient(client -> new int[] {state.bandX(), state.bandY()});
        int[] gui = guiSize(ctx);
        MenuLayout.Rect rect = MenuLayout.place(band[0], band[1], 1, gui[0], gui[1]);
        ScreenshotClientGameTest.hoverGui(ctx, rect.x() + rect.w() / 2.0, MenuLayout.rowY(rect, 0)
                + MenuLayout.ROW_HEIGHT / 2.0);
    }

    /** メニューから保存先の画面を開き、マウスを画面の隅へ置く（ホバーが写らないように）。 */
    private static NotebookScreen openNotebooks(ClientGameTestContext ctx, Object[] holder, DrawingState state,
                                                ScribbleUi ui, NotebookScreenClientGameTest.FakeAccess access,
                                                NotebookScreenClientGameTest.FakeExtras extras) {
        useAccess(ctx, ui, access, extras);
        openMenu(ctx, holder, state, ui);
        hoverRow(ctx, state);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(6);
        NotebookScreen screen = ctx.computeOnClient(client -> ScreenCompat.currentScreen() instanceof NotebookScreen s
                ? s : null);
        if (screen == null) {
            throw new IllegalStateException("メニューの項目を押しても保存先の画面が開かない（画面: "
                    + ScreenshotClientGameTest.screenOf(ctx) + "）");
        }
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(3);
        return screen;
    }

    private static void closeNotebook(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(4);
    }
}
