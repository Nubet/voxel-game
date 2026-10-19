package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public final class FramebufferCapture {
    private FramebufferCapture() {}

    public static BufferedImage capture(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Framebuffer is empty");
        int bytes = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        ByteBuffer pixels = MemoryUtil.memAlloc(bytes);
        int framebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_PACK_ALIGNMENT);
        int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        int skipRows = glGetInteger(GL_PACK_SKIP_ROWS);
        int skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        int readBuffer = glGetInteger(GL_READ_BUFFER);
        try {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glPixelStorei(GL_PACK_ROW_LENGTH, 0);
            glPixelStorei(GL_PACK_SKIP_ROWS, 0);
            glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
            glReadBuffer(GL_BACK);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] rgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4;
                    rgb[(height - y - 1) * width + x] = Byte.toUnsignedInt(pixels.get(offset)) << 16
                            | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                            | Byte.toUnsignedInt(pixels.get(offset + 2));
                }
            }
            return image;
        } finally {
            glReadBuffer(readBuffer);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
            glPixelStorei(GL_PACK_ALIGNMENT, alignment);
            glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_PACK_SKIP_ROWS, skipRows);
            glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels);
            MemoryUtil.memFree(pixels);
        }
    }
}
