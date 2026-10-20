package io.github.radishsprouts.mapscribble.model;

/**
 * 地図上の点。小数のワールド座標（x, z）で持つ。
 * ブロックの整数に丸めないのは、拡大した全画面マップでもなぞった輪郭を滑らかに描くため。
 */
public record MapPoint(double x, double z) {
}
