package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.List;

import org.jdom.Element;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * T004 (FR-005, FR-018, FR-019): the defaults of data-model.md, user-level storage, typed reads, a
 * damaged field falling back alone, unknown fields written back, one notice per session, and the
 * service answering in a headless application.
 */
@RunWith(JUnit4.class)
public class AdpSettingsTest extends BasePlatformTestCase {

    private final List<Notification> notices = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification notification) {
                if ("ADP".equals(notification.getGroupId())) {
                    notices.add(notification);
                }
            }
        });
    }

    private static AdpSettings loaded(String xml) throws Exception {
        AdpSettings settings = new AdpSettings();
        settings.loadState(JDOMUtil.load(xml));
        return settings;
    }

    @Test
    public void theDefaultsAreThoseOfTheDataModel() {
        AdpSettings settings = new AdpSettings();
        assertEquals(new CanvasOptions(false, true, 1.0), settings.canvas());
        assertEquals(CanvasOptions.DEFAULTS, settings.canvas());
        assertFalse(settings.isOff("any.designer"));
        assertTrue(settings.offDesigners().isEmpty());
        assertEquals(1.0, settings.openingZoom(), 1e-9);
    }

    @Test
    public void everySettingIsStoredForTheUserInTheOptionsFolder() {
        State state = AdpSettings.class.getAnnotation(State.class);
        assertEquals("AdpSettings", state.name());
        Storage storage = state.storages()[0];
        assertEquals("adp.xml", storage.value());
        assertFalse("not in a workspace or project file", storage.value().startsWith(StoragePathMacros.WORKSPACE_FILE));
        assertNotNull("an application service", ApplicationManager.getApplication().getService(AdpSettings.class));
    }

    @Test
    public void readsAreTyped() throws Exception {
        AdpSettings settings = loaded("""
                <state>
                  <option name="offDesigners"><set><option value="etalii.adp.sample"/></set></option>
                  <option name="showGrid" value="true"/>
                  <option name="snapToGrid" value="false"/>
                  <option name="openingZoom" value="150"/>
                </state>""");
        assertEquals(new CanvasOptions(true, false, 1.5), settings.canvas());
        assertTrue(settings.isOff("etalii.adp.sample"));
        assertFalse(settings.isOff("etalii.adp.other"));
        assertTrue(notices.isEmpty());
    }

    @Test
    public void aDamagedFieldFallsBackAloneAndTheOthersKeepTheirValues() throws Exception {
        AdpSettings settings = loaded("""
                <state>
                  <option name="offDesigners"><set><option value="etalii.adp.sample"/></set></option>
                  <option name="showGrid" value="true"/>
                  <option name="snapToGrid" value="sometimes"/>
                  <option name="openingZoom" value="9000"/>
                </state>""");
        assertEquals(new CanvasOptions(true, true, 1.0), settings.canvas());
        assertTrue(settings.isOff("etalii.adp.sample"));
    }

    @Test
    public void theStoredTextOfADamagedFieldIsLeftAloneUntilApplied() throws Exception {
        AdpSettings settings = loaded("<state><option name=\"openingZoom\" value=\"huge\"/></state>");
        settings.canvas();
        assertTrue(JDOMUtil.write(settings.getState()).contains("value=\"huge\""));

        settings.setCanvas(new CanvasOptions(false, true, 0.75));
        assertTrue(JDOMUtil.write(settings.getState()).contains("value=\"75\""));
        assertEquals(0.75, settings.openingZoom(), 1e-9);
    }

    @Test
    public void unknownStoredFieldsAreWrittenBackUnchanged() throws Exception {
        AdpSettings settings = loaded("""
                <state>
                  <option name="showGrid" value="true"/>
                  <option name="fromANewerPlugin" value="42"/>
                  <somethingElse kind="new"><child/></somethingElse>
                </state>""");
        settings.setOff("etalii.adp.sample", true);
        String written = JDOMUtil.write(settings.getState());
        assertTrue(written, written.contains("<option name=\"fromANewerPlugin\" value=\"42\" />"));
        assertTrue(written, written.contains("<somethingElse kind=\"new\">"));
        assertTrue(written, written.contains("etalii.adp.sample"));
    }

    @Test
    public void oneNoticePerSessionWhenAnythingFellBack() throws Exception {
        AdpSettings settings = loaded("<state><option name=\"showGrid\" value=\"perhaps\"/><option name=\"openingZoom\" value=\"x\"/></state>");
        settings.canvas();
        settings.canvas();
        settings.loadState(JDOMUtil.load("<state><option name=\"snapToGrid\" value=\"?\"/></state>"));
        settings.canvas();
        assertEquals(1, notices.size());
        String content = notices.getFirst().getContent();
        assertTrue(content, content.contains("Show grid"));
        assertTrue(content, content.contains("Opening zoom"));
    }

    @Test
    public void noNoticeWhenEverythingReads() throws Exception {
        loaded("<state><option name=\"showGrid\" value=\"true\"/></state>").canvas();
        new AdpSettings().canvas();
        assertTrue(notices.isEmpty());
    }

    @Test
    public void aFieldOfTheWrongShapeDoesNotStopTheOthers() throws Exception {
        AdpSettings settings = loaded("""
                <state>
                  <option name="offDesigners"><map><entry key="1" value="2"/></map></option>
                  <option name="designerSettings" value="flat"/>
                  <option name="showGrid" value="true"/>
                </state>""");
        assertTrue(settings.canvas().showGrid());
        assertFalse(settings.isOff("etalii.adp.sample"));
    }

    @Test
    public void theServiceLoadsAndAnswersInAHeadlessApplication() {
        assertTrue(ApplicationManager.getApplication().isHeadlessEnvironment());
        AdpSettings settings = AdpSettings.getInstance();
        assertNotNull(settings);
        assertNotNull(settings.canvas());
    }

    @Test
    public void aStateWithoutChildrenIsTheDefaults() {
        AdpSettings settings = new AdpSettings();
        settings.loadState(new Element("state"));
        assertEquals(CanvasOptions.DEFAULTS, settings.canvas());
    }
}
