package etalii.adp.freemind.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RichTextTest {

    @Test
    void dropsTagsAndTheHead() {
        assertEquals("Hello world", RichText.toText(
                "<html><head><style>p { color: red }</style></head><body><p>Hello <b>world</b></p></body></html>"));
    }

    @Test
    void blockTagsAndBreaksBecomeLineBreaks() {
        assertEquals("one\ntwo\nthree", RichText.toText("<html><body><p>one</p><p>two<br/>three</p></body></html>"));
        assertEquals("a\nb", RichText.toText("<ul><li>a</li><li>b</li></ul>"));
        assertEquals("title\nbody", RichText.toText("<h1>title</h1><div>body</div>"));
    }

    @Test
    void collapsesSourceWhitespace() {
        assertEquals("spaced out", RichText.toText("<html>\n  <body>\n    <p>\n      spaced\n      out\n    </p>\n  </body>\n</html>"));
    }

    @Test
    void decodesNamedAndNumericEntities() {
        assertEquals("caf\u00e9 & th\u00e9 <3\u00a0x \u2014 \u00a9", RichText.toText("<p>caf&#233; &amp; th&eacute; &lt;3&nbsp;x &#x2014; &copy;</p>"));
    }
}
