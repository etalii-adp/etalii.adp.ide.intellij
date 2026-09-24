package etalii.adp.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import javax.swing.JComponent;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * A second, minimal format for the framework's own tests: a {@code .txt} file whose first line is
 * {@code #adpfake}, one item per following line. A line starting with {@code !} cannot be shown.
 * It needs no change to core (FR-019): a provider, a sniff and a designer are all a format writes.
 */
final class FakeFormat {

    static final String HEADER = "#adpfake\n";
    static final String EDITOR_TYPE_ID = "etalii.adp.fake.editor";

    private FakeFormat() {
    }

    /** Register the provider for the rest of the test. */
    static Provider register(Disposable testDisposable) {
        Provider provider = new Provider();
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(provider, testDisposable);
        return provider;
    }

    static final class Provider extends AdpEditorProvider {

        int sniffedBytes;

        @Override
        protected Set<String> extensions() {
            return Set.of("txt");
        }

        @Override
        protected boolean sniff(byte[] head) {
            sniffedBytes = head.length;
            return new String(head, UTF_8).startsWith(HEADER);
        }

        @Override
        protected AdpDesignerEditor<?> createDesigner(Project project, VirtualFile file, Document document) {
            return new Designer(project, file, document);
        }

        @Override
        protected String editorName() {
            return "Fake Items";
        }

        @Override
        public String getEditorTypeId() {
            return EDITOR_TYPE_ID;
        }
    }

    /** The parse result: the item lines. Keys are the lines' indexes. */
    record Items(List<String> lines) {
    }

    static final class Designer extends AdpDesignerEditor<Items> {

        static final int ROW = 20;

        int parseCount;
        Items shown;
        Object revealed;

        Designer(Project project, VirtualFile file, Document document) {
            super(project, file, document);
        }

        @Override
        protected Items parse(CharSequence text) throws FormatProblem {
            parseCount++;
            String all = text.toString();
            if (!all.startsWith(HEADER)) {
                throw new FormatProblem("No " + HEADER.trim() + " header", 0);
            }
            List<String> lines = List.of(all.substring(HEADER.length()).split("\n"));
            int offset = HEADER.length();
            for (String line : lines) {
                if (line.startsWith("!")) {
                    throw new FormatProblem("An item cannot start with !", offset);
                }
                offset += line.length() + 1;
            }
            return new Items(lines);
        }

        @Override
        protected JComponent createView() {
            return new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    if (shown != null) {
                        for (int i = 0; i < shown.lines().size(); i++) {
                            g.drawString(shown.lines().get(i), 2, (i + 1) * ROW - 5);
                        }
                    }
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(200, shown == null ? 0 : shown.lines().size() * ROW);
                }
            };
        }

        @Override
        protected void modelChanged(Items model) {
            shown = model;
            view().revalidate();
        }

        @Override
        public void reveal(Object key) {
            revealed = key;
            select(List.of(key));
        }

        @Override
        public NodeView viewOf(Object key) {
            int index = (Integer) key;
            if (shown == null || index < 0 || index >= shown.lines().size()) {
                return null;
            }
            return new NodeView(key, new Rectangle(0, index * ROW, 200, ROW), shown.lines().get(index), Color.BLACK, null,
                    new Font(Font.DIALOG, Font.PLAIN, 12), List.of(), false, false, false);
        }

        @Override
        protected List<?> allKeys() {
            return shown == null ? List.of() : IntStream.range(0, shown.lines().size()).boxed().toList();
        }

        @Override
        public StructureViewBuilder getStructureViewBuilder() {
            return new AdpStructureView(this) {
                @Override
                protected Object rootKey() {
                    return shown == null ? null : "root";
                }

                @Override
                protected String text(Object key) {
                    return key.equals("root") ? "Items" : shown.lines().get((Integer) key);
                }

                @Override
                protected List<?> children(Object key) {
                    return key.equals("root") ? allKeys() : List.of();
                }
            };
        }
    }
}
