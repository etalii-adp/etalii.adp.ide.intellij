package etalii.adp.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.LeakHunter;

import etalii.adp.testing.DesignerDriver;

/**
 * Spec 006 FR-006: a designer that was opened, edited through its text and closed holds no memory
 * afterwards. Nothing the IDE keeps (the document, its listeners, undo history, tool windows) may
 * still reach it.
 */
@RunWith(JUnit4.class)
public class DesignerLeakTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
        FakeFormat.register(getTestRootDisposable());
    }

    @Test
    public void closedDesignersAreNotReachable() {
        for (int i = 0; i < 5; i++) {
            try (var d = DesignerDriver.openText(myFixture, "items" + i + ".txt", FakeFormat.HEADER + "alpha\nbeta\n")) {
                assertNotNull(d.designer());
                d.editText(t -> t + "gamma\n");
            }
        }
        LeakHunter.checkLeak(LeakHunter.allRoots(), AdpDesignerEditor.class, null);
    }
}
