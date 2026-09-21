package com.stardew.craft.joja;

/**
 * Joja 线常量，1:1 对应 SDV JojaMart.cs / JojaCDMenu.cs。
 */
public final class JojaConstants {

    private JojaConstants() {}

    /** JojaMart.cs:10 — 会员费 */
    public static final int JOJA_MEMBERSHIP_PRICE = 5000;

    // ── JojaCDMenu 几何（1:1 对应 SDV 源）──
    /** SDV JojaCDMenu width/height（逻辑像素） */
    public static final int CD_MENU_WIDTH  = 1280;
    public static final int CD_MENU_HEIGHT = 576;

    /** 购买完成后自动退出延迟（毫秒） */
    public static final int CD_EXIT_TIMER_MS = 1000;

    /** 钱不够时金钱框抖动时长（毫秒） */
    public static final int MONEY_SHAKE_MS = 1000;

    /** 贴图源 rect 尺寸：form 主体 320x144，scale=4 → 1280x576。 */
    public static final int FORM_W = 320;
    public static final int FORM_H = 144;
    /** JojaCDForm.png 实际贴图尺寸（checkmark 条在 y=144..159）。 */
    public static final int TEX_W = 320;
    public static final int TEX_H = 160;
    public static final int SCALE = 4;

    // ── 5 格按钮价格（JojaCDMenu.cs:166-177） ──
    public static int priceForButton(int buttonIdx) {
        return switch (buttonIdx) {
            case 0 -> 40000; // Vault
            case 1 -> 15000; // Boiler Room
            case 2 -> 25000; // Crafts Room
            case 3 -> 35000; // Pantry
            case 4 -> 20000; // Fish Tank
            default -> -1;
        };
    }

    /** 购买结果码（S→C） */
    public static final int RESULT_OK              = 0;
    public static final int RESULT_NOT_ENOUGH_MONEY = 1;
    public static final int RESULT_ALREADY_DONE    = 2;
}
