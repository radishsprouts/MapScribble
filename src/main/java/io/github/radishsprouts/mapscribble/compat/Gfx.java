package io.github.radishsprouts.mapscribble.compat;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * 描画の窓口。Minecraft が画面・HUD へ渡す描画の入れ物（1.20〜1.21.x は {@code GuiGraphics}、
 * 26.1 以降は {@code GuiGraphicsExtractor}）を1つ包むだけの薄いラッパー。
 *
 * <p>MOD の画面・描画のコードは、この型を引数に受け、{@link GuiCompat} 経由でだけ描く。
 * 版ごとの入れ物の型やメソッドの違いは、{@code Gfx} と {@code GuiCompat} の中だけで吸収する
 * （入れ物が無い版・描き方が違う版を足す時に、画面のコードを書き直さないため）。
 *
 * <p>{@link #raw()} は、Minecraft や他 MOD の API へ入れ物をそのまま渡す境目
 * （{@code super.render(...)}・親画面の描画・ウィジェットの描画）と、{@link GuiCompat} の中だけで使う。
 * 画面・描画のコードから入れ物のメソッドを直接呼ばない。
 *
 * <p>毎フレーム呼ばれるため、同じ入れ物なら直前に作った {@code Gfx} を使い回し、割り当てを増やさない。
 * 描画はレンダースレッドだけで行われるので、使い回しの保持にロックは要らない。
 */
public final class Gfx {
    /** 直前に包んだ {@code Gfx}。同じ入れ物が続く間はこれを返す。 */
    private static Gfx last;

    //? if >=26.1 {
    private final GuiGraphicsExtractor raw;

    private Gfx(GuiGraphicsExtractor raw) {
        this.raw = raw;
    }

    /** Minecraft から渡された入れ物を包む（同じ入れ物なら使い回す）。 */
    public static Gfx of(GuiGraphicsExtractor raw) {
        Gfx cached = last;
        if (cached != null && cached.raw == raw) {
            return cached;
        }
        Gfx created = new Gfx(raw);
        last = created;
        return created;
    }

    /** 包んでいる入れ物。Minecraft・他 MOD の API へ渡す境目と {@link GuiCompat} の中だけで使う。 */
    public GuiGraphicsExtractor raw() {
        return raw;
    }
    //?} else {
    /*private final GuiGraphics raw;

    private Gfx(GuiGraphics raw) {
        this.raw = raw;
    }

    public static Gfx of(GuiGraphics raw) {
        Gfx cached = last;
        if (cached != null && cached.raw == raw) {
            return cached;
        }
        Gfx created = new Gfx(raw);
        last = created;
        return created;
    }

    public GuiGraphics raw() {
        return raw;
    }
    *///?}
}
