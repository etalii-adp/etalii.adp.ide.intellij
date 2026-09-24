package etalii.adp.freemind;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.ITextContentDescriber;

/**
 * Decides which {@code .mm} files are FreeMind maps (FR-002): those whose first element is
 * {@code <map>}. Anything else, such as Objective-C++ source, is INVALID, so it opens with the
 * editor that would otherwise apply. The platform's XML root describer answers INDETERMINATE for
 * text that is not XML, and as the only content type for {@code *.mm} it would still win.
 */
public class MindMapContentDescriber implements ITextContentDescriber {

    private static final int PREFIX = 4096;
    private static final Pattern ENCODING = Pattern.compile("^<\\?xml[^>]*\\bencoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    @Override
    public int describe(InputStream contents, IContentDescription description) throws IOException {
        return describe(new InputStreamReader(contents, StandardCharsets.ISO_8859_1), description);
    }

    @Override
    public int describe(Reader contents, IContentDescription description) throws IOException {
        char[] buffer = new char[PREFIX];
        int length = 0;
        for (int read; length < PREFIX && (read = contents.read(buffer, length, PREFIX - length)) > 0;) {
            length += read;
        }
        String prefix = new String(buffer, 0, length);
        if (prefix.startsWith("\ufeff") || prefix.startsWith("\u00ef\u00bb\u00bf")) {
            prefix = prefix.substring(prefix.charAt(0) == '\ufeff' ? 1 : 3);
        }
        if (!rootIsMap(prefix)) {
            return INVALID;
        }
        if (description != null && description.isRequested(IContentDescription.CHARSET)) {
            Matcher encoding = ENCODING.matcher(prefix);
            if (encoding.find()) {
                description.setProperty(IContentDescription.CHARSET, encoding.group(1));
            }
        }
        return VALID;
    }

    /** Skips whitespace, the XML declaration, processing instructions, comments and a DOCTYPE. */
    private static boolean rootIsMap(String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (text.startsWith("<?", i)) {
                i = skipPast(text, "?>", i);
            } else if (text.startsWith("<!--", i)) {
                i = skipPast(text, "-->", i);
            } else if (text.startsWith("<!", i)) {
                i = skipPast(text, ">", i);
            } else {
                return text.startsWith("<map", i) && i + 4 < text.length()
                        && (Character.isWhitespace(text.charAt(i + 4)) || text.charAt(i + 4) == '>' || text.charAt(i + 4) == '/');
            }
            if (i < 0) {
                return false;
            }
        }
        return false;
    }

    private static int skipPast(String text, String terminator, int from) {
        int end = text.indexOf(terminator, from);
        return end < 0 ? -1 : end + terminator.length();
    }

    @Override
    public QualifiedName[] getSupportedOptions() {
        return new QualifiedName[] { IContentDescription.CHARSET };
    }
}
