package ygba.cpu.instruction;

import ygba.cpu.ARM7TDMI;
import ygba.memory.MemoryInterface;

public final class THUMB_4 {
    
    public static void execute(ARM7TDMI cpu, MemoryInterface memory, int opcode) {
        int rdIndex = opcode & 0x0007;
        int rsIndex = (opcode >>> 3) & 0x0007;
        int rsValue = cpu.getRegister(rsIndex);
        int shiftAmountByte = rsValue & 0x000000FF;
        int rdOldValue = cpu.getRegister(rdIndex);
        int rdNewValue = rdOldValue;
        
        switch (opcode & 0x03C0) {
            case 0x0000: // AND Rd, Rs
                rdNewValue &= rsValue;
                cpu.setRegister(rdIndex, rdNewValue);
                break;
                
            case 0x0040: // EOR Rd, Rs
                rdNewValue ^= rsValue;
                cpu.setRegister(rdIndex, rdNewValue);
                break;
                
            case 0x0080: // LSL Rd, Rs
                if (shiftAmountByte != 0) {
                    if (shiftAmountByte < 32) {
                        cpu.setCFlag((rdOldValue & (1 << (32 - shiftAmountByte))) != 0);
                        rdNewValue <<= shiftAmountByte;
                    } else if (shiftAmountByte == 32) {
                        cpu.setCFlag((rdOldValue & 0x00000001) != 0);
                        rdNewValue = 0;
                    } else {
                        cpu.setCFlag(false);
                        rdNewValue = 0;
                    }
                    cpu.setRegister(rdIndex, rdNewValue);
                }
                break;
                
            case 0x00C0: // LSR Rd, Rs
                if (shiftAmountByte != 0) {
                    if (shiftAmountByte < 32) {
                        cpu.setCFlag((rdOldValue & (1 << (shiftAmountByte - 1))) != 0);
                        rdNewValue >>>= shiftAmountByte;
                    } else if (shiftAmountByte == 32) {
                        cpu.setCFlag((rdOldValue & 0x80000000) != 0);
                        rdNewValue = 0;
                    } else {
                        cpu.setCFlag(false);
                        rdNewValue = 0;
                    }
                    cpu.setRegister(rdIndex, rdNewValue);
                }
                break;
                
            case 0x0100: // ASR Rd, Rs
                if (shiftAmountByte != 0) {
                    if (shiftAmountByte < 32) {
                        cpu.setCFlag((rdOldValue & (1 << (shiftAmountByte - 1))) != 0);
                        rdNewValue >>= shiftAmountByte;
                    } else {
                        cpu.setCFlag((rdOldValue & 0x80000000) != 0);
                        rdNewValue >>= 31;
                    }
                    cpu.setRegister(rdIndex, rdNewValue);
                }
                break;
                
            case 0x0140: { // ADC Rd, Rs
                int carry = cpu.getCFlag() ? 1 : 0;
                long longResult = (rdOldValue & 0xFFFFFFFFL) + (rsValue & 0xFFFFFFFFL) + carry;
                rdNewValue = (int) longResult;
                cpu.setCFlag(longResult > 0xFFFFFFFFL);
                cpu.setVFlag(((rdOldValue ^ rdNewValue) & (rsValue ^ rdNewValue)) < 0);
                cpu.setRegister(rdIndex, rdNewValue);
                break;
            }

            case 0x0180: { // SBC Rd, Rs
                int borrow = cpu.getCFlag() ? 0 : 1;
                long longResult = (rdOldValue & 0xFFFFFFFFL) - (rsValue & 0xFFFFFFFFL) - borrow;
                rdNewValue = (int) longResult;
                cpu.setCFlag(longResult >= 0);
                cpu.setVFlag(((rdOldValue ^ rsValue) & (rdOldValue ^ rdNewValue)) < 0);
                cpu.setRegister(rdIndex, rdNewValue);
                break;
            }
                
            case 0x01C0: // ROR Rd, Rs
                if (shiftAmountByte != 0) {
                    int shiftAmount = shiftAmountByte & 0x0000001F;
                    if (shiftAmount == 0) {
                        cpu.setCFlag((rdOldValue & 0x80000000) != 0);
                    } else {
                        cpu.setCFlag((rdOldValue & (1 << (shiftAmount - 1))) != 0);
                        rdNewValue = (rdOldValue << (32 - shiftAmount)) | (rdOldValue >>> shiftAmount);
                        cpu.setRegister(rdIndex, rdNewValue);
                    }
                }
                break;
                
            case 0x0200: // TST Rd, Rs
                rdNewValue &= rsValue;
                break;
                
            case 0x0240: // NEG Rd, Rs
                rdNewValue = -rsValue;
                cpu.setVCFlagsForSUB(0, rsValue, rdNewValue);
                cpu.setRegister(rdIndex, rdNewValue);
                break;
                
            case 0x0280: // CMP Rd, Rs
                rdNewValue -= rsValue;
                cpu.setVCFlagsForSUB(rdOldValue, rsValue, rdNewValue);
                break;
                
            case 0x02C0: // CMN Rd, Rs
                rdNewValue += rsValue;
                cpu.setVCFlagsForADD(rdOldValue, rsValue, rdNewValue);
                break;
                
            case 0x0300: // ORR Rd, Rs
                rdNewValue |= rsValue;
                cpu.setRegister(rdIndex, rdNewValue);
                break;
                
            case 0x0340: // MUL Rd, Rs
                rdNewValue *= rsValue;
                cpu.setRegister(rdIndex, rdNewValue);
                break;
                
            case 0x0380: // BIC Rd, Rs
                rdNewValue &= ~rsValue;
                cpu.setRegister(rdIndex, rdNewValue);
                break;
                
            case 0x03C0: // MVN Rd, Rs
                rdNewValue = ~rsValue;
                cpu.setRegister(rdIndex, rdNewValue);
                break;
                
            default: // Unknown
        }
        
        cpu.setZFlag(rdNewValue == 0);
        cpu.setNFlag(rdNewValue < 0);
    }
    
    
    final static String[] InstructionName = {
        "and",
        "eor",
        "lsl",
        "lsr",
        "asr",
        "adc",
        "sbc",
        "ror",
        "tst",
        "neg",
        "cmp",
        "cmn",
        "orr",
        "mul",
        "bic",
        "mvn",
    };
    
    public static String disassemble(ARM7TDMI cpu, MemoryInterface memory, int opcode, int offset) {
        int instruction = (opcode >>> 6) & 0x000F;
        String rd = cpu.getRegisterName(opcode & 0x0007);
        String rs = cpu.getRegisterName((opcode >>> 3) & 0x0007);
        
        return InstructionName[instruction] + " " + rd + ", " + rs;
    }
    
}
