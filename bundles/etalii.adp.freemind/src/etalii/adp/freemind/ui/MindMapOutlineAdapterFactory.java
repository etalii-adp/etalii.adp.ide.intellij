package etalii.adp.freemind.ui;

import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

/**
 * Gives each {@link MindMapEditor} its Outline page (FR-026). Registered in {@code plugin.xml}, and
 * consulted by the editor's {@code getAdapter}, so the core framework needs no outline hook.
 */
public class MindMapOutlineAdapterFactory implements IAdapterFactory {

    private final Map<MindMapEditor, MindMapOutlinePage> pages = new WeakHashMap<>();

    @Override
    public <T> T getAdapter(Object adaptable, Class<T> adapterType) {
        if (adapterType != IContentOutlinePage.class || !(adaptable instanceof MindMapEditor editor)) {
            return null;
        }
        MindMapOutlinePage page = pages.get(editor);
        if (page == null || (page.getControl() != null && page.getControl().isDisposed())) {
            page = new MindMapOutlinePage(editor);
            pages.put(editor, page);
        }
        return adapterType.cast(page);
    }

    @Override
    public Class<?>[] getAdapterList() {
        return new Class<?>[] { IContentOutlinePage.class };
    }
}
