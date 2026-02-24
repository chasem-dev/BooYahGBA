package ygba.dma;

import ygba.memory.Memory;
import ygba.memory.IORegMemory;

public abstract class DMA {
    
    int source, destination;
    short count, control;
    boolean isEnabled, isIRQEnabled, isRepeatEnabled;
    int startTiming;

    private long triggerCount;
    private long totalUnitsTransferred;
    private long vramHalfWordUnits;
    private long vramWordUnits;
    private long ewramHalfWordUnits;
    private long ewramWordUnits;
    private int lastSourceStart;
    private int lastSourceEnd;
    private int lastDestinationStart;
    private int lastDestinationEnd;
    private int lastUnitCount;
    private int lastSourceStep;
    private int lastDestinationStep;
    private boolean lastTransfer32Bit;
    private boolean lastTransferTouchedVRAM;
    private int lastVRAMControl;
    private int lastVRAMSourceStart;
    private int lastVRAMSourceEnd;
    private int lastVRAMDestinationStart;
    private int lastVRAMDestinationEnd;
    private int lastVRAMUnitCount;
    private int lastVRAMSourceStep;
    private int lastVRAMDestinationStep;
    private boolean lastVRAMTransfer32Bit;
    private int lastEWRAMControl;
    private int lastEWRAMSourceStart;
    private int lastEWRAMSourceEnd;
    private int lastEWRAMDestinationStart;
    private int lastEWRAMDestinationEnd;
    private int lastEWRAMUnitCount;
    private int lastEWRAMSourceStep;
    private int lastEWRAMDestinationStep;
    private boolean lastEWRAMTransfer32Bit;
    
    private int dmaNumber;
    private int dmaMaxCount;
    private short dmaInterruptBit;
    
    Memory memory;
    IORegMemory iorMem;
    
    private final static int
            ImmediateStartTiming = 0x0000,
            VBlankStartTiming = 0x1000,
            HBlankStartTiming = 0x2000,
            SpecialStartTiming = 0x3000;
    
    public DMA(int dmaNumber) {
        this.dmaNumber = dmaNumber;
        dmaMaxCount = ((dmaNumber == 3) ? 0x00010000 : 0x00004000);
        dmaInterruptBit = (short) (0x0100 << dmaNumber);
    }
    
    public final void connectToMemory(Memory memory) {
        this.memory = memory;
        iorMem = memory.getIORegMemory();
    }
    
    public final void reset() {
        source = destination = 0;
        count = control = 0;
        isEnabled = isIRQEnabled = isRepeatEnabled = false;
        startTiming = 0;
        triggerCount = 0;
        totalUnitsTransferred = 0;
        vramHalfWordUnits = 0;
        vramWordUnits = 0;
        ewramHalfWordUnits = 0;
        ewramWordUnits = 0;
        lastSourceStart = 0;
        lastSourceEnd = 0;
        lastDestinationStart = 0;
        lastDestinationEnd = 0;
        lastUnitCount = 0;
        lastSourceStep = 0;
        lastDestinationStep = 0;
        lastTransfer32Bit = false;
        lastTransferTouchedVRAM = false;
        lastVRAMControl = 0;
        lastVRAMSourceStart = 0;
        lastVRAMSourceEnd = 0;
        lastVRAMDestinationStart = 0;
        lastVRAMDestinationEnd = 0;
        lastVRAMUnitCount = 0;
        lastVRAMSourceStep = 0;
        lastVRAMDestinationStep = 0;
        lastVRAMTransfer32Bit = false;
        lastEWRAMControl = 0;
        lastEWRAMSourceStart = 0;
        lastEWRAMSourceEnd = 0;
        lastEWRAMDestinationStart = 0;
        lastEWRAMDestinationEnd = 0;
        lastEWRAMUnitCount = 0;
        lastEWRAMSourceStep = 0;
        lastEWRAMDestinationStep = 0;
        lastEWRAMTransfer32Bit = false;
    }
    
    public final String getName() {
        return "DMA" + dmaNumber;
    }
    
    public void setSourceLRegister(short value) {
        source = (source & 0xFFFF0000) | (value & 0x0000FFFF);
    }
    
    public abstract void setSourceHRegister(short value);
    
