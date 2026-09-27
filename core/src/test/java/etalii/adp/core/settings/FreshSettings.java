package etalii.adp.core.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ServiceContainerUtil;

/** A fresh {@link AdpSettings} in place of the application's, for the rest of one test. */
public final class FreshSettings {

    private FreshSettings() {
    }

    public static AdpSettings install(Disposable testDisposable) {
        AdpSettings fresh = new AdpSettings();
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), AdpSettings.class, fresh, testDisposable);
        return fresh;
    }
}
