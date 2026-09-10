package io.github.paracosms.calquake.testsupport;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility to generate official USGS ShakeMap MMI icons in SVG and PNG formats.
 */
public class MmiIconGenerator {

    public record IconSpec(String id, String roman, String colorHex, String textColorHex) {}

    public static final List<IconSpec> ICONS = List.of(
            new IconSpec("mmi_i", "I", "#fbfcff", "#000000"),
            new IconSpec("mmi_ii", "II", "#acdbff", "#000000"),
            new IconSpec("mmi_iii", "III", "#acdbff", "#000000"),
            new IconSpec("mmi_ii_iii", "II-III", "#acdbff", "#000000"),
            new IconSpec("mmi_iv", "IV", "#7ffffa", "#000000"),
            new IconSpec("mmi_v", "V", "#81ff8a", "#000000"),
            new IconSpec("mmi_vi", "VI", "#fffa00", "#000000"),
            new IconSpec("mmi_vii", "VII", "#ffc400", "#000000"),
            new IconSpec("mmi_viii", "VIII", "#ff8500", "#ffffff"),
            new IconSpec("mmi_ix", "IX", "#fb0000", "#ffffff"),
            new IconSpec("mmi_x", "X", "#c80000", "#ffffff"),
            new IconSpec("mmi_x_plus", "X+", "#c80000", "#ffffff"),
            new IconSpec("mmi_na", "N/A", "#808080", "#ffffff")
    );

    public static void main(String[] args) throws IOException {
        Path outputDir = Path.of("src/main/resources/icons/mmi");
        Files.createDirectories(outputDir);
        generateAll(outputDir);
        System.out.println("Generated all MMI icons in " + outputDir.toAbsolutePath());
    }

    public static void generateAll(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        for (IconSpec spec : ICONS) {
            generateSvg(spec, outputDir.resolve(spec.id() + ".svg"));
            generatePng(spec, outputDir.resolve(spec.id() + ".png"), 64);
        }
    }

    public static void generateSvg(IconSpec spec, Path outputPath) throws IOException {
        int fontSize = getFontSize(spec.roman(), 64);
        String svg = String.format("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">
                  <rect x="2" y="2" width="60" height="60" rx="8" ry="8" fill="%s" stroke="#334155" stroke-width="2.5"/>
                  <text x="32" y="32" fill="%s" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-weight="bold" font-size="%d" text-anchor="middle" dominant-baseline="central">%s</text>
                </svg>
                """, spec.colorHex(), spec.textColorHex(), fontSize, spec.roman());
        Files.writeString(outputPath, svg);
    }

    public static void generatePng(IconSpec spec, Path outputPath, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            double strokeWidth = size * (2.5 / 64.0);
            double cornerRadius = size * (16.0 / 64.0);
            double inset = strokeWidth / 2.0 + (size * (1.0 / 64.0));
            double rectSize = size - (inset * 2.0);

            RoundRectangle2D rect = new RoundRectangle2D.Double(inset, inset, rectSize, rectSize, cornerRadius, cornerRadius);

            // Fill
            g2d.setColor(Color.decode(spec.colorHex()));
            g2d.fill(rect);

            // Stroke
            g2d.setColor(Color.decode("#334155"));
            g2d.setStroke(new BasicStroke((float) strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.draw(rect);

            // Text
            int fontSize = getFontSize(spec.roman(), size);
            Font font = new Font("Segoe UI", Font.BOLD, fontSize);
            g2d.setFont(font);
            g2d.setColor(Color.decode(spec.textColorHex()));

            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(spec.roman());
            // Center text vertically using ascents/descents
            int textX = (size - textWidth) / 2;
            int textY = ((size - fm.getHeight()) / 2) + fm.getAscent();

            g2d.drawString(spec.roman(), textX, textY);
        } finally {
            g2d.dispose();
        }

        ImageIO.write(image, "PNG", outputPath.toFile());
    }

    private static int getFontSize(String text, int size) {
        double scale = size / 64.0;
        int baseSize;
        if (text.length() <= 1) {
            baseSize = 31;
        } else if (text.length() == 2) {
            baseSize = 27;
        } else if (text.length() == 3) {
            baseSize = 23;
        } else if (text.length() == 4) {
            baseSize = 19;
        } else {
            baseSize = 16;
        }
        return (int) Math.round(baseSize * scale);
    }
}
