package ygba.util;

import java.util.concurrent.atomic.AtomicInteger;

public final class MemoryWriteWatch {

    private static final long startAddress = parseAddress(System.getProperty("ygba.watch.write.start"), -1L);
    private static final long endAddress = parseAddress(System.getProperty("ygba.watch.write.end"), -1L);
    private static final int maxLogs = parseInt(System.getProperty("ygba.watch.write.max"), 256);
    private static final boolean nonZeroOnly = parseBoolean(System.getProperty("ygba.watch.write.nonzeroOnly"), false);
    private static final boolean logByteWrites;
    private static final boolean logHalfWordWrites;
    private static final boolean logWordWrites;
    private static final boolean enabled = (startAddress >= 0 && endAddress > startAddress);
    private static final AtomicInteger logged = new AtomicInteger(0);

    static {
        boolean[] writeSizes = parseSizes(System.getProperty("ygba.watch.write.sizes"));
        logByteWrites = writeSizes[0];
        logHalfWordWrites = writeSizes[1];
        logWordWrites = writeSizes[2];
    }

    private static final ThreadLocal<Context> context = new ThreadLocal<Context>() {
        protected Context initialValue() {
            return new Context();
        }
    };

    private MemoryWriteWatch() {}

    private static long parseAddress(String value, long fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                return Long.parseLong(trimmed.substring(2), 16) & 0xFFFFFFFFL;
            }
            return Long.parseLong(trimmed) & 0xFFFFFFFFL;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) return true;
        if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) return false;
        return fallback;
    }

    private static boolean[] parseSizes(String value) {
        boolean[] sizes = new boolean[] { true, true, true };
        if (value == null || value.trim().isEmpty()) return sizes;
        sizes[0] = false;
        sizes[1] = false;
        sizes[2] = false;
        String[] tokens = value.split(",");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if ("1".equals(token) || "byte".equalsIgnoreCase(token) || "bytes".equalsIgnoreCase(token)) {
                sizes[0] = true;
            } else if ("2".equals(token) || "half".equalsIgnoreCase(token) || "halfword".equalsIgnoreCase(token)) {
                sizes[1] = true;
            } else if ("4".equals(token) || "word".equalsIgnoreCase(token) || "words".equalsIgnoreCase(token)) {
                sizes[2] = true;
            }
        }
        return sizes;
    }

    private static boolean shouldLogSize(int size) {
        if (size == 1) return logByteWrites;
        if (size == 2) return logHalfWordWrites;
        if (size == 4) return logWordWrites;
        return true;
    }

    private static int maskValue(int size, int value) {
        if (size == 1) return value & 0xFF;
        if (size == 2) return value & 0xFFFF;
        return value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setCPUContext(int pc, boolean thumb, int opcode) {
        if (!enabled) return;
        Context c = context.get();
        c.pc = pc;
        c.thumb = thumb;
        c.opcode = opcode;
        c.valid = true;
    }

    public static void clearCPUContext() {
        if (!enabled) return;
        context.get().valid = false;
    }

    public static void logWrite(int address, int size, int value) {
        if (!enabled) return;
        if (!shouldLogSize(size)) return;
        long writeStart = address & 0xFFFFFFFFL;
        long writeEnd = writeStart + size;
        if (writeEnd <= startAddress || writeStart >= endAddress) return;
        int maskedValue = maskValue(size, value);
        if (nonZeroOnly && maskedValue == 0) return;

        int index = logged.incrementAndGet();
        if (index > maxLogs) {
            if (index == (maxLogs + 1)) {
                System.out.printf("[WATCH] reached max logs (%d), suppressing further entries%n", maxLogs);
            }
            return;
        }

        Context c = context.get();
        if (c.valid) {
            System.out.printf("[WATCH] addr=%08X size=%d value=%08X pc=%08X state=%s opcode=%08X%n",
                    address, size, maskedValue, c.pc, c.thumb ? "THUMB" : "ARM", c.opcode);
        } else {
            System.out.printf("[WATCH] addr=%08X size=%d value=%08X pc=<none>%n",
                    address, size, maskedValue);
        }
    }

    private static final class Context {
        int pc;
        int opcode;
        boolean thumb;
        boolean valid;
    }
}
