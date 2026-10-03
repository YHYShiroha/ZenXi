package com.zenxi.yogatimer;

/**
 * 全应用配色。所有界面颜色都从这里取，便于「禅夜 / 素白」两套主题整体切换。
 */
public final class Palette {

    public static final Palette DARK = new Palette(true);
    public static final Palette LIGHT = new Palette(false);

    public static Palette of(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    public final boolean dark;

    public final int bgTop;
    public final int bgBottom;
    public final int glowA;
    public final int glowB;
    public final int vignette;

    public final int ringTrack;
    public final int arcA;
    public final int arcB;

    public final int textHi;
    public final int textMid;
    public final int textLow;

    public final int card;
    public final int cardStroke;
    public final int chip;
    public final int chipStroke;
    public final int chipText;

    public final int accent;
    public final int accentSoft;
    public final int onAccent;
    public final int amber;
    public final int amberSoft;
    public final int danger;
    public final int scrim;
    public final int knob;
    public final int trackOff;
    public final int divider;

    private Palette(boolean dark) {
        this.dark = dark;
        if (dark) {
            bgTop = 0xFF06090F;
            bgBottom = 0xFF0B1523;
            glowA = 0x5533D2A2;
            glowB = 0x4A3A6BD4;
            vignette = 0x66000000;

            ringTrack = 0x18FFFFFF;
            arcA = 0xFF4FE3B8;
            arcB = 0xFF63A8F5;

            textHi = 0xFFEAF1FF;
            textMid = 0xFFA6B4CC;
            textLow = 0xFF6E7C96;

            card = 0xF2142038;
            cardStroke = 0x22FFFFFF;
            chip = 0x14FFFFFF;
            chipStroke = 0x1EFFFFFF;
            chipText = 0xFFB9C6DC;

            accent = 0xFF4FE3B8;
            accentSoft = 0x2E4FE3B8;
            onAccent = 0xFF04221A;
            amber = 0xFFFFC46B;
            amberSoft = 0x33FFC46B;
            danger = 0xFFFF8A96;
            scrim = 0xD905070C;
            knob = 0xFFF2F7FF;
            trackOff = 0x33FFFFFF;
            divider = 0x14FFFFFF;
        } else {
            bgTop = 0xFFF9F7F2;
            bgBottom = 0xFFE9EFF6;
            glowA = 0x3A5BC8A8;
            glowB = 0x3A8FB6F0;
            vignette = 0x22FFFFFF;

            ringTrack = 0x16000000;
            arcA = 0xFF12A981;
            arcB = 0xFF3E8FD0;

            textHi = 0xFF1A2233;
            textMid = 0xFF56657C;
            textLow = 0xFF8E9BAC;

            card = 0xFFFFFFFF;
            cardStroke = 0x16000000;
            chip = 0x0F000000;
            chipStroke = 0x1A000000;
            chipText = 0xFF4A5A70;

            accent = 0xFF12A981;
            accentSoft = 0x2A12A981;
            onAccent = 0xFFFFFFFF;
            amber = 0xFFC07A18;
            amberSoft = 0x30C07A18;
            danger = 0xFFD2555F;
            scrim = 0xF2F4F1EB;
            knob = 0xFFFFFFFF;
            trackOff = 0x33000000;
            divider = 0x12000000;
        }
    }
}
