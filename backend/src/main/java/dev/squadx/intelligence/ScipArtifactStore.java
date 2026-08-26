package dev.squadx.intelligence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Stores revision-pinned SCIP artifacts with content-addressed integrity checks. */
public final class ScipArtifactStore {
    private final Path root;

    public ScipArtifactStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path put(String snapshotId, byte[] artifact, String expectedSha256) {
        if (snapshotId == null || !snapshotId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Invalid snapshot id");
        }
        String actual = sha256(artifact);
        if (expectedSha256 != null && !expectedSha256.isBlank() && !actual.equalsIgnoreCase(expectedSha256)) {
            throw new IllegalArgumentException("SCIP artifact checksum mismatch");
        }
        try {
            Files.createDirectories(root);
            Path target = root.resolve(snapshotId + ".scip").normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid artifact path");
            Path temporary = Files.createTempFile(root, snapshotId + ".", ".tmp");
            try {
                Files.write(temporary, artifact);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to store SCIP artifact", e);
        }
    }

    public byte[] read(String snapshotId, String expectedSha256) {
        try {
            Path target = root.resolve(snapshotId + ".scip").normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                throw new IllegalStateException("SCIP artifact not found");
            }
            byte[] artifact = Files.readAllBytes(target);
            if (expectedSha256 != null && !sha256(artifact).equalsIgnoreCase(expectedSha256)) {
                throw new IllegalStateException("SCIP artifact checksum mismatch");
            }
            return artifact;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read SCIP artifact", e);
        }
    }

    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
