package etalii.adp.core.diagram;

/** Whether a gesture is allowed, and if not, the reason shown to the user as is (research R9). */
public record Verdict(boolean allowed, String reason) {

    private static final Verdict ALLOW = new Verdict(true, null);

    public static Verdict allow() {
        return ALLOW;
    }

    public static Verdict refuse(String reason) {
        return new Verdict(false, reason);
    }
}