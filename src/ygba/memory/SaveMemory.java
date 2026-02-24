package ygba.memory;

public final class SaveMemory
        extends MemoryManager {

    // Flash chip states
    private static final int STATE_READY = 0;
    private static final int STATE_CMD_1 = 1;     // Received 0xAA at 0x5555
    private static final int STATE_CMD_2 = 2;     // Received 0x55 at 0x2AAA
    private static final int STATE_ID = 3;         // Chip ID mode
    private static final int STATE_ERASE = 4;      // Erase command pending
    private static final int STATE_ERASE_1 = 5;    // Erase: received 0xAA at 0x5555
    private static final int STATE_ERASE_2 = 6;    // Erase: received 0x55 at 0x2AAA
    private static final int STATE_WRITE = 7;      // Single byte write mode
    private static final int STATE_BANK = 8;       // Bank select mode

    private int state = STATE_READY;
    private int bankOffset = 0;  // 0 or 0x10000 for 128K flash

    // Sanyo 128KB Flash chip IDs (LE106F) — common for Pokemon FireRed
    private static final int MANUFACTURER_ID = 0x62;
    private static final int DEVICE_ID = 0x13;


    public SaveMemory() {
        super("Save RAM", 0x20000); // 128KB flash
    }


    public byte loadByte(int offset) {
        offset = getInternalOffset(offset) & 0xFFFF; // Keep within 64K window

        if (state == STATE_ID) {
            if (offset == 0x0000) return (byte) MANUFACTURER_ID;
            if (offset == 0x0001) return (byte) DEVICE_ID;
            return 0;
        }

        return space[bankOffset + offset];
    }

    public short loadHalfWord(int offset) { return 0; }

    public int loadWord(int offset) { return 0; }


    public void storeByte(int offset, byte value) {
        offset = getInternalOffset(offset) & 0xFFFF;
        int v = value & 0xFF;

        switch (state) {
            case STATE_READY:
                if (offset == 0x5555 && v == 0xAA) {
                    state = STATE_CMD_1;
                }
                break;

            case STATE_CMD_1:
                if (offset == 0x2AAA && v == 0x55) {
                    state = STATE_CMD_2;
                } else {
                    state = STATE_READY;
                }
                break;

            case STATE_CMD_2:
                if (offset == 0x5555) {
                    switch (v) {
                        case 0x90: // Enter chip ID mode
                            state = STATE_ID;
                            break;
                        case 0x80: // Erase command
                            state = STATE_ERASE;
                            break;
                        case 0xA0: // Write byte command
                            state = STATE_WRITE;
                            break;
                        case 0xB0: // Bank switch command
                            state = STATE_BANK;
                            break;
                        case 0xF0: // Exit/reset
                            state = STATE_READY;
                            break;
                        default:
                            state = STATE_READY;
                    }
                } else {
                    state = STATE_READY;
                }
                break;

            case STATE_ID:
                if (v == 0xF0) {
                    state = STATE_READY;
                } else if (offset == 0x5555 && v == 0xAA) {
                    state = STATE_ERASE_1; // Could be re-entering command sequence
                }
                break;

            case STATE_ERASE:
                if (offset == 0x5555 && v == 0xAA) {
                    state = STATE_ERASE_1;
                } else {
                    state = STATE_READY;
                }
                break;

            case STATE_ERASE_1:
                if (offset == 0x2AAA && v == 0x55) {
                    state = STATE_ERASE_2;
                } else {
                    state = STATE_READY;
                }
                break;

            case STATE_ERASE_2:
                if (offset == 0x5555 && v == 0x10) {
                    // Erase entire chip
                    for (int i = 0; i < space.length; i++) space[i] = (byte) 0xFF;
                } else if (v == 0x30) {
                    // Erase 4KB sector
                    int sector = bankOffset + (offset & 0xF000);
                    for (int i = sector; i < sector + 0x1000 && i < space.length; i++) {
                        space[i] = (byte) 0xFF;
                    }
                }
                state = STATE_READY;
                break;

            case STATE_WRITE:
                // Single byte program (can only clear bits, not set them — but we'll allow any write)
                space[bankOffset + offset] = value;
                state = STATE_READY;
                break;

            case STATE_BANK:
                if (offset == 0x0000) {
                    bankOffset = (v & 0x01) * 0x10000;
                }
                state = STATE_READY;
                break;

            default:
                state = STATE_READY;
        }
    }

    public void storeHalfWord(int offset, short value) {}

    public void storeWord(int offset, int value) {}

}
