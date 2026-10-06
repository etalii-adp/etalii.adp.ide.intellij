package etalii.adp.fbl.document;

/** The names FBL allows for a binding and a rule. */
final class Names {

    private Names() {
    }

    static boolean isName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (!(isAsciiLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(isAsciiLetter(c) || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }
}
