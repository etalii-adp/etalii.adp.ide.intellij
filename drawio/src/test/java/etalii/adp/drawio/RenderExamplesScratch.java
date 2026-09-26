package etalii.adp.drawio;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.view.ElementView;
import etalii.adp.testing.DiagramDriver;

/** Throwaway diagnostic: renders every example to a PNG. Not part of the suite; deleted after use. */
@RunWith(JUnit4.class)
public class RenderExamplesScratch extends FileEditorManagerTestCase {

    @Test
    public void render() throws Exception {
        Path out = Path.of(System.getenv("ADP_RENDER"));
        for (String name : DrawioMappingTest.EXAMPLES) {
            try (var d = DiagramDriver.open(myFixture, DrawioMappingTest.example(name))) {
                var canvas = d.designer().canvas();
                canvas.setSize(canvas.getPreferredSize());
                BufferedImage image = new BufferedImage(Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                canvas.paint(graphics);
                graphics.dispose();
                ImageIO.write(image, "png", new File(out.toFile(), name.replace(".drawio", "") + ".png"));
                StringBuilder dump = new StringBuilder();
                for (Object key : d.elementKeys()) {
                    ElementView v = d.elementView(key);
                    dump.append(key).append(' ').append(v.type()).append(' ').append(v.bounds()).append(' ').append(v.texts()).append(" text=").append(v.text())
                            .append(" fill=").append(v.fill()).append(" plate=").append(v.plate()).append('\n');
                }
                java.nio.file.Files.writeString(out.resolve(name.replace(".drawio", "") + ".txt"), dump + "connections=" + d.connectionKeys().size() + "\n");
            }
        }
    }
}
