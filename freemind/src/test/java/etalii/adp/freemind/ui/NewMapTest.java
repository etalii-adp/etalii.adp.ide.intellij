package etalii.adp.freemind.ui;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.freemind.model.MindMap;

/** FR-014, US3-AS1: New > FreeMind Mind Map writes FreeMind 1.0.1's new-map text and opens it in the diagram. */
@RunWith(JUnit4.class)
public class NewMapTest extends FileEditorManagerTestCase {

    private static final Pattern NEW_MAP = Pattern.compile("""
            <map version="1\\.0\\.1">(\\r?\\n)\
            <!-- To view this file, download free mind mapping software FreeMind from http://freemind\\.sourceforge\\.net -->\\1\
            <node CREATED="(\\d+)" ID="(ID_\\d+)" MODIFIED="(\\d+)" TEXT="New Mindmap"/>\\1\
            </map>\\1""");

    private VirtualFile directory;

    @Override
    public void setUp() {
        super.setUp();
        try {
            directory = myFixture.getTempDirFixture().findOrCreateDir("maps");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void tearDown() throws Exception {
        try {
            TestDialogManager.setTestInputDialog(null);
        } finally {
            super.tearDown();
        }
    }

    @Test
    public void isInTheNewGroupRightAfterNewFile() {
        ActionManager actions = ActionManager.getInstance();
        List<String> ids = Stream.of(((DefaultActionGroup) actions.getAction("NewGroup")).getChildActionsOrStubs()).map(actions::getId).toList();
        AnAction newMap = actions.getAction(NewMindMapAction.ID);

        assertNotNull(newMap);
        assertEquals("FreeMind Mind Map", newMap.getTemplatePresentation().getText());
        assertEquals(ids.toString(), ids.indexOf("NewFile") + 1, ids.indexOf(NewMindMapAction.ID));
    }

    @Test
    public void createsANewMapAndOpensItInTheTool() throws IOException {
        long before = System.currentTimeMillis();
        VirtualFile file = create("mindmap");
        long after = System.currentTimeMillis();

        assertEquals("mindmap.mm", file.getName());
        Matcher matcher = NEW_MAP.matcher(new String(file.contentsToByteArray(), UTF_8));
        assertTrue(matcher.matches());
        assertEquals("one clock read", matcher.group(2), matcher.group(4));
        long created = Long.parseLong(matcher.group(2));
        assertTrue(before <= created && created <= after);

        var editor = assertInstanceOf(FileEditorManager.getInstance(getProject()).getSelectedEditor(file), AdpEditorProvider.Composite.class);
        MindMap map = (MindMap) editor.tool().model();
        assertNotNull(map);
        assertEquals("New Mindmap", map.root().text());
        assertTrue(map.root().children().isEmpty());
        assertFalse(editor.isModified());
    }

    @Test
    public void eachNewMapGetsAFreshId() throws IOException {
        Matcher a = NEW_MAP.matcher(new String(create("one").contentsToByteArray(), UTF_8));
        Matcher b = NEW_MAP.matcher(new String(create("two").contentsToByteArray(), UTF_8));

        assertTrue(a.matches() && b.matches());
        assertFalse(a.group(3).equals(b.group(3)));
    }

    @Test
    public void keepsAnExtensionThatIsThere() {
        assertEquals("plan.mm", create("plan.mm").getName());
        assertEquals("plan.MM", NewMindMapAction.fileName("plan.MM"));
    }

    @Test
    public void refusesAnExistingName() {
        create("taken");
        NewMindMapAction.NameValidator validator = new NewMindMapAction.NameValidator(directory);

        assertFalse(validator.checkInput("taken"));
        assertFalse(validator.checkInput("taken.mm"));
        assertNotNull(validator.getErrorText("taken"));
        assertTrue(validator.checkInput("free"));
        assertFalse(validator.checkInput(" "));
    }

    @Test
    public void writesTheProjectsLineSeparator() throws IOException {
        var settings = CodeStyle.getSettings(getProject());
        String before = settings.LINE_SEPARATOR;
        settings.LINE_SEPARATOR = "\r\n";
        try {
            Matcher matcher = NEW_MAP.matcher(new String(create("windows").contentsToByteArray(), UTF_8));
            assertTrue(matcher.matches());
            assertEquals("\r\n", matcher.group(1));
        } finally {
            settings.LINE_SEPARATOR = before;
        }
    }

    /** Run the action as the New menu would, answering its name prompt with {@code name}. */
    private VirtualFile create(String name) {
        TestDialogManager.setTestInputDialog(message -> name);
        AnAction action = ActionManager.getInstance().getAction(NewMindMapAction.ID);
        var context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject()).add(CommonDataKeys.VIRTUAL_FILE, directory).build();
        AnActionEvent event = AnActionEvent.createEvent(context, action.getTemplatePresentation().clone(), "NewGroup", ActionUiKind.NONE, null);
        ActionUtil.performDumbAwareUpdate(action, event, true);
        assertTrue(event.getPresentation().isEnabledAndVisible());
        ActionUtil.performActionDumbAwareWithCallbacks(action, event);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        VirtualFile file = directory.findChild(NewMindMapAction.fileName(name));
        assertNotNull(file);
        return file;
    }
}
