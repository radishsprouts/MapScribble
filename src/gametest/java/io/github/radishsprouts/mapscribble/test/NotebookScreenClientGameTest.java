package io.github.radishsprouts.mapscribble.test;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.store.NotebookAccess;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.NotebookExtras;
import io.github.radishsprouts.mapscribble.ui.NotebookLayout;
import io.github.radishsprouts.mapscribble.ui.NotebookScreen;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 保存先の画面（設計書 3.7）の、作り直しで保つもの・押せない一覧の ↑↓・カーソルが無いまま押せる状態になった時を確かめる
 * （確かめる項目 AC195・AC202・AC205〜AC212。AC213 は実機）。偽の {@link NotebookAccess} と {@link NotebookExtras} で
 * {@link NotebookScreen} を開き、Tab・↓・Enter・窓の大きさ・GUI の倍率を変える。判定が外れたら AssertionError で落とす。
 * 経過は標準出力へ「[MS-NB] 」を付けて出す。
 *
 * <p>AC212（読み込み中に一覧へフォーカスを入れ、行が届く）は、本物の読み込み中の判定を
 * {@code StoreSession.setNotebookLoadingForTest} で作って確かめる。一覧の論理（フォーカス中に setRows で行が届く）は
 * 単体試験（NotebookListKeyNeedsMinecraftTest）でも確かめている。
 *
 * <p>終わると、変えた画面の大きさと GUI の倍率を元へ戻す。
 */
@SuppressWarnings("UnstableApiUsage")
public final class NotebookScreenClientGameTest {
    /** GUI 960x540（一覧 6 行）。 */
    private static final int BIG_W = 1920;
    private static final int BIG_H = 1080;
    private static final int BIG_SCALE = 2;
    /** GUI 427x240（一覧 3 行）。 */
    private static final int SMALL_W = 854;
    private static final int SMALL_H = 480;
    private static final int SMALL_SCALE = 2;
    /** 大きい窓のまま倍率だけ 4 にする（GUI 480x270、一覧 3 行）。 */
    private static final int BIG_SCALE_4 = 4;

    private static final NotebookAccess.Result OK = new NotebookAccess.Result(true, null);

    private NotebookScreenClientGameTest() {
    }

