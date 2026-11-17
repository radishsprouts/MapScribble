package io.github.radishsprouts.mapscribble.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ワールドの見分けに使う値を、版・ローダーの違いを隠して返す。主スレッドから呼ぶ。
 *
 * <p>hashed seed（サーバーがログイン・リスポーンのパケットで送るシードのハッシュ）は、全構成で公開 API では読めず、
 * {@code BiomeManager.biomeZoomSeed}（private final long）にだけある。そこでそのフィールドを
 * Access Widener（Fabric）/ Access Transformer（Forge・NeoForge）で開け、型付きで直接読む。
 * 開けるファイルは {@code src/main/accesswidener/}・{@code src/main/accesstransformer/} にあり、
 * 各ビルドスクリプトが自分の構成の分だけ jar へ入れる。
 */
public final class WorldIdentityCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("MapScribble");

    /** ハッシュが取れない時の値。0 はサーバーがシードを隠した時にも来るので、同じに扱う。 */
    public static final long UNKNOWN = 0L;

    /** AW/AT が効いていない警告を出したか。同じ警告を毎回出さない。 */
    private static boolean warned;

    private WorldIdentityCompat() {
    }

    /**
     * そのワールドの hashed seed（BiomeManager.biomeZoomSeed。AW/AT で開けてある）。
     * level が null、または AW/AT が効いていない jar では UNKNOWN。
     */
    public static long hashedSeed(Level level) {
        if (level == null) {
            return UNKNOWN;
        }
        try {
            return level.getBiomeManager().biomeZoomSeed;
        } catch (IllegalAccessError e) {
            // AW/AT が jar で効いていない。UNKNOWN は「分けない・直下へ書く」ので、今までと同じ動きに倒れる
            if (!warned) {
                warned = true;
                LOGGER.warn("MapScribble: hashed seed を読めない（AW/AT が効いていない）。サーバーごとのメモの分離は働かない", e);
            }
            return UNKNOWN;
        }
    }

    /**
     * 今の接続の実体（netty の net.minecraft.network.Connection）。同じ接続のままかを == で比べるためだけに使う。
     * 未接続なら null。シングルでも内部の接続が返るが、呼び出し側はシングルでは使わない。
     */
    public static Object connectionToken() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        return listener == null ? null : listener.getConnection();
    }
}
