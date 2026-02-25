package ygba;

import ygba.cpu.ARM7TDMI;
import ygba.memory.Memory;
import ygba.memory.SaveMemory;
import ygba.memory.SavePersistence;
import ygba.dma.DirectMemoryAccess;
import ygba.gfx.GFX;
import ygba.time.Time;

import java.io.File;
import java.util.Arrays;

public final class YGBA {
    
    private ARM7TDMI cpu;
    private Memory memory;
    private DirectMemoryAccess dma;
    private GFX gfx;
    private Time time;
    
    private YGBACore ygbaCore;
    private Thread ygbaThread;

    private SavePersistence savePersistence;
    private Thread shutdownHook;
    
    
    public YGBA() {
        cpu = new ARM7TDMI();
        memory = new Memory();
        dma = new DirectMemoryAccess();
        gfx = new GFX();
        time = new Time();
        
        ygbaCore = new YGBACore(cpu, memory, time);
        ygbaThread = null;
        
        setupConnections();
    }
    
    private void setupConnections() {
        cpu.connectToMemory(memory);
        memory.connectToDMA(dma);
        memory.connectToGraphics(gfx);
        memory.connectToTime(time);
        dma.connectToMemory(memory);
        gfx.connectToMemory(memory);
        time.connectToMemory(memory);
    }
    
    
    public ARM7TDMI getCPU() { return cpu; }
    
    public Memory getMemory() { return memory; }
    
    public DirectMemoryAccess getDMA() { return dma; }
    
    public GFX getGraphics() { return gfx; }
    
    public Time getTime() { return time; }
    
    
    public void runOneFrame() {
        ygbaCore.runOneFrame();
    }

    public void setFramePacing(boolean enabled) {
        ygbaCore.setFramePacing(enabled);
    }

    public void reset() {
        cpu.reset();
        memory.reset();
        dma.reset();
        gfx.reset();
        time.reset();
        
        ygbaThread = null;
        
        System.gc();
    }
    
    public void run() {
        ygbaThread = new Thread(ygbaCore);
        ygbaThread.setPriority(Thread.NORM_PRIORITY);
        ygbaThread.start();
    }
    
    public void stop() {
        if (ygbaThread != null) {
            ygbaCore.stop();
            try { ygbaThread.join(); } catch (InterruptedException e) {}
            ygbaThread = null;
        }
        if (savePersistence != null) {
            savePersistence.flushNow();
        }
    }
    
    public void setupSavePersistence(File saveDir, String romSource) {
        String saveName = SavePersistence.romNameToSaveName(romSource);
        File saveFile = new File(saveDir, saveName);

        SaveMemory saveMem = memory.getSaveMemory();

        // Fill with 0xFF (erased flash state) before loading
        Arrays.fill(saveMem.getSpace(), (byte) 0xFF);

        savePersistence = new SavePersistence(saveMem, saveFile);
        saveMem.setPersistence(savePersistence);
        savePersistence.loadIfExists();

        ygbaCore.setSavePersistence(savePersistence);

        // Safety net for unclean exits
        shutdownHook = new Thread(new Runnable() {
            public void run() {
                if (savePersistence != null) savePersistence.flushNow();
            }
        }, "ygba-save-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        System.out.println("[SAVE] persistence configured: " + saveFile.getPath());
    }

    public SavePersistence getSavePersistence() {
        return savePersistence;
    }

    public boolean isReady() {
        return (memory.isBIOSLoaded() && memory.isROMLoaded());
    }
    
}
