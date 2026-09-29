package etalii.adp.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.LeakHunter;

import etalii.adp.testing.ToolDriver;

/**
 * Spec 006 FR-006: a tool that was opened, edited through its text and closed holds no memory
 * afterwards. Nothing the IDE keeps (the document, its listeners, undo history, tool windows) may
 * still reach it.
 */
@RunWith(JUnit4.class)
public class ToolLeakTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
        FakeFormat.register(getTestRootDisposable());
    }

    @Test
    public void closedToolsAreNotReachable() {
        for (int i = 0; i < 5; i++) {
            try (var d = ToolDriver.openText(myFixture, "items" + i + ".txt", FakeFormat.HEADER + "alpha\nbeta\n")) {
                assertNotNull(d.tool());
                d.editText(t -> t + "gamma\n");
            }
        }
        LeakHunter.checkLeak(LeakHunter.allRoots(), AdpToolFileEditor.class, null);
    }
}