    public void setDestinationLRegister(short value) {
        destination = (destination & 0xFFFF0000) | (value & 0x0000FFFF);
    }
    
    public abstract void setDestinationHRegister(short value);
    
    public abstract void setCountRegister(short value);
    
    public final void setControlRegister(short value) {
        control = value;
        isEnabled = ((control & 0x8000) != 0);
        isIRQEnabled = ((control & 0x4000) != 0);
        isRepeatEnabled = ((control & 0x0200) != 0);
        startTiming = (control & 0x3000);
        signalImmediately();
    }
    
    private final void signal(int st) {
        if (isEnabled & (startTiming == st)) {
            boolean is32BitTransfer = ((control & 0x0400) != 0);
            int dmaTransferSize = (is32BitTransfer ? 4 : 2);
            int dmaCount = ((count == 0) ? dmaMaxCount : (count & 0x0000FFFF));
            int sourceStart = source;
            int destinationStart = destination;
            boolean touchedVRAM = false;
            boolean touchedEWRAM = false;
            
            int dstAdd, srcAdd;
            int dstControl = control & 0x0060;
            int srcControl = control & 0x0180;
            
            switch (dstControl) {
                case 0x0000:
                case 0x0060: dstAdd = +dmaTransferSize; break;
                case 0x0020: dstAdd = -dmaTransferSize; break;
                case 0x0040: dstAdd = 0; break;
                default: return;
            }
            switch (srcControl) {
                case 0x0000: srcAdd = +dmaTransferSize; break;
                case 0x0080: srcAdd = -dmaTransferSize; break;
                case 0x0100: srcAdd = 0; break;
                default: return;
            }
            
            int old_destination = destination;
            if (is32BitTransfer) {
                for (int i = 0; i < dmaCount; i++) {
                    if ((destination & 0x0F000000) == 0x06000000) {
                        vramWordUnits++;
                        touchedVRAM = true;
                    } else if ((destination & 0x0F000000) == 0x02000000) {
                        ewramWordUnits++;
                        touchedEWRAM = true;
                    }
                    memory.storeWord(destination, memory.loadWord(source));
                    destination += dstAdd;
                    source += srcAdd;
                }
            } else {
                for (int i = 0; i < dmaCount; i++) {
                    if ((destination & 0x0F000000) == 0x06000000) {
                        vramHalfWordUnits++;
                        touchedVRAM = true;
                    } else if ((destination & 0x0F000000) == 0x02000000) {
                        ewramHalfWordUnits++;
                        touchedEWRAM = true;
                    }
                    memory.storeHalfWord(destination, memory.loadHalfWord(source));
                    destination += dstAdd;
                    source += srcAdd;
                }
            }

            triggerCount++;
            totalUnitsTransferred += dmaCount;
            lastSourceStart = sourceStart;
            lastSourceEnd = source;
            lastDestinationStart = destinationStart;
            lastDestinationEnd = destination;
            lastUnitCount = dmaCount;
            lastSourceStep = srcAdd;
            lastDestinationStep = dstAdd;
            lastTransfer32Bit = is32BitTransfer;
            lastTransferTouchedVRAM = touchedVRAM;
            if (touchedVRAM) {
                lastVRAMControl = control & 0xFFFF;
                lastVRAMSourceStart = sourceStart;
                lastVRAMSourceEnd = source;
                lastVRAMDestinationStart = destinationStart;
                lastVRAMDestinationEnd = destination;
                lastVRAMUnitCount = dmaCount;
                lastVRAMSourceStep = srcAdd;
                lastVRAMDestinationStep = dstAdd;
                lastVRAMTransfer32Bit = is32BitTransfer;
            }
            if (touchedEWRAM) {
                lastEWRAMControl = control & 0xFFFF;
                lastEWRAMSourceStart = sourceStart;
                lastEWRAMSourceEnd = source;
                lastEWRAMDestinationStart = destinationStart;
                lastEWRAMDestinationEnd = destination;
                lastEWRAMUnitCount = dmaCount;
                lastEWRAMSourceStep = srcAdd;
                lastEWRAMDestinationStep = dstAdd;
                lastEWRAMTransfer32Bit = is32BitTransfer;
            }
            if (dstControl == 0x0060) destination = old_destination;
            
            if (isIRQEnabled) iorMem.generateInterrupt(dmaInterruptBit);
            
            if (!isRepeatEnabled) {
                control &= ~0x8000;
                isEnabled = false;
            }
        }
    }
    
