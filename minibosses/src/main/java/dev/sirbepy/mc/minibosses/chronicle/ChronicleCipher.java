package dev.sirbepy.mc.minibosses.chronicle;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

/**
 * AES-256-GCM for the Chronicles' story text. The repo is public, so the story ships inside the
 * jar only as {@code chronicles.enc} (a 12-byte IV followed by ciphertext and tag); the base64 key
 * lives in the gitignored {@code server/secrets.properties} and on the server as
 * {@code plugins/Minibosses/chronicles.key}. GCM's tag makes a wrong key fail loudly instead of
 * decoding garbage. The build's {@code encryptChronicles}/{@code decryptChronicles} tasks run
 * {@link #main} so both sides share this one implementation.
 */
public final class ChronicleCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private ChronicleCipher() {
    }

    /** {@code encrypt|decrypt <plain.yml> <sealed.enc> <secrets.properties>}, for the Gradle tasks. */
    public static void main(String[] args) throws Exception {
        Properties secrets = new Properties();
        try (var in = Files.newInputStream(Path.of(args[3]))) {
            secrets.load(in);
        }
        String key = secrets.getProperty("minibosses.chronicles-key");
        if (key == null) {
            throw new IllegalStateException(args[3] + " has no minibosses.chronicles-key");
        }
        Path plain = Path.of(args[1]), sealed = Path.of(args[2]);
        if (args[0].equals("encrypt")) {
            Files.write(sealed, encrypt(Files.readAllBytes(plain), key));
        } else {
            Files.createDirectories(plain.toAbsolutePath().getParent());
            Files.write(plain, decrypt(Files.readAllBytes(sealed), key));
        }
    }

    public static byte[] encrypt(byte[] plain, String base64Key) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(base64Key), new GCMParameterSpec(TAG_BITS, iv));
        byte[] sealed = cipher.doFinal(plain);
        byte[] out = Arrays.copyOf(iv, IV_BYTES + sealed.length);
        System.arraycopy(sealed, 0, out, IV_BYTES, sealed.length);
        return out;
    }

    public static byte[] decrypt(byte[] blob, String base64Key) throws GeneralSecurityException {
        if (blob.length <= IV_BYTES) {
            throw new GeneralSecurityException("Chronicles blob is too short to hold an IV and tag");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(base64Key), new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
        return cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
    }

    private static SecretKeySpec key(String base64Key) throws GeneralSecurityException {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Chronicles key is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new GeneralSecurityException("Chronicles key must be 32 bytes, got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }
}
