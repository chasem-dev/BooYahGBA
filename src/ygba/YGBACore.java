package ygba;

import ygba.cpu.ARM7TDMI;
import ygba.memory.Memory;
import ygba.memory.IORegMemory;
import ygba.time.Time;

public final class YGBACore
        implements Runnable {

    private ARM7TDMI cpu;
    private IORegMemory iorMem;
    private Time time;

    private boolean stopped;
    private boolean framePacing = true;
    private final boolean debugConsole;
    private final boolean debugStatus;

    private int lastFramePC;
    private int stalledFrames;
    private int statusFrameCount;
    private long statusWindowStart;
    
    
    public YGBACore(ARM7TDMI cpu, Memory memory, Time time) {
        this.cpu = cpu;
        this.iorMem = memory.getIORegMemory();
        this.time = time;
        
        stopped = true;
        debugConsole = Boolean.getBoolean("ygba.debug.console");
        debugStatus = Boolean.getBoolean("ygba.debug.status");
        lastFramePC = Integer.MIN_VALUE;
        stalledFrames = 0;
        statusFrameCount = 0;
        statusWindowStart = 0;
    }
    
    
    private final static int
            // Horizontal Dimensions
            HDrawDots  = 240,
            HBlankDots = 68,
            HDots      = HDrawDots + HBlankDots,
            // Vertical Dimensions
            VDrawLines  = 160,
            VBlankLines = 68,
            VLines      = VDrawLines + VBlankLines,
            // Timings
            CyclesPerDot    = 4,
            CyclesPerHDraw  = HDrawDots * CyclesPerDot,
            CyclesPerHBlank = HBlankDots * CyclesPerDot,
            CyclesPerLine   = CyclesPerHDraw + CyclesPerHBlank;
    
    // GBA runs at ~59.7275 fps => ~16.743 ms per frame
    private final static long FRAME_TIME_NS = 16_743_000L;

    public void setFramePacing(boolean enabled) {
        this.framePacing = enabled;
    }

    public void runOneFrame() {
        for (int scanline = 0; scanline < VLines; scanline++) {
            iorMem.setCurrentScanline(scanline);
            cpu.run(CyclesPerHDraw);
            iorMem.enterHBlank();
            cpu.run(CyclesPerHBlank);
            iorMem.exitHBlank();
            time.addTime(CyclesPerLine);
            if (scanline == VDrawLines - 1) iorMem.enterVBlank();
            else if (scanline == VLines - 1) iorMem.exitVBlank();
        }
    }

    public void run() {
        stopped = false;

        long frameStart = System.nanoTime();
        statusWindowStart = frameStart;
        statusFrameCount = 0;

        while (!stopped) {
            runOneFrame();

            if (debugConsole) logIfStalled();
            if (debugStatus) logPeriodicStatus();

            if (framePacing) {
                long elapsed = System.nanoTime() - frameStart;
                long sleepNs = FRAME_TIME_NS - elapsed;
                if (sleepNs > 0) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000, (int) (sleepNs % 1_000_000));
                    } catch (InterruptedException e) {}
                }
                frameStart = System.nanoTime();
            }
        }
    }

    public void stop() {
        stopped = true;
    }

    private void logIfStalled() {
        int currentPC = cpu.getCurrentPC();
        if (currentPC == lastFramePC) stalledFrames++;
        else {
            lastFramePC = currentPC;
            stalledFrames = 0;
        }

        if (stalledFrames > 180 && (stalledFrames % 60) == 0) {
            int ime = iorMem.getHalfWord(IORegMemory.REG_IME) & 0xFFFF;
            int ie = iorMem.getHalfWord(IORegMemory.REG_IE) & 0xFFFF;
            int interruptFlags = iorMem.getHalfWord(IORegMemory.REG_IF) & 0xFFFF;
            int dispstat = iorMem.getHalfWord(IORegMemory.REG_DISPSTAT) & 0xFFFF;
            int vcount = iorMem.getCurrentScanline();
            System.out.printf(
                    "[DBG] possible stall pc=%08X frames=%d mode=%s T=%b I=%b IME=%04X IE=%04X IF=%04X DISPSTAT=%04X VCOUNT=%d%n",
                    currentPC, stalledFrames, cpu.getModeName(), cpu.getTFlag(), cpu.getIFlag(),
                    ime, ie, interruptFlags, dispstat, vcount);
        }
    }

    private void logPeriodicStatus() {
        statusFrameCount++;
        long now = System.nanoTime();
        long elapsed = now - statusWindowStart;
        if (elapsed < 1_000_000_000L) return;

        double fps = statusFrameCount * 1_000_000_000.0 / elapsed;
        int currentPC = cpu.getCurrentPC();
        int dispcnt = iorMem.getHalfWord(IORegMemory.REG_DISPCNT) & 0xFFFF;
        int dispstat = iorMem.getHalfWord(IORegMemory.REG_DISPSTAT) & 0xFFFF;
        int bldcnt = iorMem.getHalfWord(IORegMemory.REG_BLDMOD) & 0xFFFF;
        int ime = iorMem.getHalfWord(IORegMemory.REG_IME) & 0xFFFF;
        int ie = iorMem.getHalfWord(IORegMemory.REG_IE) & 0xFFFF;
        int interruptFlags = iorMem.getHalfWord(IORegMemory.REG_IF) & 0xFFFF;
        int vcount = iorMem.getCurrentScanline();

        System.out.printf(
                "[STAT] fps=%.2f pc=%08X cpu=%s T=%b I=%b vmode=%d LY=%d DISPCNT=%04X DISPSTAT=%04X BLDCNT=%04X IME=%04X IE=%04X IF=%04X%n",
                fps, currentPC, cpu.getModeName(), cpu.getTFlag(), cpu.getIFlag(), iorMem.getVideoMode(), vcount,
                dispcnt, dispstat, bldcnt, ime, ie, interruptFlags);

        statusFrameCount = 0;
        statusWindowStart = now;
    }
    
}
