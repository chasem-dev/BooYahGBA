package ygba.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public final class Log {

    private static boolean initialized = false;

    private Log() {
    }

    public static synchronized void initFromProperties() {
        if (initialized) return;
        initialized = true;

        String logPath = System.getProperty("ygba.log.file");
        if (logPath == null || logPath.trim().length() == 0) return;

        File logFile = new File(logPath).getAbsoluteFile();
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            PrintStream fileStream = new PrintStream(new FileOutputStream(logFile, false), true, "UTF-8");

            System.setOut(new PrintStream(new TeeOutputStream(originalOut, fileStream), true));
            System.setErr(new PrintStream(new TeeOutputStream(originalErr, fileStream), true));

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    fileStream.flush();
                    fileStream.close();
                }
            }));

            System.out.println("[LOG] writing emulator output to " + logFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[LOG] failed to initialize log file: " + e.getMessage());
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream left;
        private final OutputStream right;

        private TeeOutputStream(OutputStream left, OutputStream right) {
            this.left = left;
            this.right = right;
        }

        public void write(int b) throws IOException {
            left.write(b);
            right.write(b);
        }

        public void write(byte[] b) throws IOException {
            left.write(b);
            right.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            left.write(b, off, len);
            right.write(b, off, len);
        }

        public void flush() throws IOException {
            left.flush();
            right.flush();
        }
    }
}
