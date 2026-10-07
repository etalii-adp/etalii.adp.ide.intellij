package etalii.adp.fbl.document;

import java.util.List;

/** What a binding says about its registrations (FBL §8). What it does not state is null, false or empty. */
public record RegistrationSettings(List<String> headers, boolean createOnFirstPlacement, String resourceCapture, String legacyLayout,
        String legacyIdentities) {

    public static final RegistrationSettings DEFAULT = new RegistrationSettings(List.of(), false, null, null, null);

    public RegistrationSettings {
        headers = List.copyOf(headers);
    }
}
