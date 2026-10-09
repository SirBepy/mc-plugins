package dev.sirbepy.mc.endfight.reset;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** {@code plugins/EndFight/island-snapshot.bin} (de)serialization: GZIP-wraps
 * {@link IslandSnapshotCodec}'s plain binary format and writes atomically (temp file + rename),
 * same crash-safety pattern as {@code YamlFiles.saveAtomically}. */
public class IslandSnapshotStore {

    private final File file;
    private final Logger logger;

    public IslandSnapshotStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public boolean exists() {
        return file.isFile();
    }

    public long fileSizeBytes() {
        return file.length();
    }

    public long ageMillis(long now) {
        return file.exists() ? Math.max(0, now - file.lastModified()) : -1;
    }

    /** Encodes {@code bounds} through {@code lookup} and writes it, timed end to end. */
    public SnapshotStats save(SnapshotBounds bounds, BlockDataLookup lookup) throws IOException {
        long start = System.currentTimeMillis();
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File temp = new File(parent, file.getName() + ".tmp");
        try (var fileOut = new FileOutputStream(temp);
             var buffered = new BufferedOutputStream(fileOut);
             var gzip = new GZIPOutputStream(buffered)) {
            IslandSnapshotCodec.encode(bounds, lookup, gzip);
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        long millis = System.currentTimeMillis() - start;
        return new SnapshotStats(bounds.volume(), file.length(), millis);
    }

    public Optional<IslandSnapshot> load() {
        if (!file.isFile()) {
            return Optional.empty();
        }
        try (var fileIn = new FileInputStream(file);
             var buffered = new BufferedInputStream(fileIn);
             var gzip = new GZIPInputStream(buffered)) {
            return Optional.of(IslandSnapshotCodec.decode(gzip));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; treating the island snapshot as missing", e);
            return Optional.empty();
        }
    }
}
