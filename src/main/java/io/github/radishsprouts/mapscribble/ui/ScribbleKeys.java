package io.github.radishsprouts.mapscribble.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.draw.Tool;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

/**
 * 操作設定に出るキー。手書きモードの切替は既定で未割り当て（画面設計書 8-3）。
 * ツールの切替は 1〜5（囲み・線・文字・消しゴム・編集）。JourneyMap の全画面マップが数字キーを使っていないことを、
 * JourneyMap 6.0.10 の操作一覧で確かめて決めた。全画面マップの上でだけ効く（ゲーム内の操作は何も奪わない）。
 */
public final class ScribbleKeys {
    private static KeyMapping toggle;
    private static KeyMapping[] tools;

    private ScribbleKeys() {
    }

    /** MOD の初期化で 1 回だけ呼ぶ。 */
    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MapScribble.MOD_ID, "main"));
        toggle = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.mapscribble.toggle_mode", InputConstants.UNKNOWN.getValue(), category));
        int[] defaults = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4,
                InputConstants.KEY_5};
        Tool[] order = Tool.values();
        tools = new KeyMapping[order.length];
        for (int i = 0; i < order.length; i++) {
            tools[i] = KeyMappingHelper.registerKeyMapping(
                    new KeyMapping(HintResolver.toolKey(order[i]), defaults[i], category));
        }
    }

    /** このキーが手書きモードの切替か。 */
    public static boolean isToggle(KeyEvent event) {
        return toggle != null && toggle.matches(event);
    }

    /** このキーに割り当てたツール。無ければ null。 */
    public static Tool toolFor(KeyEvent event) {
        if (tools == null) {
            return null;
        }
        Tool[] order = Tool.values();
        for (int i = 0; i < order.length; i++) {
            if (tools[i].matches(event)) {
                return order[i];
            }
        }
        return null;
    }
}
