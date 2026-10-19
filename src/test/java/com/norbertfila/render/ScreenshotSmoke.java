package com.norbertfila.render;

import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.persistence.ScreenshotWriter;
import com.norbertfila.platform.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public final class ScreenshotSmoke {
    public static void main(String[] args) throws Exception {
        try (var window = new Window(320, 240, "Screenshot regression");
                var writer = new ScreenshotWriter(Path.of(args[0]))) {
            for (int size : new int[] {240, 360}) {
                glfwSetWindowSize(window.handle(), size + 1, size);
                window.pollEvents();
                int[] width = new int[1], height = new int[1];
                window.framebufferSize(width, height);
                glViewport(0, 0, width[0], height[0]);
                glClearColor(1, 0, 0, 0);
                glClear(GL_COLOR_BUFFER_BIT);
                glEnable(GL_SCISSOR_TEST);
                glScissor(0, height[0] / 2, width[0], height[0] - height[0] / 2);
                glClearColor(0, 1, 0, 0);
                glClear(GL_COLOR_BUFFER_BIT);
                glDisable(GL_SCISSOR_TEST);

                int buffer = glGenBuffers();
                int framebuffer = glGenFramebuffers();
                try {
                    glBindBuffer(GL_PIXEL_PACK_BUFFER, buffer);
                    glBufferData(GL_PIXEL_PACK_BUFFER, 64, GL_STREAM_READ);
                    glPixelStorei(GL_PACK_ALIGNMENT, 8);
                    glPixelStorei(GL_PACK_ROW_LENGTH, 7);
                    glPixelStorei(GL_PACK_SKIP_ROWS, 2);
                    glPixelStorei(GL_PACK_SKIP_PIXELS, 3);
                    glReadBuffer(GL_FRONT);
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
                    BufferedImage captured = FramebufferCapture.capture(width[0], height[0]);
                    check(glGetInteger(GL_READ_FRAMEBUFFER_BINDING) == framebuffer, "Read framebuffer changed");
                    check(glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING) == buffer, "Pack buffer changed");
                    check(glGetInteger(GL_PACK_ALIGNMENT) == 8, "Pack alignment changed");
                    check(glGetInteger(GL_PACK_ROW_LENGTH) == 7, "Pack row length changed");
                    check(glGetInteger(GL_PACK_SKIP_ROWS) == 2, "Pack skip rows changed");
                    check(glGetInteger(GL_PACK_SKIP_PIXELS) == 3, "Pack skip pixels changed");
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
                    check(glGetInteger(GL_READ_BUFFER) == GL_FRONT, "Read buffer changed");
                    Path saved = writer.save(captured).get(10, TimeUnit.SECONDS);
                    BufferedImage decoded = ImageIO.read(saved.toFile());
                    check(decoded.getWidth() == width[0] && decoded.getHeight() == height[0], "Wrong PNG size");
                    check(decoded.getRGB(0, 0) == 0xff00ff00, "Wrong top color or alpha");
                    check(decoded.getRGB(width[0] - 1, height[0] - 1) == 0xffff0000, "Wrong bottom color");
                    check(glGetError() == GL_NO_ERROR, "Screenshot GL error");
                } finally {
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
                    glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
                    glDeleteFramebuffers(framebuffer);
                    glDeleteBuffers(buffer);
                }
                window.swapBuffers();
            }
            System.out.println("PASS screenshot PNG colors, orientation, resize and OpenGL state");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
