package etalii.adp.freemind.ui;

import etalii.adp.core.diagram.edit.RefusalFeedback;

/**
 * The editing gestures (drag-and-drop move, double-click rename) come from the framework's
 * editing and properties features, which every diagram canvas gets when it is made (research R19);
 * no listener adds them any more. What is left here is what spec 001's tests ask of the old
 * installer.
 */
public final class EditingInstaller {

    private EditingInstaller() {
    }

    /** Whether the editing gestures are on this canvas: the framework's editing feature is installed on it. */
    public static boolean isAttached(MindMapCanvas canvas) {
        return RefusalFeedback.of(canvas) != null;
    }
}
