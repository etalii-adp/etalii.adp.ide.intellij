package etalii.adp.core;

/** A colour a file states, 0 to 255 per channel. The tool turns it into a toolkit colour when it paints. */
public record Rgb(int red, int green, int blue) {

    public Rgb {
        if ((red | green | blue) < 0 || red > 255 || green > 255 || blue > 255) {
            throw new IllegalArgumentException("A colour channel is 0 to 255");
        }
    }
}
