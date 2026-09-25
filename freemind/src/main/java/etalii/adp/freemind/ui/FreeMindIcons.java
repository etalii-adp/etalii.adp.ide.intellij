package etalii.adp.freemind.ui;

import java.util.Map;

/**
 * FreeMind's built-in icons as Unicode glyphs drawn with the system font, since FreeMind's own
 * artwork is GPL (research R8). An unknown or custom icon is shown as a badge with its name.
 */
public final class FreeMindIcons {

    private static final Map<String, String> GLYPHS = Map.ofEntries(
            Map.entry("help", "\u2753"),
            Map.entry("messagebox_warning", "\u26a0\ufe0f"),
            Map.entry("idea", "\ud83d\udca1"),
            Map.entry("button_ok", "\u2705"),
            Map.entry("button_cancel", "\u274c"),
            Map.entry("full-0", "0\ufe0f\u20e3"),
            Map.entry("full-1", "1\ufe0f\u20e3"),
            Map.entry("full-2", "2\ufe0f\u20e3"),
            Map.entry("full-3", "3\ufe0f\u20e3"),
            Map.entry("full-4", "4\ufe0f\u20e3"),
            Map.entry("full-5", "5\ufe0f\u20e3"),
            Map.entry("full-6", "6\ufe0f\u20e3"),
            Map.entry("full-7", "7\ufe0f\u20e3"),
            Map.entry("full-8", "8\ufe0f\u20e3"),
            Map.entry("full-9", "9\ufe0f\u20e3"),
            Map.entry("back", "\u2b05\ufe0f"),
            Map.entry("forward", "\u27a1\ufe0f"),
            Map.entry("up", "\u2b06\ufe0f"),
            Map.entry("down", "\u2b07\ufe0f"),
            Map.entry("attach", "\ud83d\udcce"),
            Map.entry("ksmiletris", "\ud83d\ude42"),
            Map.entry("smiley-neutral", "\ud83d\ude10"),
            Map.entry("smiley-oh", "\ud83d\ude2e"),
            Map.entry("smiley-angry", "\ud83d\ude20"),
            Map.entry("smily_bad", "\ud83d\ude1e"),
            Map.entry("clanbomber", "\ud83d\udca3"),
            Map.entry("desktop_new", "\ud83d\udda5\ufe0f"),
            Map.entry("gohome", "\ud83c\udfe0"),
            Map.entry("folder", "\ud83d\udcc1"),
            Map.entry("bookmark", "\ud83d\udd16"),
            Map.entry("penguin", "\ud83d\udc27"),
            Map.entry("licq", "\ud83c\udf3c"),
            Map.entry("freemind_butterfly", "\ud83e\udd8b"),
            Map.entry("broken-line", "\u26a1"),
            Map.entry("calendar", "\ud83d\udcc5"),
            Map.entry("clock", "\u23f0"),
            Map.entry("hourglass", "\u231b"),
            Map.entry("launch", "\ud83d\ude80"),
            Map.entry("flag", "\ud83d\udea9"),
            Map.entry("flag-black", "\ud83c\udff4"),
            Map.entry("flag-blue", "\ud83d\udd35"),
            Map.entry("flag-green", "\ud83d\udfe2"),
            Map.entry("flag-orange", "\ud83d\udfe0"),
            Map.entry("flag-pink", "\ud83e\ude77"),
            Map.entry("flag-yellow", "\ud83d\udfe1"),
            Map.entry("prepare", "\ud83d\udfe1"),
            Map.entry("go", "\ud83d\udfe2"),
            Map.entry("stop", "\ud83d\udd34"),
            Map.entry("stop-sign", "\ud83d\uded1"),
            Map.entry("closed", "\u26d4"),
            Map.entry("info", "\u2139\ufe0f"),
            Map.entry("yes", "\u2757"),
            Map.entry("list", "\ud83d\udccb"),
            Map.entry("wizard", "\ud83e\uddd9"),
            Map.entry("xmag", "\ud83d\udd0d"),
            Map.entry("bell", "\ud83d\udd14"),
            Map.entry("pencil", "\u270f\ufe0f"),
            Map.entry("edit", "\ud83d\udcdd"),
            Map.entry("kaddressbook", "\ud83d\udcd2"),
            Map.entry("knotify", "\ud83c\udfb5"),
            Map.entry("korn", "\ud83d\udcec"),
            Map.entry("Mail", "\u2709\ufe0f"),
            Map.entry("password", "\ud83d\udd11"),
            Map.entry("encrypted", "\ud83d\udd12"),
            Map.entry("decrypted", "\ud83d\udd13"),
            Map.entry("family", "\ud83d\udc6a"),
            Map.entry("female1", "\ud83d\udc69"),
            Map.entry("female2", "\ud83d\udc67"),
            Map.entry("male1", "\ud83d\udc68"),
            Map.entry("male2", "\ud83d\udc66"),
            Map.entry("fema", "\ud83d\udc6b"),
            Map.entry("group", "\ud83d\udc65"),
            Map.entry("redo", "\u21aa\ufe0f"),
            Map.entry("undo", "\u21a9\ufe0f"),
            Map.entry("checked", "\u2611\ufe0f"),
            Map.entry("unchecked", "\u2610"));

    private FreeMindIcons() {
    }

    /** The glyph for a built-in icon name, or {@code null} when the name is not built in. */
    public static String glyph(String name) {
        return GLYPHS.get(name);
    }

    /** What to draw for an icon: its glyph, or its name as a badge. */
    public static String display(String name) {
        String glyph = glyph(name);
        return glyph != null ? glyph : name;
    }
}
