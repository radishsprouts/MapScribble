package io.github.radishsprouts.mapscribble.geom;

/**
 * JourneyMap のクリック・ドラッグ通知に入るマウス座標の単位を判定する（純粋な計算）。
 *
 * <p>通知の座標は JourneyMap の版で単位が違う。6.0.0 系は GUI 座標（倍率を掛けない）、6.0.7 以降は物理ピクセル
 * （GUI 座標 × GUI 倍率）。MapScribble の内部は物理ピクセルなので、利用者が入れた版を推定せず、押した時に
 * 「ゲームが先に渡した押下の GUI 座標」と通知の座標を比べて実測する。
 */
public final class PointerUnits {
    /** 同じ位置とみなす誤差（ピクセル）。 */
    public static final double TOLERANCE = 0.5;

    /** 通知の座標の単位。 */
    public enum Unit {
        /** まだ分からない（倍率 1・原点付近で両方に合う時、記録が無い時、どちらにも合わない時）。 */
        UNKNOWN,
        /** 物理ピクセル（GUI 座標 × GUI 倍率）。 */
        PHYSICAL,
        /** GUI 座標（倍率を掛けない）。 */
        GUI
    }

    private PointerUnits() {
    }

    /**
     * 通知の座標が、押した位置の GUI 座標に対してどの単位かを判定する。
     * 物理ピクセルにも GUI 座標にも合う（倍率 1・原点付近）時と、どちらにも合わない時は {@link Unit#UNKNOWN}。
     *
     * @param posX     通知の座標 X
     * @param posY     通知の座標 Y
     * @param guiX     押した位置の GUI 座標 X（ゲームが渡したもの。記録が無い時は NaN で、UNKNOWN になる）
     * @param guiY     押した位置の GUI 座標 Y
     * @param guiScale GUI 倍率
     */
    public static Unit detect(double posX, double posY, double guiX, double guiY, double guiScale) {
        boolean asPhysical = near(posX, guiX * guiScale) && near(posY, guiY * guiScale);
        boolean asGui = near(posX, guiX) && near(posY, guiY);
        if (asPhysical == asGui) {
            return Unit.UNKNOWN;
        }
        return asPhysical ? Unit.PHYSICAL : Unit.GUI;
    }

    /** 通知の座標 1 軸分を物理ピクセルへ直す。GUI 座標なら倍率を掛け、それ以外（物理・不明）はそのまま返す。 */
    public static double toPhysical(double value, Unit unit, double guiScale) {
        return unit == Unit.GUI ? value * guiScale : value;
    }

    /**
     * クリックの座標 1 軸分を物理ピクセルにする。押した位置の記録（GUI 座標）があれば「記録 × 倍率」を使い、
     * 記録が無ければ（NaN）判定済みの単位で通知の座標を直す。
     */
    public static double clickToPhysical(double pos, double recordedGui, Unit unit, double guiScale) {
        return Double.isNaN(recordedGui) ? toPhysical(pos, unit, guiScale) : recordedGui * guiScale;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < TOLERANCE;
    }
}
