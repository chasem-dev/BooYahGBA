package ygba.memory;

public final class VideoMemory
        extends MemoryManager_16_32 {
    
    private final static int VRAMAddressMask = 0x0001FFFF;
    private final static int VRAMUpperMirrorOffset = 0x00018000;
    private final static int VRAMUpperMirrorSize = 0x00008000;
    private final static int OBJTileBaseOffsetMode0To2 = 0x10000;
    private final static int OBJTileBaseOffsetMode3To5 = 0x14000;
    
    private IORegMemory iorMem;
    private long byteWriteCount;
    private long byteWriteIgnoredCount;
    private long halfWordWriteCount;
    private long wordWriteCount;
    
    
    public VideoMemory() {
        super("Video RAM", 0x18000);
        byteWriteCount = 0;
        byteWriteIgnoredCount = 0;
        halfWordWriteCount = 0;
        wordWriteCount = 0;
    }
    
    void connectToIORegMemory(IORegMemory iorMem) {
        this.iorMem = iorMem;
    }
    
    public byte loadByte(int offset) {
        offset = getInternalOffset(offset);
        return space[offset];
    }

    // 8-bit writes mirror in BG VRAM but are ignored in OBJ VRAM.
    public void storeByte(int offset, byte value) {
        byteWriteCount++;
        offset = getInternalOffset(offset) & 0xFFFFFFFE;
        int objTileBaseOffset = OBJTileBaseOffsetMode0To2;
        if (iorMem != null) {
            int videoMode = iorMem.getVideoMode();
            if (videoMode >= 3 && videoMode <= 5) {
                objTileBaseOffset = OBJTileBaseOffsetMode3To5;
            }
        }
        if (offset >= objTileBaseOffset) {
            byteWriteIgnoredCount++;
            return;
        }
        space[offset] = value;
        space[offset + 1] = value;
    }

    public void storeHalfWord(int offset, short value) {
        halfWordWriteCount++;
        super.storeHalfWord(offset, value);
    }

    public void storeWord(int offset, int value) {
        wordWriteCount++;
        super.storeWord(offset, value);
    }
    
    public int getInternalOffset(int offset) {
        int internal = (offset & VRAMAddressMask);
        if (internal >= VRAMUpperMirrorOffset) {
            internal -= VRAMUpperMirrorSize;
        }
        return internal;
    }

    public long getByteWriteCount() {
        return byteWriteCount;
    }

    public long getByteWriteIgnoredCount() {
        return byteWriteIgnoredCount;
    }

    public long getHalfWordWriteCount() {
        return halfWordWriteCount;
    }

    public long getWordWriteCount() {
        return wordWriteCount;
    }
    
}
