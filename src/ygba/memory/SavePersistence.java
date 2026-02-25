package ygba.memory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;

public final class SavePersistence {

    private final SaveMemory saveMem;
    private final File saveFile;

    private volatile boolean dirty;
    private volatile long lastDirtyTimeNanos;

    private static final long SETTLE_NS = 1_000_000_000L; // 1 second

    public SavePersistence(SaveMemory saveMem, File saveFile) {
        this.saveMem = saveMem;
        this.saveFile = saveFile;
    }

    public void loadIfExists() {
        if (!saveFile.exists()) return;

        try (FileInputStream fis = new FileInputStream(saveFile)) {
            byte[] space = saveMem.getSpace();
            int bytesRead = 0;
            int pos = 0;
            int remaining = space.length;
            while (remaining > 0) {
                bytesRead = fis.read(space, pos, remaining);
                if (bytesRead == -1) break;
                pos += bytesRead;
                remaining -= bytesRead;
            }
            System.out.println("[SAVE] loaded " + pos + " bytes from " + saveFile.getPath());
        } catch (IOException e) {
            System.out.println("[SAVE] failed to load save file: " + e.getMessage());
        }
    }

    public void markDirty() {
        dirty = true;
        lastDirtyTimeNanos = System.nanoTime();
    }

    public void flushIfSettled() {
        if (!dirty) return;
        if (System.nanoTime() - lastDirtyTimeNanos < SETTLE_NS) return;
        flushNow();
    }

    public void flushNow() {
        if (!dirty) return;
        dirty = false;

        try {
            File parent = saveFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                fos.write(saveMem.getSpace());
            }
            System.out.println("[SAVE] flushed to " + saveFile.getPath());
        } catch (IOException e) {
            System.out.println("[SAVE] failed to write save file: " + e.getMessage());
        }
    }

    public File getSaveFile() {
        return saveFile;
    }

    public static String romNameToSaveName(String romSource) {
        if (romSource == null || romSource.isEmpty()) return "game.sav";

        // Extract filename from URL or path
        String name = romSource;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // URL-decode
        try {
            name = URLDecoder.decode(name, "UTF-8");
        } catch (Exception e) {
            // keep as-is
        }

        // Replace .gba/.agb/.bin extension with .sav
        String lower = name.toLowerCase();
        if (lower.endsWith(".gba") || lower.endsWith(".agb") || lower.endsWith(".bin")) {
            name = name.substring(0, name.length() - 4);
        }

        // Sanitize: keep alphanumeric, dot, dash; spaces to underscore
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-') {
                sb.append(c);
            } else if (c == ' ' || c == '_') {
                sb.append('_');
            }
            // drop everything else (parens, brackets, etc.)
        }

        // Collapse consecutive underscores
        String sanitized = sb.toString().replaceAll("_+", "_");

        // Trim leading/trailing underscores
        while (sanitized.startsWith("_")) sanitized = sanitized.substring(1);
        while (sanitized.endsWith("_")) sanitized = sanitized.substring(0, sanitized.length() - 1);

        if (sanitized.isEmpty()) return "game.sav";

        return sanitized + ".sav";
    }
}
