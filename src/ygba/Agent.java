package ygba;

import ygba.cpu.ARM7TDMI;
import ygba.gfx.GFX;
import ygba.memory.IORegMemory;
import ygba.memory.Memory;
import ygba.memory.SavePersistence;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Headless programmatic API for the GBA emulator.
 * No AWT imports are required to construct or step frames;
 * getFrameAsImage() is the only method that uses AWT (opt-in).
 */
public final class Agent {

    private final YGBA ygba;
    private final Memory memory;
    private final IORegMemory iorMem;
    private final GFX gfx;

    public Agent(String biosPath, String romPath) {
        ygba = new YGBA();
        memory = ygba.getMemory();
        iorMem = memory.getIORegMemory();
        gfx = ygba.getGraphics();

        ygba.setFramePacing(false);

        try {
            memory.loadBIOS(new File(biosPath).toURI().toURL());
            memory.loadROM(new File(romPath).toURI().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid path: " + e.getMessage(), e);
        }

        if (!ygba.isReady()) {
            throw new IllegalStateException("Failed to load BIOS or ROM");
        }

        ygba.reset();
    }

    public void setupSavePersistence(File saveDir, String romFileName) {
        ygba.setupSavePersistence(saveDir, romFileName);
    }

    public void runOneFrame() {
        ygba.runOneFrame();
        SavePersistence sp = ygba.getSavePersistence();
        if (sp != null) sp.flushIfSettled();
    }

    public void runFrames(int n) {
        for (int i = 0; i < n; i++) {
            ygba.runOneFrame();
        }
    }

    public int[] getPixels() {
        int[] src = gfx.getPixels();
        int[] copy = new int[src.length];
        System.arraycopy(src, 0, copy, 0, src.length);
        return copy;
    }

    public BufferedImage getFrameAsImage() {
        int w = GFX.XScreenSize;
        int h = GFX.YScreenSize;
        BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        frame.setRGB(0, 0, w, h, gfx.getPixels(), 0, w);
        return frame;
    }

    public void pressButton(int btnMask) {
        iorMem.pressButton(btnMask);
    }

    public void releaseButton(int btnMask) {
        iorMem.releaseButton(btnMask);
    }

    public ARM7TDMI getCPU() {
        return ygba.getCPU();
    }

    public Memory getMemory() {
        return memory;
    }

    public void stop() {
        ygba.stop();
    }
}
