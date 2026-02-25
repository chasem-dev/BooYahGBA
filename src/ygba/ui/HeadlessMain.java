package ygba.ui;

import ygba.Agent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class HeadlessMain {

    public static void main(String[] args) {
        String biosPath = null;
        String romPath = null;
        int frames = 60;
        String screenshotPath = null;
        String saveDirPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bios":
                    biosPath = args[++i];
                    break;
                case "--rom":
                    romPath = args[++i];
                    break;
                case "--frames":
                    frames = Integer.parseInt(args[++i]);
                    break;
                case "--screenshot":
                    screenshotPath = args[++i];
                    break;
                case "--save-dir":
                    saveDirPath = args[++i];
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }

        if (biosPath == null || romPath == null) {
            System.err.println("Usage: HeadlessMain --bios <path> --rom <path> [--frames <n>] [--screenshot <path.png>] [--save-dir <dir>]");
            System.exit(1);
        }

        if (!new File(biosPath).exists()) {
            System.err.println("[HEADLESS] BIOS file not found: " + biosPath);
            System.exit(1);
        }
        if (!new File(romPath).exists()) {
            System.err.println("[HEADLESS] ROM file not found: " + romPath);
            System.exit(1);
        }

        File saveDir = new File(saveDirPath != null ? saveDirPath : ".");

        System.out.println("[HEADLESS] bios=" + biosPath);
        System.out.println("[HEADLESS] rom=" + romPath);
        System.out.println("[HEADLESS] frames=" + frames);
        System.out.println("[HEADLESS] save-dir=" + saveDir.getAbsolutePath());

        Agent agent = new Agent(biosPath, romPath);
        if (!saveDir.exists()) saveDir.mkdirs();
        agent.setupSavePersistence(saveDir, romPath);

        long startNs = System.nanoTime();
        agent.runFrames(frames);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        System.out.println("[HEADLESS] completed " + frames + " frames in " + elapsedMs + "ms");
        System.out.printf("[HEADLESS] PC=0x%08X%n", agent.getCPU().getCurrentPC());

        if (screenshotPath != null) {
            try {
                BufferedImage image = agent.getFrameAsImage();
                ImageIO.write(image, "png", new File(screenshotPath));
                System.out.println("[HEADLESS] screenshot=" + screenshotPath);
            } catch (IOException e) {
                System.err.println("[HEADLESS] failed to write screenshot: " + e.getMessage());
                System.exit(1);
            }
        }

        agent.stop();
    }
}
