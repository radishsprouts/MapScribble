package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.compat.WorldIdentityCompat;
import io.github.radishsprouts.mapscribble.ui.HintResolver;
import io.github.radishsprouts.mapscribble.ui.NotebookExtras;
import io.github.radishsprouts.mapscribble.ui.NotebookNames;
import io.github.radishsprouts.mapscribble.ui.NoticeTimer;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebooks;
import io.github.radishsprouts.mapscribble.store.NotebookRegistry.ManualSelection;
import io.github.radishsprouts.mapscribble.store.NotebookRegistry.Resolution;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 地図MOD非依存のメモ保存セッション管理。
 * 保存先は .minecraft/mapscribble/saves/<ワールド名>/ または servers/<ホスト_ポート>/。
 * マルチでは、プロキシの先でサーバーが変わった（同じ接続の中でハッシュが変わった）時に、
 * アドレスのフォルダの下をサーバーごとのメモ帳に分ける（v0.4 設計 2章・4章）。
 * 旧 JourneyMap フォルダ・0.1.0 のデータの取り込みにも対応する。
 *
 * <p>状態はすべて主スレッドから触る。書き込み用スレッドの結果は {@link #MAIN_THREAD} で主スレッドへ戻す。
 */
public final class StoreSession {
    private static final Executor MAIN_THREAD = command -> Minecraft.getInstance().execute(command);

    private static final DebouncedWriter WRITER = new DebouncedWriter(MemoStore.WRITE_DELAY_MS,
            (file, snapshot) -> MemoFile.writeAtomic(file, MemoFile.toJson(snapshot)));
    private static final MemoStore STORE = new MemoStore(WRITER);
    private static final SessionController CONTROLLER = new SessionController(STORE);
    private static final NotebookRegistry REGISTRY = new NotebookRegistry();
    /** 切り替わりの検出器（Minecraft に依存しない）。切断では消さない（接続の実体で持つため）。 */
    private static final SplitDetector DETECTOR = new SplitDetector();
    /** 毎 tick の呼び出しを、参照の比較だけで弾く。 */
    private static final SessionGate GATE = new SessionGate();
    /** 停止の書き切り。ローダーのイベントと JVM の終了フックのどちらから呼ばれても1回だけ。 */
    private static final OnceAction SHUTDOWN = new OnceAction(() -> {
        CONTROLLER.flush();
        STORE.shutdown();
    });
    private static final NotebookService NOTEBOOKS = new NotebookService();
    private static final StoreNotebookExtras EXTRAS = new StoreNotebookExtras(STORE);

    /**
     * 読み込みに失敗した時の受け手。地図MODごとに画面の担当が別にいる（JourneyMap 用と Xaero/FTB 用）ので、
     * 1 つに上書きせず全部へ届ける。
     */
    private static final List<Consumer<String>> PROBLEM_NOTIFIERS = new CopyOnWriteArrayList<>();
    /** 保存の失敗・回復の受け手。理由は上と同じ。 */
    private static final List<Consumer<Boolean>> SAVE_NOTIFIERS = new CopyOnWriteArrayList<>();
    /** メモ帳の状態が変わった時（切り替わり・記録の更新）の受け手。主スレッドで呼ぶ。 */
    private static final List<Runnable> NOTEBOOK_LISTENERS = new CopyOnWriteArrayList<>();

    /** 今のセッションの入力。切断で消す。 */
    private static Target currentTarget;
    private static ResourceKey<Level> currentDimension;
    /** 今の ClientLevel のハッシュ。ClientLevel が変わった時だけ読み直す。 */
    private static long lastSeedHash = WorldIdentityCompat.UNKNOWN;
    /** 利用者が今の接続の中で手で選んだメモ帳（4.5 の select(remember=false)）。 */
    private static ManualSelection manualSelection;
    private static WeakReference<Object> manualConnection = new WeakReference<>(null);
    /** 書き込み中の notebooks.json の記録の数。0 より大きい間は、他の記録の提案を重ねない。 */
    private static int inFlight;
    /** 0.1.0 のメモを取り込む確認を済ませた（または進行中の）フォルダ。セッションで1回だけ確かめる。 */
    private static final Set<Path> IMPORT_CHECKED = new HashSet<>();
    /** 0.1.0 のメモをメモ帳として取り込んでいる最中のアドレス。この間は自動の記録の提案を重ねない。 */
    private static final Set<Path> IMPORTING = new HashSet<>();
    /** 読めない記録ファイルを知らせたアドレス。同じ知らせを繰り返さない。 */
    private static final Set<Path> UNREADABLE_NOTIFIED = new HashSet<>();

    static {
        NOTEBOOK_LISTENERS.add(EXTRAS::invalidate);
        STORE.setMainThreadExecutor(MAIN_THREAD);
        STORE.setProblemHandler(StoreSession::notifyProblem);
        // 書き込みは主スレッドの外で行うので、ここで主スレッドへ戻す
        STORE.setSaveStateHandler(failed -> MAIN_THREAD.execute(() -> {
            for (Consumer<Boolean> notifier : SAVE_NOTIFIERS) {
                notifier.accept(failed);
            }
        }));
        // ローダーの停止イベントが呼ばれなかった時の予備（OnceAction で1回だけ働く）
        Runtime.getRuntime().addShutdownHook(new Thread(StoreSession::shutdown, "MapScribble-Shutdown"));
    }

    private StoreSession() {
    }

    public static MemoRepository repository() {
        // 0.1.0 のメモをメモ帳として取り込んでいる間は使えない扱い（描く・消す・編集・元に戻す・やり直すが何もしない。
        // 説明の行は「読み込み中」になる）。取り込みが終わると鍵を作り直して元に戻る
        return isNotebookLoading() ? null : STORE;
    }

    /** 保存先の画面から呼ぶ窓口（v0.4 設計 4.5）。 */
    public static NotebookService notebooks() {
        return NOTEBOOKS;
    }

    /** ファイルを読めなかった時の通知先を足す。画面の担当ごとに呼ぶ（後から呼んでも前の受け手は消えない）。 */
    public static void addNotifier(Consumer<String> notifier) {
        if (notifier != null) {
            PROBLEM_NOTIFIERS.add(notifier);
        }
    }

    /**
     * 保存に失敗した時に true、その後の保存が成功した時に false を渡す受け手を足す。
     * 呼ばれるのは Minecraft の主スレッド。
     */
    public static void addSaveNotifier(Consumer<Boolean> notifier) {
        if (notifier != null) {
            SAVE_NOTIFIERS.add(notifier);
        }
    }

    /** メモ帳の状態が変わった時（切り替わり・記録の更新）に主スレッドで呼ばれる受け手を足す。 */
    public static void addNotebookListener(Runnable listener) {
        if (listener != null) {
            NOTEBOOK_LISTENERS.add(listener);
        }
    }

    /**
     * ディメンション切り替え時・接続時、そしてクライアントの毎 tick（20 回/秒）から呼ぶ。
     * 前回と同じ ClientLevel・ディメンション・接続先なら、鍵も組み立てずに返る（参照の比較だけ）。
     * ここで弾かないと毎 tick メモのファイルを読み直し、メモの実体が入れ替わって描画側のキャッシュが外れる。
     */
    public static void updateDimension(ResourceKey<Level> dimension) {
        if (dimension == null) {
            GATE.reset();
            currentTarget = null;
            currentDimension = null;
            lastSeedHash = WorldIdentityCompat.UNKNOWN;
            manualSelection = null;
            // 接続を離れたら、待っているお知らせは捨てる（前の接続先の「使うメモ帳：」を次の接続先で出さない）
            NoticeTimer.shared().clear();
            CONTROLLER.update(null, null);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        SessionGate.Change change = GATE.check(mc.level, dimension, currentConnection(), CONTROLLER.current() != null);
        if (change == SessionGate.Change.NONE) {
            return;
        }
        if (change == SessionGate.Change.LEVEL) {
            // ClientLevel ができた時だけハッシュを読む
            lastSeedHash = WorldIdentityCompat.hashedSeed(mc.level);
        }
        Target target = resolveTarget(mc);
        currentTarget = target;
        currentDimension = dimension;
        if (change == SessionGate.Change.LEVEL) {
            onLevelChanged(target, ScreenCompat.dimensionId(dimension));
        }
        importLegacyOnce(target);
        // メモ帳が変わると鍵が変わるので、SessionController が「書き切る → 捨てる → 読み直す」を行う
        String notebookBefore = currentNotebookId();
        if (CONTROLLER.update(createKey(target, dimension), StoreSession::beforeOpen)) {
            // 開き直すと元に戻すの履歴を捨てるので、「Ctrl+Z で戻せます」の知らせも捨てる（切り替えの知らせより先に）
            NoticeTimer.shared().discardUndoHints();
            announceIfNotebookChanged(notebookBefore);
            notifyNotebookListeners();
        }
    }

    /** 今つないでいる先。切り替わりの検出にしか使わないので、型は見ない（同じ実体のままかだけを見る）。 */
    private static Object currentConnection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            return mc.getSingleplayerServer();
        }
        return mc.getCurrentServer();
    }

    public static void flush() {
        CONTROLLER.flush();
    }

    /** 停止の書き切り。何度呼んでも1回だけ働く（ローダーの停止イベントと JVM の終了フックの両方から呼ばれる）。 */
    public static void shutdown() {
        SHUTDOWN.run();
    }

    // ---- 保存先の決定 ----

    private enum Kind { SINGLE, MULTI, UNKNOWN }

    /** 今の接続先から決まる保存先。 */
    private record Target(Kind kind, Path saveDir, String worldFolder, String singleName, String serverName, String ip) {
    }

    private static Target resolveTarget(Minecraft mc) {
        Path baseDir = ScreenCompat.gameDirectory().resolve("mapscribble");
        try {
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                String name = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).getParent().getFileName().toString();
                String folder = "saves/" + name;
                return new Target(Kind.SINGLE, baseDir.resolve(folder), folder, name, null, null);
            }
            if (mc.getCurrentServer() != null) {
                String rawIp = mc.getCurrentServer().ip;
                String folder = "servers/" + rawIp.replace(':', '_').replaceAll("[^a-zA-Z0-9._-]", "_");
                return new Target(Kind.MULTI, baseDir.resolve(folder), folder, null, mc.getCurrentServer().name, rawIp);
            }
        } catch (Exception e) {
            // 下の unknown へ
        }
        return new Target(Kind.UNKNOWN, baseDir.resolve("saves/unknown"), "saves/unknown", null, null, null);
    }

    private static SessionKey createKey(Target target, ResourceKey<Level> dimension) {
        String notebook = null;
        if (target.kind() == Kind.MULTI) {
            Path dir = target.saveDir();
            Resolution r = REGISTRY.resolveFull(dir, lastSeedHash, manualSelection, StoreSession::autoNotebookName);
            notebook = r.notebookId();
            if (r.proposal() != null) {
                if (inFlight > 0 || IMPORTING.contains(normalize(dir))) {
                    // 他の記録の書き込み中は重ねない。その書き込みが終われば鍵を作り直すので、その時にまた判定する
                    REGISTRY.cancel(dir);
                } else {
                    persist(dir, r.proposal(), true, null, null);
                }
            }
        }
        return new SessionKey(target.saveDir(), target.worldFolder(), ScreenCompat.dimensionId(dimension), notebook);
    }

    /** 鍵が変わった後、ファイルを開く直前の処理（旧 JourneyMap の簡易な引き継ぎ）。 */
    private static void beforeOpen(SessionKey key) {
        migrateFromJourneyMapIfNeeded(key.dataPath(), key.dimension());
    }

    // ---- 切り替わり（2.3〜2.6） ----

    /** ClientLevel が変わった時に1回だけ呼ぶ。マルチの時だけ働く。 */
    private static void onLevelChanged(Target target, String dimensionId) {
        Object connection = WorldIdentityCompat.connectionToken();
        if (manualSelection != null
                && (manualSelection.seedHash() != lastSeedHash || manualConnection.get() != connection)) {
            manualSelection = null;
        }
        if (target.kind() != Kind.MULTI) {
            return;
        }
        notifyNoWorldIdIfSplit(target.saveDir());
        SplitDetector.Result r = DETECTOR.observe(connection, dimensionId, lastSeedHash);
        if (!r.split()) {
            return;
        }
        Path dir = target.saveDir();
        if (inFlight > 0 || IMPORTING.contains(normalize(dir))) {
            // 他の記録の書き込み中。ハッシュが違う間は次の ClientLevel でも split になるので、その時に改めて判定する
            return;
        }
        Notebooks proposal = REGISTRY.proposeSplit(dir, r.previous(), r.current(), Instant.now(),
                StoreSession::autoNotebookName);
        if (proposal == null) {
            // 提案できない理由が「読めない」なら知らせる（mode が決まっている・書き込み中は静かに何もしない）
            if (!REGISTRY.status(dir).canWrite() && UNREADABLE_NOTIFIED.add(normalize(dir))) {
                notifyProblem("保存先の記録ファイル（notebooks.json）を読めなかったため、サーバーごとに分けずに保存します");
            }
            return;
        }
        // 分け始めたことを知らせ、続けて今どのメモ帳に書くかを知らせる（設計書 3.9。鍵の作り直しの後に名前を引く）
        persist(dir, proposal, true, written -> setNotice(NOTICE_SPLIT), done -> {
            String name = done ? currentNotebookName() : null;
            if (name != null) {
                setNotice(NOTICE_NOTEBOOK_SWITCHED, name);
            }
        });
    }

    // ---- notebooks.json への書き込み（書いてから使う） ----

    /**
     * proposal を書き込み用スレッドで書いて読み直し、成功したら主スレッドで commit、失敗なら abandon する。
     * 書けたと確かめるまでメモリ上の状態は変えない（記録が残らないまま新しいメモ帳へ書かないため）。
     * 成功したら鍵を作り直し、受け手へ知らせる。
     *
     * @param reportFailure true なら、失敗を問題の受け手へ知らせる（自動の記録）。利用者の操作は結果で返すので false
     * @param onOk          成功した時（commit の後、鍵の作り直しの前）。null 可
     * @param onDone        終わった時（鍵の作り直しと受け手への通知の後。成功なら true）。null 可
     */
    static void persist(Path dir, Notebooks proposal, boolean reportFailure, Consumer<Notebooks> onOk,
            Consumer<Boolean> onDone) {
        boolean wasStopped = REGISTRY.notebooks(dir).autoStopped();
        inFlight++;
        NotebookRegistry.persist(dir, proposal, WRITER).whenComplete((written, error) -> MAIN_THREAD.execute(() -> {
            inFlight--;
            if (error != null) {
                REGISTRY.abandon(dir);
                MapScribble.LOGGER.warn("Failed to write notebooks.json: {}", error.getMessage());
                if (reportFailure) {
                    notifyProblem("メモ帳の記録（notebooks.json）を書けなかったため、サーバーごとに分けずに保存します");
                }
                if (onDone != null) {
                    onDone.accept(false);
                }
                return;
            }
            REGISTRY.commit(dir, written);
            if (onOk != null) {
                onOk.accept(written);
            }
            if (written.autoStopped() && !wasStopped) {
                notifyProblem("このサーバーは入るたびにシードが変わるため、自動で分けるのを止めました");
            }
            rekey();
            notifyNotebookListeners();
            if (onDone != null) {
                onDone.accept(true);
            }
        }));
    }

    /** いま他の記録の書き込みや取り込みが進んでいるか。利用者の操作を重ねない判定に使う。 */
    static boolean isNotebookBusy(Path dir) {
        return inFlight > 0 || IMPORTING.contains(normalize(dir));
    }

    /** 今の入力のまま鍵を作り直す。メモ帳の選択が変わった時に呼ぶ（変わっていなければ何も起きない）。 */
    static void rekey() {
        Target target = currentTarget;
        ResourceKey<Level> dimension = currentDimension;
        if (target == null || dimension == null) {
            return;
        }
        String notebookBefore = currentNotebookId();
        if (CONTROLLER.update(createKey(target, dimension), StoreSession::beforeOpen)) {
            // 開き直すと元に戻すの履歴を捨てるので、「Ctrl+Z で戻せます」の知らせも捨てる（切り替えの知らせより先に）
            NoticeTimer.shared().discardUndoHints();
            announceIfNotebookChanged(notebookBefore);
            notifyNotebookListeners();
        }
    }

    // ---- NotebookService が使う口 ----

    static NotebookRegistry registry() {
        return REGISTRY;
    }

    /** 保存先の画面を出せる時のアドレスのフォルダ。マルチで決まっている時だけ。それ以外は null。 */
    static Path notebookAddressDir() {
        Target t = currentTarget;
        return t != null && t.kind() == Kind.MULTI ? t.saveDir() : null;
    }

    /** いま開いている分ける前のファイル（メモ帳を開いている時だけ。直下なら null）。 */
    static Path currentLegacyFile() {
        SessionKey key = CONTROLLER.current();
        return key == null ? null : key.legacyFile();
    }

    static long currentSeedHash() {
        return lastSeedHash;
    }

    /** 今の書き込み先のメモ帳の id。直下なら null。 */
    static String currentNotebookId() {
        SessionKey key = CONTROLLER.current();
        return key == null ? null : key.notebook();
    }

    static ManualSelection manualSelection() {
        return manualSelection;
    }

    /** 手で選んだメモ帳を置く（null で外す）。今の接続・今のハッシュの間だけ有効。 */
    static void setManualSelection(ManualSelection selection) {
        manualSelection = selection;
        manualConnection = new WeakReference<>(selection == null ? null : WorldIdentityCompat.connectionToken());
    }

    static final String NOTICE_SPLIT = HintResolver.NOTICE_SPLIT_STARTED;
    static final String NOTICE_JM010 = HintResolver.NOTICE_JM010_IMPORTED;
    static final String NOTICE_NO_WORLD_ID = HintResolver.NOTICE_NO_WORLD_ID;
    static final String NOTICE_NOTEBOOK_SWITCHED = HintResolver.NOTICE_NOTEBOOK_SWITCHED;

    /**
     * 説明の行に出す知らせ（翻訳キーと引数）。主スレッドで、共有の待ち行列へ積む。
     * 帯が出ていない間に起きたことも失わず、次に帯が出た時に1回出る（設計書 3.9）。
     */
    static void setNotice(String key, Object... args) {
        NoticeTimer.shared().push(key, NoticeTimer.nowMs(), args);
    }

    /** 今書いているメモ帳の名前。直下（メモ帳なし）・見つからない時は null。 */
    private static String currentNotebookName() {
        Path dir = notebookAddressDir();
        String id = currentNotebookId();
        if (dir == null || id == null) {
            return null;
        }
        NotebookFile.Notebook nb = REGISTRY.notebooks(dir).find(id);
        return nb == null ? null : NotebookNames.display(nb.name());
    }

    /** 自動で作るメモ帳の名前（「メモ帳 N」/「Notebook N」。作った時のゲームの言語）。 */
    private static String autoNotebookName(int n) {
        return I18n.get("mapscribble.notebook.default_name", n);
    }

    /**
     * 使うメモ帳の id が、before と違う値（null でない）に変わっていたら、切り替えを知らせる（設計書 3.9）。
     * ディメンションの移動だけ（id が同じ）と、直下（null）へ替わった時は知らせない。
     */
    private static void announceIfNotebookChanged(String before) {
        String after = currentNotebookId();
        if (after == null || after.equals(before)) {
            return;
        }
        String name = currentNotebookName();
        if (name != null) {
            setNotice(NOTICE_NOTEBOOK_SWITCHED, name);
        }
    }

    /**
     * 分けている接続先で、ワールドの識別値が取れない時に知らせる（設計書 3.9・AC160）。
     * その間に描いたメモは分ける前のメモへ書かれる（同じ判定が保存先の画面の「ワールドを見分けられない」）。
     */
    private static void notifyNoWorldIdIfSplit(Path dir) {
        if (lastSeedHash != WorldIdentityCompat.UNKNOWN || manualSelection != null) {
            return;
        }
        if (!REGISTRY.status(dir).canWrite()) {
            return;
        }
        NotebookFile.Mode mode = REGISTRY.notebooks(dir).mode();
        if (mode == NotebookFile.Mode.AUTO || mode == NotebookFile.Mode.USER) {
            setNotice(NOTICE_NO_WORLD_ID);
        }
    }

    /** 今の接続先のアドレス（接続に使った文字列）。マルチ以外は null。 */
    static String currentAddress() {
        Target t = currentTarget;
        return t != null && t.kind() == Kind.MULTI ? t.ip() : null;
    }

    /** 0.1.0 のメモをメモ帳として取り込んでいる最中か。 */
    static boolean isNotebookLoading() {
        if (IMPORTING.isEmpty()) {
            // 毎フレーム呼ばれるので、取り込みが無い時はパスを組み立てない
            return false;
        }
        Target t = currentTarget;
        return t != null && IMPORTING.contains(normalize(t.saveDir()));
    }

    /** 保存先の画面に渡す件数・移す操作の窓口。呼ぶたびに、画面を開いた時に見えている範囲を控え直す。 */
    public static NotebookExtras notebookExtras() {
        EXTRAS.snapshotVisible();
        return EXTRAS;
    }

    static void notifyNotebookListeners() {
        for (Runnable listener : NOTEBOOK_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                MapScribble.LOGGER.warn("Notebook listener failed: {}", e.getMessage());
            }
        }
    }

    private static void notifyProblem(String message) {
        for (Consumer<String> notifier : PROBLEM_NOTIFIERS) {
            notifier.accept(message);
        }
    }

    // ---- 過去のデータの取り込み ----

    /**
     * 0.1.0 が JourneyMap のフォルダへ書いたメモを取り込む（W0）。保存先ごとにセッションで1回だけ確かめる
     * （取り込んだ印があれば列挙もしない）。
     * 直下へ入れる場合は、ファイルを開く前に終わらせるため、ここで同期して行う（小さなファイルのコピーだけ。
     * 非同期にすると、開いた後に描いたメモの書き込みが取り込みと競合して、取り込んだメモを消しうる）。
     * メモ帳として取り込む場合（候補が2つ以上）は新しいフォルダへ書くだけなので、書き込み用スレッドで行う。
     */
    private static void importLegacyOnce(Target target) {
        if (target.kind() == Kind.UNKNOWN) {
            return;
        }
        Path dir = target.saveDir();
        Path key = normalize(dir);
        if (!IMPORT_CHECKED.add(key)) {
            return;
        }
        try {
            Path gameDir = ScreenCompat.gameDirectory();
            if (target.kind() == Kind.SINGLE) {
                Jm010Import.Plan plan = Jm010Import.planSingleplayer(gameDir, dir, target.singleName());
                Jm010Import.importIntoRoot(plan, dir);
                return;
            }
            Jm010Import.Plan plan = Jm010Import.planMultiplayer(gameDir, dir, target.serverName(), target.ip());
            if (plan.isEmpty()) {
                return;
            }
            if (!plan.asNotebooks()) {
                Jm010Import.importIntoRoot(plan, dir);
                return;
            }
            IMPORTING.add(key);
            Jm010Import.importAsNotebooks(REGISTRY, dir, plan, WRITER, MAIN_THREAD, Instant.now())
                    .whenComplete((count, error) -> MAIN_THREAD.execute(() -> {
                        IMPORTING.remove(key);
                        if (error != null) {
                            // 印を置いていないので、次にこのアドレスへ入った時にやり直す
                            IMPORT_CHECKED.remove(key);
                            MapScribble.LOGGER.warn("Failed to import 0.1.0 notes as notebooks: {}", error.getMessage());
                        } else if (count != null && count > 0) {
                            setNotice(NOTICE_JM010);
                        }
                        String before = currentNotebookId();
                        rekey();
                        // 取り込みで使うメモ帳が変わったら、取り込みの知らせの後に続けて知らせる（設計書 3.9）
                        String after = currentNotebookId();
                        String name = after == null || after.equals(before) ? null : currentNotebookName();
                        if (name != null) {
                            setNotice(NOTICE_NOTEBOOK_SWITCHED, name);
                        }
                        notifyNotebookListeners();
                    }));
        } catch (RuntimeException e) {
            IMPORT_CHECKED.remove(key);
            MapScribble.LOGGER.warn("Failed to check or import 0.1.0 notes: {}", e.getMessage());
        }
    }

    private static Path normalize(Path dir) {
        return dir.toAbsolutePath().normalize();
    }

    private static void migrateFromJourneyMapIfNeeded(Path targetDir, String dimId) {
        try {
            Path jmAddonDir = ScreenCompat.gameDirectory().resolve("journeymap").resolve("addon-data").resolve(MapScribble.MOD_ID);
            if (!Files.exists(jmAddonDir)) {
                return;
            }
            String fileName = MemoFile.fileName(dimId);
            Path oldFile = jmAddonDir.resolve(fileName);
            Path newFile = targetDir.resolve(fileName);
            if (Files.exists(oldFile) && !Files.exists(newFile)) {
                Files.createDirectories(targetDir);
                Files.copy(oldFile, newFile, StandardCopyOption.COPY_ATTRIBUTES);
                MapScribble.LOGGER.info("Migrated legacy MapScribble note from {} to {}", oldFile, newFile);
            }
        } catch (Exception e) {
            MapScribble.LOGGER.warn("Failed to check or migrate legacy notes: {}", e.getMessage());
        }
    }
}
