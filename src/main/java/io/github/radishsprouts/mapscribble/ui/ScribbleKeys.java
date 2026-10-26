package io.github.radishsprouts.mapscribble.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.KeyMapping;

//? if fabric {
    //? if >=26.1 {
    import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
    //?} else {
    /*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
    *///?}
//?}

//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?}

//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
//?}

/**
 * 操作設定に出るキー。手書きモードの切替は既定で未割り当て。
 * ツールの切替は 1〜5（囲み・線・文字・消しゴム・編集）。
 */
public final class ScribbleKeys {
    private static KeyMapping toggle;
    private static KeyMapping[] tools;

    private ScribbleKeys() {
    }

    /** MOD の初期化で 1 回だけ呼ぶ。 */
    public static void register() {
        //? if >=1.21.11 {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MapScribble.MOD_ID, "main"));
        //?} else {
        /*String category = "key.categories.mapscribble";
        *///?}

        toggle = new KeyMapping("key.mapscribble.toggle_mode", InputConstants.UNKNOWN.getValue(), category);
        int[] defaults = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4,
                InputConstants.KEY_5};
        Tool[] order = Tool.values();
        tools = new KeyMapping[order.length];
        for (int i = 0; i < order.length; i++) {
            tools[i] = new KeyMapping(HintResolver.toolKey(order[i]), defaults[i], category);
        }

        //? if fabric {
            //? if >=26.1 {
            KeyMappingHelper.registerKeyMapping(toggle);
            for (KeyMapping k : tools) {
                KeyMappingHelper.registerKeyMapping(k);
            }
            //?} else {
            /*KeyBindingHelper.registerKeyBinding(toggle);
            for (KeyMapping k : tools) {
                KeyBindingHelper.registerKeyBinding(k);
            }
            *///?}
        //?}
    }

    public static List<KeyMapping> allKeys() {
        List<KeyMapping> list = new ArrayList<>();
        if (toggle != null) {
            list.add(toggle);
        }
        if (tools != null) {
            Collections.addAll(list, tools);
        }
        return list;
    }

    //? if >=1.21.11 {
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
    //?} else {
    /*public static boolean isToggle(int keyCode, int scanCode) {
        return toggle != null && toggle.matches(keyCode, scanCode);
    }

    public static Tool toolFor(int keyCode, int scanCode) {
        if (tools == null) {
            return null;
        }
        Tool[] order = Tool.values();
        for (int i = 0; i < order.length; i++) {
            if (tools[i].matches(keyCode, scanCode)) {
                return order[i];
            }
        }
        return null;
    }
    *///?}

    /** このツールに今割り当てられているキーの表示名（操作設定で変えた後の名前）。未割り当て・未登録なら null。 */
    public static String toolKeyLabel(Tool tool) {
        if (tools == null || tool.ordinal() >= tools.length || tools[tool.ordinal()].isUnbound()) {
            return null;
        }
        return tools[tool.ordinal()].getTranslatedKeyMessage().getString();
    }
}
