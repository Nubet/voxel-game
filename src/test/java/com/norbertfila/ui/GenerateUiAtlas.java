package com.norbertfila.ui;

import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateUiAtlas {
    public static void main(String[] args) throws Exception {
        Font font;
        try (var stream = Files.newInputStream(Path.of(args[0]))) {
            font = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(12f);
        }
        FontRenderContext context = new FontRenderContext(null, false, false);
        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        for (int character = 33; character <= 126; character++) {
            var bounds = font.createGlyphVector(context, new char[] {(char) character})
                    .getPixelBounds(context, 0, 0);
            if (bounds.width > 16) throw new IllegalArgumentException("Glyph too wide: " + character);
            top = Math.min(top, bounds.y);
            bottom = Math.max(bottom, bounds.y + bounds.height);
        }
        if (bottom - top > 16)
            throw new IllegalArgumentException("Glyphs exceed cell height: top=" + top + " bottom=" + bottom);
        BufferedImage image = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffffffff);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            graphics.setColor(java.awt.Color.WHITE);
            for (int character = 33; character <= 126; character++) {
                var glyph = font.createGlyphVector(context, new char[] {(char) character});
                var bounds = glyph.getPixelBounds(context, 0, 0);
                graphics.drawGlyphVector(glyph, character % 16 * 16 - bounds.x, character / 16 * 16 - top);
            }
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", Path.of(args[1]).toFile()))
            throw new IllegalStateException("PNG writer unavailable");
        System.out.println("Generated " + font.getFontName() + " at 12px; ink height=" + (bottom - top));
    }
}