    private final void signalImmediately() {
        signal(ImmediateStartTiming);
    }
    
    public final void signalVBlank() {
        signal(VBlankStartTiming);
    }
    
    public final void signalHBlank() {
        signal(HBlankStartTiming);
    }
    
    public final void signalSpecial() {
        signal(SpecialStartTiming);
    }
    
    public final short getSourceLRegister() {
        return (short) (source & 0x0000FFFF);
    }
    
    public final short getSourceHRegister() {
        return (short) (source >>> 16);
    }
    
    public final short getDestinationLRegister() {
        return (short) (destination & 0x0000FFFF);
    }
    
    public final short getDestinationHRegister() {
        return (short) (destination >>> 16);
    }
    
    public final short getCountRegister() {
        return count;
    }
    
    public final short getControlRegister() {
        return control;
    }

    public final long getTriggerCount() {
        return triggerCount;
    }

    public final long getTotalUnitsTransferred() {
        return totalUnitsTransferred;
    }

    public final long getVRAMHalfWordUnits() {
        return vramHalfWordUnits;
    }

    public final long getVRAMWordUnits() {
        return vramWordUnits;
    }

    public final long getEWRAMHalfWordUnits() {
        return ewramHalfWordUnits;
    }

    public final long getEWRAMWordUnits() {
        return ewramWordUnits;
    }

    public final int getLastSourceStart() {
        return lastSourceStart;
    }

    public final int getLastSourceEnd() {
        return lastSourceEnd;
    }

    public final int getLastDestinationStart() {
        return lastDestinationStart;
    }

    public final int getLastDestinationEnd() {
        return lastDestinationEnd;
    }

    public final int getLastUnitCount() {
        return lastUnitCount;
    }

    public final int getLastSourceStep() {
        return lastSourceStep;
    }

    public final int getLastDestinationStep() {
        return lastDestinationStep;
    }

    public final boolean wasLastTransfer32Bit() {
        return lastTransfer32Bit;
    }

    public final boolean didLastTransferTouchVRAM() {
        return lastTransferTouchedVRAM;
    }

    public final int getLastVRAMControl() {
        return lastVRAMControl;
    }

    public final int getLastVRAMSourceStart() {
        return lastVRAMSourceStart;
    }

    public final int getLastVRAMSourceEnd() {
        return lastVRAMSourceEnd;
    }

    public final int getLastVRAMDestinationStart() {
        return lastVRAMDestinationStart;
    }

    public final int getLastVRAMDestinationEnd() {
        return lastVRAMDestinationEnd;
    }

    public final int getLastVRAMUnitCount() {
        return lastVRAMUnitCount;
    }

    public final int getLastVRAMSourceStep() {
        return lastVRAMSourceStep;
    }

    public final int getLastVRAMDestinationStep() {
        return lastVRAMDestinationStep;
    }

    public final boolean wasLastVRAMTransfer32Bit() {
        return lastVRAMTransfer32Bit;
    }

    public final int getLastEWRAMControl() {
        return lastEWRAMControl;
    }

    public final int getLastEWRAMSourceStart() {
        return lastEWRAMSourceStart;
    }

    public final int getLastEWRAMSourceEnd() {
        return lastEWRAMSourceEnd;
    }

    public final int getLastEWRAMDestinationStart() {
        return lastEWRAMDestinationStart;
    }

    public final int getLastEWRAMDestinationEnd() {
        return lastEWRAMDestinationEnd;
    }

    public final int getLastEWRAMUnitCount() {
        return lastEWRAMUnitCount;
    }

    public final int getLastEWRAMSourceStep() {
        return lastEWRAMSourceStep;
    }

    public final int getLastEWRAMDestinationStep() {
        return lastEWRAMDestinationStep;
    }

    public final boolean wasLastEWRAMTransfer32Bit() {
        return lastEWRAMTransfer32Bit;
    }
    
}
