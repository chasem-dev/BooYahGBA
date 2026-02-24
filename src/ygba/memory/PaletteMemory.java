package ygba.memory;

public final class PaletteMemory
        extends MemoryManager_16_32 {
    
    
    public PaletteMemory() {
        super("Palette RAM", 0x400);
    }

    public byte loadByte(int offset) {
        offset = getInternalOffset(offset);
        return space[offset];
    }

    // 8-bit palette writes mirror to both bytes of the addressed halfword.
    public void storeByte(int offset, byte value) {
        offset = getInternalOffset(offset) & 0xFFFFFFFE;
        space[offset] = value;
        space[offset + 1] = value;
    }
    
}