    private static void log(String message) {
        System.out.println("[MS-NB] " + message);
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError("[MS-NB] " + message);
        }
    }

    // ---- 偽の窓口 ----

    private static List<NotebookAccess.Info> infos(int count) {
        List<NotebookAccess.Info> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new NotebookAccess.Info("id" + i, "メモ帳 " + i, Instant.parse("2026-11-01T00:00:00Z"), "auto",
                    List.of()));
        }
        return list;
    }

    /** 分けている接続先。current が null ならワールドを見分けられない状態（使用中の行が無い）。 */
    private static NotebookAccess.State splitState(String current, int count) {
        return new NotebookAccess.State(NotebookAccess.Mode.USER, true, false, current, current != null,
                current == null ? 0L : 5L, infos(count));
    }

    private static final class FakeAccess implements NotebookAccess {
        private NotebookAccess.State state;
        final List<String> selected = new ArrayList<>();
        final List<String> renamed = new ArrayList<>();

        FakeAccess(NotebookAccess.State state) {
            this.state = state;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public NotebookAccess.State state() {
            return state;
        }

        @Override
        public void setSplit(boolean split, Consumer<NotebookAccess.Result> onDone) {
            state = new NotebookAccess.State(split ? NotebookAccess.Mode.USER : NotebookAccess.Mode.OFF,
                    state.registryReadable(), state.autoStopped(), state.currentNotebookId(), state.currentIsManual(),
                    state.currentSeedHash(), state.notebooks());
            onDone.accept(OK);
        }

        @Override
        public void create(String name, Consumer<NotebookAccess.Result> onDone) {
            onDone.accept(OK);
        }

        @Override
        public void rename(String id, String name, Consumer<NotebookAccess.Result> onDone) {
            renamed.add(id + "=" + name);
            List<NotebookAccess.Info> next = new ArrayList<>();
            for (NotebookAccess.Info info : state.notebooks()) {
                next.add(info.id().equals(id)
                        ? new NotebookAccess.Info(info.id(), name, info.created(), info.origin(), info.boundSeeds())
                        : info);
            }
            state = new NotebookAccess.State(state.mode(), state.registryReadable(), state.autoStopped(),
                    state.currentNotebookId(), state.currentIsManual(), state.currentSeedHash(), next);
            onDone.accept(OK);
        }

        @Override
        public void select(String id, boolean remember, Consumer<NotebookAccess.Result> onDone) {
            selected.add(id);
            state = new NotebookAccess.State(state.mode(), state.registryReadable(), state.autoStopped(), id, true,
                    state.currentSeedHash(), state.notebooks());
            onDone.accept(OK);
        }

        @Override
        public void moveLegacyToCurrent(Collection<UUID> ids, Consumer<NotebookAccess.Result> onDone) {
            onDone.accept(OK);
        }

        @Override
        public void addListener(Runnable listener) {
        }
    }

    /** 分ける前のメモが 3 件（見えている分は 2 件）ある偽の窓口。移す操作は finishMove まで保留する。 */
    private static final class FakeExtras implements NotebookExtras {
        boolean loading;
        boolean moving;
        boolean undoable;
        int undoCount;
        private Consumer<NotebookAccess.Result> pendingMove;

        @Override
        public String address() {
            return "テスト接続";
        }

        @Override
        public boolean isLoading() {
            return loading;
        }

        @Override
        public int noteCount(String notebookId) {
            return 1;
        }

        @Override
        public int legacyTotal() {
            return 3;
        }

        @Override
        public int legacyHere() {
            return 3;
        }

        @Override
        public int legacyOtherDimensions() {
            return 0;
        }

        @Override
        public int legacyVisible() {
            return 2;
        }

        @Override
        public Collection<UUID> legacyVisibleIds() {
            return List.of();
        }

        @Override
        public boolean isMoving() {
            return moving;
        }

        @Override
        public boolean canUndoMove() {
            return undoable;
        }

        @Override
        public void move(Collection<UUID> ids, Consumer<NotebookAccess.Result> onDone) {
            moving = true;
            pendingMove = onDone;
        }

        @Override
        public void undoMove(Consumer<NotebookAccess.Result> onDone) {
            undoCount++;
            onDone.accept(OK);
        }

        /** 保留していた移す操作を成功で終える（クライアントのスレッドから呼ぶ）。 */
        void finishMove() {
            moving = false;
            undoable = true;
            Consumer<NotebookAccess.Result> done = pendingMove;
            pendingMove = null;
            done.accept(OK);
        }
    }

    /** 保存先の画面の親にする、何も描かない画面。 */
    private static final class EmptyScreen extends Screen {
        EmptyScreen() {
            super(Component.empty());
        }
    }

    // ---- 操作と読み取り ----

    private static NotebookScreen open(ClientGameTestContext ctx, NotebookAccess access, NotebookExtras extras) {
        NotebookScreen[] holder = new NotebookScreen[1];
        ctx.setScreen(() -> {
            holder[0] = new NotebookScreen(new EmptyScreen(), access, extras, outcome -> {
            });
            return holder[0];
        });
        ctx.waitTicks(5);
        // マウスを一覧・ボタンの上に置かない（ホバーの面・ツールチップが出ないように）
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(2);
        return holder[0];
    }

    private static void closeScreen(ClientGameTestContext ctx) {
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    private static void key(ClientGameTestContext ctx, int code) {
        ctx.getInput().pressKey(code);
        ctx.waitTicks(2);
    }

    private static void keys(ClientGameTestContext ctx, int code, int times) {
        for (int i = 0; i < times; i++) {
            key(ctx, code);
        }
    }

    private static String focusOf(ClientGameTestContext ctx, NotebookScreen s) {
        return ctx.computeOnClient(client -> s.focusedPartName());
    }

    private static String cursorOf(ClientGameTestContext ctx, NotebookScreen s) {
        return ctx.computeOnClient(client -> String.valueOf(s.notebookList().cursorRowId()));
    }

    private static int scrollOf(ClientGameTestContext ctx, NotebookScreen s) {
        return ctx.computeOnClient(client -> s.notebookList().scrollOffset());
    }

    private static boolean isCurrent(ClientGameTestContext ctx, NotebookScreen s) {
        return ctx.computeOnClient(client -> ScreenCompat.currentScreen() == s);
    }

    private static String currentScreenName(ClientGameTestContext ctx) {
        return ctx.computeOnClient(client -> {
            Screen screen = ScreenCompat.currentScreen();
            return screen == null ? "null" : screen.getClass().getSimpleName();
        });
    }

    /** Tab を押して、その部品にフォーカスが届くまで進める（最大 12 回）。 */
    private static void tabTo(ClientGameTestContext ctx, NotebookScreen s, String part) {
        for (int i = 0; i < 12; i++) {
            if (focusOf(ctx, s).equals(part)) {
                return;
            }
            key(ctx, InputConstants.KEY_TAB);
        }
        check(focusOf(ctx, s).equals(part), "Tab で " + part + " にフォーカスが届かない（今は " + focusOf(ctx, s) + "）");
    }

    private static void display(ClientGameTestContext ctx, int w, int h, int scale) {
        int actual = ScreenshotClientGameTest.applyDisplay(ctx, w, h, scale);
        check(actual == scale, "GUI 倍率が頭打ちになった: 要求 " + scale + " 実際 " + actual + "（" + w + "x" + h + "）");
    }

    private static void big(ClientGameTestContext ctx) {
        display(ctx, BIG_W, BIG_H, BIG_SCALE);
    }

    private static void small(ClientGameTestContext ctx) {
        display(ctx, SMALL_W, SMALL_H, SMALL_SCALE);
    }

    /** 一覧の中央へマウスを置く（ホイールを一覧へ届ける）。 */
    private static void mouseOnList(ClientGameTestContext ctx, NotebookScreen s) {
        double[] p = ctx.computeOnClient(client -> {
            double scale = client.getWindow().getGuiScale();
            return new double[] {(s.notebookList().getX() + s.notebookList().getWidth() / 2.0) * scale,
                    (s.notebookList().getY() + s.notebookList().getHeight() / 2.0) * scale};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(2);
    }

    /** ホイールで下へスクロールする向き（scroll に渡す符号）を、実際に動かして決める。1 行進んだ状態で返る。 */
    private static double wheelDownDirection(ClientGameTestContext ctx, NotebookScreen s) {
        mouseOnList(ctx, s);
        int before = scrollOf(ctx, s);
        ctx.getInput().scroll(-1);
        ctx.waitTicks(2);
        if (scrollOf(ctx, s) > before) {
            return -1;
        }
        ctx.getInput().scroll(1);
        ctx.waitTicks(2);
        check(scrollOf(ctx, s) > before, "ホイールで一覧がスクロールしない（scroll=" + scrollOf(ctx, s) + "）");
        return 1;
    }

    private static void wheelDownTo(ClientGameTestContext ctx, NotebookScreen s, double direction, int target) {
        mouseOnList(ctx, s);
        for (int i = 0; i < 20 && scrollOf(ctx, s) < target; i++) {
            ctx.getInput().scroll(direction);
            ctx.waitTicks(2);
        }
        check(scrollOf(ctx, s) == target, "ホイールで scroll=" + target + " にできない（今は " + scrollOf(ctx, s) + "）");
    }

    // ---- 入口 ----

    /** ワールドが開いて描画を待ち終わった状態から始める。 */
    public static void run(ClientGameTestContext ctx) {
        int[] saved = ctx.computeOnClient(client -> new int[] {client.options.guiScale().get(),
                client.getWindow().getWidth(), client.getWindow().getHeight()});
        try {
            big(ctx);
            ac195(ctx);
            ac205(ctx);
            ac206(ctx);
            ac207(ctx);
            ac208(ctx);
            ac209(ctx);
            ac210(ctx);
            ac211(ctx);
            ac202(ctx);
            // AC212 は、画面が読み込みの完了を受けて行を出す口を持たない間は落ちる（設計書どおりに作る側の不足を示す）。
            // 他の項目の結果を先に出すため、最後に置く
            ac212(ctx);
            log("保存先の画面の試験が最後まで通った（AC195・AC202・AC205〜AC212）");
        } finally {
            closeScreen(ctx);
            ScreenshotClientGameTest.applyDisplay(ctx, saved[1], saved[2], saved[0]);
        }
    }

    // ---- AC195: 使用中の行が無い状態で、Tab で入ると先頭の行にカーソルが出る ----

    private static void ac195(ClientGameTestContext ctx) {
        big(ctx);
        FakeAccess access = new FakeAccess(splitState(null, 4));
        NotebookScreen s = open(ctx, access, new FakeExtras());
        tabTo(ctx, s, "LIST");
        check(cursorOf(ctx, s).equals("id0"), "AC195: 使用中の行が無い時、Tab で入ったカーソルは先頭の行のはず: " + cursorOf(ctx, s));
        ctx.waitTicks(3);
        try {
            checkCursorDrawn(ctx, s);
        } catch (IOException e) {
            throw new AssertionError("[MS-NB] AC195: スクリーンショットを読めない: " + e, e);
        }
        key(ctx, InputConstants.KEY_RETURN);
        check(access.selected.equals(List.of("id0")), "AC195: Enter で select が id0 で呼ばれるはず: " + access.selected);
        closeScreen(ctx);
        log("AC195 通った");
    }

    /** スクリーンショットで、先頭の行の左の縁が白く、2 行目は白くないこと。 */
    private static void checkCursorDrawn(ClientGameTestContext ctx, NotebookScreen s) throws IOException {
        Path shot = ctx.takeScreenshot("notebook_ac195");
        BufferedImage image = ImageIO.read(shot.toFile());
        int[] at = ctx.computeOnClient(client -> {
            int scale = (int) client.getWindow().getGuiScale();
            int x = (s.notebookList().getX() + NotebookLayout.ROW_INNER_PAD) * scale;
            int y = (s.notebookList().getY() + NotebookLayout.LIST_PAD + NotebookLayout.ROW_HEIGHT / 2) * scale;
            return new int[] {x, y, NotebookLayout.ROW_HEIGHT * scale};
        });
        check(at[0] < image.getWidth() && at[1] + at[2] < image.getHeight(),
                "AC195: スクリーンショットの大きさが合わない " + image.getWidth() + "x" + image.getHeight());
        check(isWhite(image.getRGB(at[0], at[1])), "AC195: 先頭の行の左の縁が白く描かれていない: "
                + Integer.toHexString(image.getRGB(at[0], at[1])));
        check(!isWhite(image.getRGB(at[0], at[1] + at[2])), "AC195: 2 行目の左の縁まで白い");
    }

    private static boolean isWhite(int argb) {
        return ((argb >> 16) & 0xFF) >= 0xF0 && ((argb >> 8) & 0xFF) >= 0xF0 && (argb & 0xFF) >= 0xF0;
    }

    // ---- AC205: 窓の大きさ・倍率の変更で、一覧のフォーカスとカーソルが残る ----

    private static void ac205(ClientGameTestContext ctx) {
        big(ctx);
        NotebookScreen s = open(ctx, new FakeAccess(splitState("id0", 8)), new FakeExtras());
        tabTo(ctx, s, "LIST");
        check(cursorOf(ctx, s).equals("id0"), "AC205: 入った時のカーソルは使用中の行: " + cursorOf(ctx, s));
        keys(ctx, InputConstants.KEY_DOWN, 6);
        verifyKept(ctx, s, 6, "AC205 作り直しの前");

        small(ctx);
        verifyKept(ctx, s, 6, "AC205 窓 (d)→(a)");
        big(ctx);
        verifyKept(ctx, s, 6, "AC205 窓 (a)→(d)");
        display(ctx, BIG_W, BIG_H, BIG_SCALE_4);
        verifyKept(ctx, s, 6, "AC205 倍率 2→4");
        big(ctx);
        verifyKept(ctx, s, 6, "AC205 倍率 4→2");
        closeScreen(ctx);
        log("AC205 通った");
    }

    /** フォーカスが一覧にあり、カーソルが id<expected> の行で見える位置にあり、↓ で次へ・↑ で戻る。 */
    private static void verifyKept(ClientGameTestContext ctx, NotebookScreen s, int expected, String label) {
        check(focusOf(ctx, s).equals("LIST"), label + ": フォーカスが一覧に無い: " + focusOf(ctx, s));
        check(cursorOf(ctx, s).equals("id" + expected), label + ": カーソルが id" + expected + " に無い: " + cursorOf(ctx, s));
        int[] v = ctx.computeOnClient(client -> new int[] {s.notebookList().scrollOffset(),
                s.notebookList().visibleRowCount()});
        check(expected >= v[0] && expected < v[0] + v[1],
                label + ": カーソルの行が見える位置に無い（scroll=" + v[0] + " 表示 " + v[1] + " 行）");
        key(ctx, InputConstants.KEY_DOWN);
        check(cursorOf(ctx, s).equals("id" + (expected + 1)), label + ": 直後の ↓ でカーソルが次へ動かない: " + cursorOf(ctx, s));
        key(ctx, InputConstants.KEY_UP);
        check(cursorOf(ctx, s).equals("id" + expected), label + ": ↑ で戻らない: " + cursorOf(ctx, s));
    }

    // ---- AC206: 一覧の外の部品のフォーカス、フォーカスが無い状態 ----

    private static void ac206(ClientGameTestContext ctx) {
        for (String part : List.of("SPLIT", "NEW", "DONE")) {
            big(ctx);
            NotebookScreen s = open(ctx, new FakeAccess(splitState("id0", 4)), new FakeExtras());
            tabTo(ctx, s, part);
            small(ctx);
            check(focusOf(ctx, s).equals(part), "AC206: 窓 (d)→(a) でフォーカスが " + part + " から移った: " + focusOf(ctx, s));
            display(ctx, SMALL_W, SMALL_H, 1);
            check(focusOf(ctx, s).equals(part), "AC206: 倍率 2→1 でフォーカスが " + part + " から移った: " + focusOf(ctx, s));
            closeScreen(ctx);
        }
        // どの部品にもフォーカスが無い（マウスで開いた直後）。最後の入力をキーボードにしても、窓の変更でフォーカスを付けない
        big(ctx);
        NotebookScreen s = open(ctx, new FakeAccess(splitState("id0", 4)), new FakeExtras());
        ctx.getInput().setCursorPos(2, 2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
        ctx.runOnClient(client -> s.setFocused(null));
        ctx.waitTicks(2);
        check(focusOf(ctx, s).equals("NONE"), "AC206: 前提の、フォーカス無しの状態を作れない: " + focusOf(ctx, s));
        key(ctx, InputConstants.KEY_LSHIFT);
        small(ctx);
        check(focusOf(ctx, s).equals("NONE"), "AC206: フォーカス無しのまま窓を変えたら部品にフォーカスが付いた: " + focusOf(ctx, s));
        display(ctx, SMALL_W, SMALL_H, 1);
        check(focusOf(ctx, s).equals("NONE"), "AC206: フォーカス無しのまま倍率を変えたら部品にフォーカスが付いた: " + focusOf(ctx, s));
        closeScreen(ctx);
        log("AC206 通った");
    }

    // ---- AC207: スクロールを新しい範囲へ収める ----

    private static void ac207(ClientGameTestContext ctx) {
        big(ctx);
        NotebookScreen s = open(ctx, new FakeAccess(splitState("id0", 8)), new FakeExtras());
        tabTo(ctx, s, "NEW");
        double down = wheelDownDirection(ctx, s);
        wheelDownTo(ctx, s, down, 2);
        small(ctx);
        check(scrollOf(ctx, s) == 2, "AC207: 一覧にフォーカスが無い時、作り直しの前の一番上の行（2）を保つはず: " + scrollOf(ctx, s));
        wheelDownTo(ctx, s, down, 5);
        big(ctx);
        check(scrollOf(ctx, s) == 2, "AC207: 6 行へ戻すと範囲（最大 2）へ収まるはず: " + scrollOf(ctx, s));
        int[] v = ctx.computeOnClient(client -> new int[] {s.notebookList().scrollOffset(),
                s.notebookList().visibleRowCount(), s.notebookList().rowCount()});
        check(v[0] + v[1] <= v[2], "AC207: 行の無い空白が一覧に出る（scroll=" + v[0] + " 表示 " + v[1] + " 件 " + v[2] + "）");
        closeScreen(ctx);
        log("AC207 通った");
    }

    // ---- AC208: 名前の変更中の入力を保つ ----

    private static void ac208(ClientGameTestContext ctx) {
        big(ctx);
        FakeAccess access = new FakeAccess(splitState("id0", 4));
        NotebookScreen s = open(ctx, access, new FakeExtras());
        tabTo(ctx, s, "RENAME");
        key(ctx, InputConstants.KEY_RETURN);
        check(ctx.computeOnClient(client -> s.isRenaming()), "AC208: 「名前を変える」の Enter で名前の変更が始まらない");
        check(focusOf(ctx, s).equals("EDIT"), "AC208: 入力欄にフォーカスが無い: " + focusOf(ctx, s));
        ctx.getInput().typeChars("あい");
        ctx.waitTicks(2);
        key(ctx, InputConstants.KEY_LEFT);
        ctx.runOnClient(client -> s.renameBox().setHighlightPos(2));
        ctx.waitTicks(2);
        String[] before = editState(ctx, s);

        small(ctx);
        checkEdit(ctx, s, before, "AC208 窓 (d)→(a)");
        display(ctx, SMALL_W, SMALL_H, 1);
        checkEdit(ctx, s, before, "AC208 倍率 2→1");
        big(ctx);
        checkEdit(ctx, s, before, "AC208 窓 (a)→(d)");

        key(ctx, InputConstants.KEY_RETURN);
        check(access.renamed.equals(List.of("id0=" + before[0])),
                "AC208: Enter で打った名前が保存されない: " + access.renamed + " 期待 " + before[0]);
        check(focusOf(ctx, s).equals("RENAME"), "AC208: 決めた後のフォーカスが始めたボタンに戻らない: " + focusOf(ctx, s));
        closeScreen(ctx);
        log("AC208 通った");
    }

    /** 入力欄の {文字, カーソルの位置, 選んでいる文字}。 */
    private static String[] editState(ClientGameTestContext ctx, NotebookScreen s) {
        return ctx.computeOnClient(client -> new String[] {s.renameBox().getValue(),
                String.valueOf(s.renameBox().getCursorPosition()), s.renameBox().getHighlighted()});
    }

    private static void checkEdit(ClientGameTestContext ctx, NotebookScreen s, String[] before, String label) {
        check(ctx.computeOnClient(client -> s.isRenaming()), label + ": 名前の変更が終わっている");
        check(ctx.computeOnClient(client -> s.renameBox().visible), label + ": 入力欄が見えない");
        check(focusOf(ctx, s).equals("EDIT"), label + ": 入力欄にフォーカスが無い: " + focusOf(ctx, s));
        String[] after = editState(ctx, s);
        check(after[0].equals(before[0]), label + ": 打った文字が変わった: " + after[0] + " 前 " + before[0]);
        check(after[1].equals(before[1]), label + ": カーソルの位置が変わった: " + after[1] + " 前 " + before[1]);
        check(after[2].equals(before[2]), label + ": 選んでいる範囲が変わった: " + after[2] + " 前 " + before[2]);
        boolean onRow = ctx.computeOnClient(client -> {
            int[] bounds = s.notebookList().inUseRowBounds();
            return bounds != null && s.renameBox().getY() == bounds[1];
        });
        check(onRow, label + ": 入力欄が使用中の行に重なっていない");
    }

    // ---- AC209: 確認画面から戻ると、開いたボタンへフォーカスが戻る ----

    private static void ac209(ClientGameTestContext ctx) {
        for (String via : List.of("ESC", "YES", "CANCEL")) {
            big(ctx);
            FakeExtras extras = new FakeExtras();
            NotebookScreen s = open(ctx, new FakeAccess(splitState("id0", 8)), extras);
            tabTo(ctx, s, "MOVE_ALL");
            int scrollBefore = scrollOf(ctx, s);
            key(ctx, InputConstants.KEY_RETURN);
            check(currentScreenName(ctx).equals("ConfirmScreen"), "AC209(" + via + "): 確認画面が開かない: " + currentScreenName(ctx));
            if (via.equals("ESC")) {
                key(ctx, InputConstants.KEY_ESCAPE);
            } else if (via.equals("YES")) {
                key(ctx, InputConstants.KEY_RETURN);
            } else {
                key(ctx, InputConstants.KEY_TAB);
                key(ctx, InputConstants.KEY_RETURN);
            }
            ctx.waitTicks(3);
            check(isCurrent(ctx, s), "AC209(" + via + "): 保存先の画面へ戻らない: " + currentScreenName(ctx));
            check(focusOf(ctx, s).equals("MOVE_ALL"),
                    "AC209(" + via + "): フォーカスが確認画面を開いたボタンへ戻らない: " + focusOf(ctx, s));
            check(scrollOf(ctx, s) == scrollBefore, "AC209(" + via + "): 一覧のスクロールが変わった");
            if (via.equals("YES")) {
                check(extras.moving, "AC209: 「移す」で移す操作が始まらない");
                check(focusOf(ctx, s).equals("MOVE_ALL"), "AC209: 移している間にフォーカスが移った: " + focusOf(ctx, s));
                ctx.runOnClient(client -> extras.finishMove());
                ctx.waitTicks(3);
                check(focusOf(ctx, s).equals("MOVE_ALL"), "AC209: 移し終えた後にフォーカスが移った: " + focusOf(ctx, s));
                key(ctx, InputConstants.KEY_RETURN);
                check(extras.undoCount == 1, "AC209: 移し終えた後の Enter で「元に戻す」が効かない: " + extras.undoCount);
            }
            closeScreen(ctx);
        }
        log("AC209 通った");
    }

    // ---- AC210: 押せない時・行が無い時も、↑↓ をいつも一覧が受け取る ----

    private static void ac210(ClientGameTestContext ctx) {
        big(ctx);
        NotebookAccess.State unsplit2 = new NotebookAccess.State(NotebookAccess.Mode.OFF, true, false, null, false, 0L,
                infos(2));
        NotebookAccess.State unsplit8 = new NotebookAccess.State(NotebookAccess.Mode.OFF, true, false, null, false, 0L,
                infos(8));
        NotebookAccess.State failed = new NotebookAccess.State(NotebookAccess.Mode.USER, false, false, null, false, 0L,
                List.of());
        arrowsStay(ctx, "(1) 灰色の行が表示の数以下", new FakeAccess(unsplit2), new FakeExtras(), 0);
        arrowsStay(ctx, "(1) 灰色の行が表示の数を超える", new FakeAccess(unsplit8), new FakeExtras(), 2);
        FakeExtras loading = new FakeExtras();
        loading.loading = true;
        arrowsStay(ctx, "(3) 読み込み中", new FakeAccess(splitState("id0", 4)), loading, 0);
        arrowsStay(ctx, "(4) 一覧を読み込めない", new FakeAccess(failed), new FakeExtras(), 0);

        // (2) 移している間（行が表示の数を超える）
        FakeExtras extras = new FakeExtras();
        NotebookScreen s = open(ctx, new FakeAccess(splitState("id0", 8)), extras);
        tabTo(ctx, s, "MOVE_ALL");
        key(ctx, InputConstants.KEY_RETURN);
        key(ctx, InputConstants.KEY_RETURN);
        check(extras.moving, "AC210(2): 移す操作が始まらない");
        // 移している間は完了を含めボタンがすべて押せない（設計書 695 行）ので、Shift+Tab でも一覧に残るのが正しい
        arrowsOn(ctx, s, "(2) 移している間", 2, false);
        ctx.runOnClient(client -> extras.finishMove());
        closeScreen(ctx);

        // (5) 押せる一覧の端
        s = open(ctx, new FakeAccess(splitState("id0", 4)), new FakeExtras());
        tabTo(ctx, s, "LIST");
        keys(ctx, InputConstants.KEY_UP, 5);
        check(focusOf(ctx, s).equals("LIST"), "AC210(5): 先頭で ↑ を押したらフォーカスが一覧から出た: " + focusOf(ctx, s));
        check(cursorOf(ctx, s).equals("id0"), "AC210(5): 先頭で ↑ を押したらカーソルが動いた: " + cursorOf(ctx, s));
        keys(ctx, InputConstants.KEY_DOWN, 10);
        check(focusOf(ctx, s).equals("LIST"), "AC210(5): 末尾で ↓ を押したらフォーカスが一覧から出た: " + focusOf(ctx, s));
        check(cursorOf(ctx, s).equals("id3"), "AC210(5): 末尾のカーソルは id3 のはず: " + cursorOf(ctx, s));
        closeScreen(ctx);
        log("AC210 通った");
    }

    private static void arrowsStay(ClientGameTestContext ctx, String label, FakeAccess access, FakeExtras extras,
                                   int expectedScrollAfterDown) {
        NotebookScreen s = open(ctx, access, extras);
        tabTo(ctx, s, "LIST");
        arrowsOn(ctx, s, label, expectedScrollAfterDown, true);
        closeScreen(ctx);
    }

    /**
     * 一覧にフォーカスがある画面で ↓ ↑ をそれぞれ 5 回押し、フォーカスが出ないことを確かめる。
     * Shift+Tab は、一覧の外に押せる部品がある時（canLeave）は一覧から出られること、無い時（移している間）は一覧に残ることを確かめる。
     */
    private static void arrowsOn(ClientGameTestContext ctx, NotebookScreen s, String label, int expectedScrollAfterDown,
                                 boolean canLeave) {
        if (!focusOf(ctx, s).equals("LIST")) {
            tabTo(ctx, s, "LIST");
        }
        for (int i = 0; i < 5; i++) {
            key(ctx, InputConstants.KEY_DOWN);
            check(focusOf(ctx, s).equals("LIST"), "AC210" + label + ": ↓ でフォーカスが一覧から出た: " + focusOf(ctx, s));
        }
        check(scrollOf(ctx, s) == expectedScrollAfterDown,
                "AC210" + label + ": ↓ 5 回の後の scroll は " + expectedScrollAfterDown + " のはず: " + scrollOf(ctx, s));
        for (int i = 0; i < 5; i++) {
            key(ctx, InputConstants.KEY_UP);
            check(focusOf(ctx, s).equals("LIST"), "AC210" + label + ": ↑ でフォーカスが一覧から出た: " + focusOf(ctx, s));
        }
        check(scrollOf(ctx, s) == 0, "AC210" + label + ": ↑ 5 回の後の scroll は 0 のはず: " + scrollOf(ctx, s));
        ctx.getInput().holdShift();
        key(ctx, InputConstants.KEY_TAB);
        ctx.getInput().releaseShift();
        if (canLeave) {
            check(!focusOf(ctx, s).equals("LIST"), "AC210" + label + ": Shift+Tab で一覧から出られない");
        } else {
            check(focusOf(ctx, s).equals("LIST"),
                    "AC210" + label + ": 押せる部品が無いのに Shift+Tab でフォーカスが一覧から出た: " + focusOf(ctx, s));
        }
    }

    // ---- AC211: 移し終えた時に、フォーカス中の一覧へカーソルが置かれる ----

    private static void ac211(ClientGameTestContext ctx) {
        big(ctx);
        FakeExtras extras = new FakeExtras();
        NotebookScreen s = open(ctx, new FakeAccess(splitState("id0", 8)), extras);
        tabTo(ctx, s, "LIST");
        keys(ctx, InputConstants.KEY_DOWN, 3);
        check(cursorOf(ctx, s).equals("id3"), "AC211: 前提の、移す前のカーソル（id3）を置けない: " + cursorOf(ctx, s));
        tabTo(ctx, s, "MOVE_ALL");
        key(ctx, InputConstants.KEY_RETURN);
        key(ctx, InputConstants.KEY_RETURN);
        check(extras.moving, "AC211: 移す操作が始まらない");
        tabTo(ctx, s, "LIST");
        check(cursorOf(ctx, s).equals("null"), "AC211: 移している間は一覧にカーソルが出ないはず: " + cursorOf(ctx, s));
        keys(ctx, InputConstants.KEY_DOWN, 7);
        check(cursorOf(ctx, s).equals("null"), "AC211: 移している間に ↓ を押してもカーソルが出ないはず: " + cursorOf(ctx, s));
        check(scrollOf(ctx, s) == 2, "AC211: 移している間の ↓ で一覧がスクロールするはず: " + scrollOf(ctx, s));
        ctx.runOnClient(client -> extras.finishMove());
        ctx.waitTicks(3);
        check(focusOf(ctx, s).equals("LIST"), "AC211: 移し終えた時にフォーカスが一覧から移った: " + focusOf(ctx, s));
        check(cursorOf(ctx, s).equals("id0"), "AC211: 移し終えたら使用中の行（id0）にカーソルが置かれるはず（id3 には戻らない）: "
                + cursorOf(ctx, s));
        check(scrollOf(ctx, s) == 0, "AC211: 使用中の行が見える位置へ戻るはず: " + scrollOf(ctx, s));
        key(ctx, InputConstants.KEY_DOWN);
        check(cursorOf(ctx, s).equals("id1"), "AC211: 最初の ↓ は使用中の次の行（id1）へ動くはず: " + cursorOf(ctx, s));
        closeScreen(ctx);
        log("AC211 通った");
    }

    // ---- AC202（移している間・分けていない接続先）: 一覧にカーソルが出ず、↑↓ は 1 行ずつスクロールし、Enter は何も変えない ----

    private static void ac202(ClientGameTestContext ctx) {
        big(ctx);
        // 移している間（偽の窓口が移す操作を保留している。画面の「移す」の経路で moving になる）
        FakeAccess access = new FakeAccess(splitState("id0", 8));
        FakeExtras extras = new FakeExtras();
        NotebookScreen s = open(ctx, access, extras);
        tabTo(ctx, s, "MOVE_ALL");
        key(ctx, InputConstants.KEY_RETURN);
        key(ctx, InputConstants.KEY_RETURN);
        check(extras.moving, "AC202: 移す操作が始まらない");
        try {
            tabTo(ctx, s, "LIST");
            check(cursorOf(ctx, s).equals("null"), "AC202(移している間): 一覧にフォーカスがあってもカーソルは出ないはず: " + cursorOf(ctx, s));
            for (int i = 1; i <= 3; i++) {
                key(ctx, InputConstants.KEY_DOWN);
                check(scrollOf(ctx, s) == Math.min(i, 2), "AC202(移している間): ↓ " + i + " 回目で 1 行ずつスクロールするはず: "
                        + scrollOf(ctx, s));
                check(cursorOf(ctx, s).equals("null"), "AC202(移している間): ↓ でカーソルが出た: " + cursorOf(ctx, s));
            }
            key(ctx, InputConstants.KEY_UP);
            check(scrollOf(ctx, s) == 1, "AC202(移している間): ↑ で 1 行戻るはず: " + scrollOf(ctx, s));
            key(ctx, InputConstants.KEY_RETURN);
            check(access.selected.isEmpty(), "AC202(移している間): Enter でメモ帳が切り替わった: " + access.selected);
            check(cursorOf(ctx, s).equals("null"), "AC202(移している間): Enter でカーソルが出た: " + cursorOf(ctx, s));
            check(scrollOf(ctx, s) == 1, "AC202(移している間): Enter でスクロールが変わった: " + scrollOf(ctx, s));
        } finally {
            ctx.runOnClient(client -> extras.finishMove());
            closeScreen(ctx);
        }

        // 分けていない接続先で、灰色の行が出ている時（行が表示の数を超える）
        NotebookAccess.State unsplit8 = new NotebookAccess.State(NotebookAccess.Mode.OFF, true, false, null, false, 0L,
                infos(8));
        FakeAccess plain = new FakeAccess(unsplit8);
        s = open(ctx, plain, new FakeExtras());
        tabTo(ctx, s, "LIST");
        check(cursorOf(ctx, s).equals("null"), "AC202(分けていない): 灰色の行にカーソルが出た: " + cursorOf(ctx, s));
        keys(ctx, InputConstants.KEY_DOWN, 2);
        check(scrollOf(ctx, s) == 2, "AC202(分けていない): ↓ 2 回で 2 行スクロールするはず: " + scrollOf(ctx, s));
        key(ctx, InputConstants.KEY_RETURN);
        check(plain.selected.isEmpty(), "AC202(分けていない): Enter でメモ帳が切り替わった: " + plain.selected);
        check(cursorOf(ctx, s).equals("null"), "AC202(分けていない): Enter でカーソルが出た: " + cursorOf(ctx, s));
        closeScreen(ctx);
        log("AC202 通った");
    }

    // ---- AC212: 読み込み中に一覧へフォーカスを入れ、そのまま行が届くと、使用中の行にカーソルが出る ----

    /**
     * 読み込み中の状態は、本物の窓口（{@code StoreSession.notebookExtras()}）の読み込み中を
     * {@code StoreSession.setNotebookLoadingForTest} で作り、解く時は取り込みの完了と同じ通知が出る。
     * 偽の {@link NotebookAccess} は、行（使用中は id1）を初めから持つ。読み込み中は画面が行を出さず、解けたら行が届く。
     * 解いた後に操作を一切せずに待ち、画面が自分で行を出すかを見る（実機で読み込みが終わる時と同じ）。
     */
    private static void ac212(ClientGameTestContext ctx) {
        big(ctx);
        FakeAccess access = new FakeAccess(splitState("id1", 4));
        NotebookExtras real = ctx.computeOnClient(client -> StoreSession.notebookExtras());
        ctx.runOnClient(client -> StoreSession.setNotebookLoadingForTest(true));
        try {
            NotebookScreen s = open(ctx, access, real);
            check(ctx.computeOnClient(client -> s.notebookList().rowCount()) == 0,
                    "AC212: 読み込み中は一覧に行が出ないはず");
            tabTo(ctx, s, "LIST");
            check(cursorOf(ctx, s).equals("null"), "AC212: 読み込み中はカーソルが出ないはず: " + cursorOf(ctx, s));

            ctx.runOnClient(client -> StoreSession.setNotebookLoadingForTest(false));
            ctx.waitTicks(10);
            int rows = ctx.computeOnClient(client -> s.notebookList().rowCount());
            check(rows == 4, "AC212: 読み込みが終わっても行が届かない（行 " + rows
                    + "）。画面が読み込みの完了を受ける口を持たず、操作があるまで行を出さない");
            check(focusOf(ctx, s).equals("LIST"), "AC212: 行が届いた時にフォーカスが一覧から移った: " + focusOf(ctx, s));
            check(cursorOf(ctx, s).equals("id1"), "AC212: 届いた時点のカーソルは使用中の行（id1）のはず: " + cursorOf(ctx, s));
            int[] v = ctx.computeOnClient(client -> new int[] {s.notebookList().scrollOffset(),
                    s.notebookList().visibleRowCount()});
            check(1 >= v[0] && 1 < v[0] + v[1], "AC212: 使用中の行が見える位置に無い（scroll=" + v[0] + " 表示 " + v[1] + " 行）");
            key(ctx, InputConstants.KEY_DOWN);
            check(cursorOf(ctx, s).equals("id2"), "AC212: 最初の ↓ は使用中の次の行（id2）へ動くはず: " + cursorOf(ctx, s));
            log("AC212 通った");
        } finally {
            ctx.runOnClient(client -> StoreSession.setNotebookLoadingForTest(false));
            closeScreen(ctx);
        }
    }
}
