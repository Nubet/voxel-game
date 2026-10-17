package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.PlayerSkin;
import com.norbertfila.assets.SkinLoader;
import com.norbertfila.core.GameSettings;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.screens.AppearanceScreen;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

final class SkinManagerSmoke {
    static void checkLifecycle() throws Exception {
        var tasks = new LinkedBlockingQueue<Supplier<SkinLoader.Loaded>>();
        var worker = Executors.newSingleThreadExecutor();
        Thread glThread = Thread.currentThread();
        var first = new SkinLoader.Loaded(new PlayerSkin(new byte[64 * 64 * 4]), "first", "");
        byte[] rgba = first.skin().rgba();
        rgba[0] = 42;
        var latest = new SkinLoader.Loaded(new PlayerSkin(rgba), "latest", "");
        tasks.add(() -> first);
        int shared;
        try (var skins = new SkinManager(worker, () -> {
            assertNotSame(glThread, Thread.currentThread());
            try {
                return tasks.take().get();
            } catch (InterruptedException failure) {
                throw new IllegalStateException(failure);
            }
        })) {
            assertEquals(0, skins.texture());
            PlayerModelSmoke.awaitSkin(skins);
            int original = skins.texture();
            assertTrue(glIsTexture(original));
            var staleStarted = new CountDownLatch(1);
            var releaseStale = new CountDownLatch(1);
            var latestStarted = new CountDownLatch(1);
            var releaseLatest = new CountDownLatch(1);
            tasks.add(() -> {
                staleStarted.countDown();
                await(releaseStale);
                return first;
            });
            skins.reload();
            assertTrue(staleStarted.await(5, TimeUnit.SECONDS));
            tasks.add(() -> {
                latestStarted.countDown();
                await(releaseLatest);
                return latest;
            });
            skins.reload();
            releaseStale.countDown();
            assertTrue(latestStarted.await(5, TimeUnit.SECONDS));
            skins.update();
            assertEquals(original, skins.texture());
            assertEquals(SkinManager.State.LOADING, skins.status().state());
            releaseLatest.countDown();
            PlayerModelSmoke.awaitSkin(skins);
            shared = skins.texture();
            assertNotEquals(original, shared);
            assertFalse(glIsTexture(original));
            assertEquals("latest", skins.status().source());
            try (var a = new PlayerRenderer(skins);
                    var b = new PlayerRenderer(skins)) {
                a.close();
                assertTrue(glIsTexture(shared));
            }
            tasks.add(() -> latest);
            var appearance = new AppearanceScreen(new GameSettings(), skins);
            appearance.keyPress(UiKey.NEXT);
            appearance.keyPress(UiKey.NEXT);
            appearance.keyPress(UiKey.ACTIVATE);
            assertEquals(SkinManager.State.LOADING, skins.status().state());
            PlayerModelSmoke.awaitSkin(skins);
            assertEquals(shared, skins.texture());
            for (int i = 0; i < 100; i++) skins.update();
            assertEquals(shared, skins.texture());
            var wrongThread = worker.submit(() -> assertThrows(IllegalStateException.class, skins::update));
            wrongThread.get(5, TimeUnit.SECONDS);
            tasks.add(() -> {
                throw new IllegalStateException("test decode failure");
            });
            skins.reload();
            PlayerModelSmoke.awaitSkin(skins);
            assertEquals(SkinManager.State.ERROR, skins.status().state());
            assertEquals(shared, skins.texture());
            skins.close();
            skins.close();
            assertFalse(glIsTexture(shared));
            assertThrows(IllegalStateException.class, skins::reload);
        }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        checkExternalReload();
        assertEquals(GL_NO_ERROR, glGetError());
        System.out.println(
                "PASS shared skin async sequencing, unchanged cache, keyboard reload, fallback, GL ownership and worker lifecycle");
    }

    private static void checkExternalReload() throws Exception {
        var directory = Files.createTempDirectory("skin-smoke-");
        var path = directory.resolve("player.png");
        var loader = new SkinLoader();
        var worker = Executors.newSingleThreadExecutor();
        int texture;
        try (var skins = new SkinManager(worker, () -> loader.load(path))) {
            PlayerModelSmoke.awaitSkin(skins);
            assertEquals(SkinManager.State.FALLBACK, skins.status().state());
            int fallback = skins.texture();
            ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", path.toFile());
            skins.reload();
            assertEquals(fallback, skins.texture());
            PlayerModelSmoke.awaitSkin(skins);
            assertEquals(SkinManager.State.EXTERNAL, skins.status().state());
            assertNotEquals(fallback, skins.texture());
            Files.write(path, new byte[] {1, 2, 3});
            skins.reload();
            PlayerModelSmoke.awaitSkin(skins);
            assertEquals(SkinManager.State.FALLBACK, skins.status().state());
            assertFalse(skins.status().diagnostic().isEmpty());
            texture = skins.texture();
            Files.delete(path);
            skins.reload();
            PlayerModelSmoke.awaitSkin(skins);
            assertEquals(texture, skins.texture());
            skins.reload();
            skins.close();
            assertFalse(glIsTexture(texture));
        } finally {
            Files.deleteIfExists(path);
            Files.delete(directory);
        }
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
