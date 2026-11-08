package com.snovasys.trustvault.storage;

import com.snovasys.trustvault.service.VaultExceptions.BadRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

@Component
public class PrivateFileStorage {
    private final Path root;
    private final long maxBytes;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom random = new SecureRandom();
    private static final byte[] MAGIC = new byte[]{'T','V','0','1'};

    public PrivateFileStorage(@Value("${app.storage-dir}") String storageDir,
                              @Value("${app.max-upload-bytes}") long maxBytes,
                              @Value("${app.file-encryption-key}") String encryptionKeyBase64) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encryptionKeyBase64); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("app.file-encryption-key must be Base64", ex); }
        if (decoded.length != 32) throw new IllegalStateException("app.file-encryption-key must decode to exactly 32 bytes");
        this.encryptionKey = new SecretKeySpec(decoded, "AES");
        try { Files.createDirectories(root); }
        catch (IOException ex) { throw new IllegalStateException("Cannot initialise private file storage", ex); }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequest("Choose a non-empty file");
        if (file.getSize() > maxBytes) throw new BadRequest("File exceeds the 10 MB limit");
        String original = cleanFilename(file.getOriginalFilename());
        String extension = extension(original);
        try {
            byte[] plaintext = file.getBytes();
            String contentType = validateAndDetect(plaintext, extension);
            String key = UUID.randomUUID().toString();
            writeEncrypted(key, plaintext);
            return new StoredFile(key, original, contentType, plaintext.length, root.resolve(key));
        } catch (IOException ex) {
            throw new BadRequest("Unable to read uploaded file");
        }
    }

    public StoredFile storeSeedText(String filename, byte[] bytes) {
        String key = UUID.randomUUID().toString();
        writeEncrypted(key, bytes);
        return new StoredFile(key, filename, "text/plain", bytes.length, root.resolve(key));
    }

    public byte[] readDecrypted(String storageKey) {
        try {
            Path path = resolve(storageKey);
            byte[] stored = Files.readAllBytes(path);
            if (stored.length < 4 + 12 + 16 || !Arrays.equals(Arrays.copyOfRange(stored, 0, 4), MAGIC))
                throw new IllegalStateException("Encrypted file header is invalid");
            byte[] iv = Arrays.copyOfRange(stored, 4, 16);
            byte[] ciphertext = Arrays.copyOfRange(stored, 16, stored.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt stored document", ex);
        }
    }

    private void writeEncrypted(String key, byte[] plaintext) {
        Path target = root.resolve(key).normalize();
        if (!target.getParent().equals(root)) throw new IllegalStateException("Invalid storage key");
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer buffer = ByteBuffer.allocate(MAGIC.length + iv.length + ciphertext.length);
            buffer.put(MAGIC).put(iv).put(ciphertext);
            Files.write(target, buffer.array(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt and store document", ex);
        }
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}")) throw new IllegalStateException("Invalid storage key");
        Path path = root.resolve(storageKey).normalize();
        if (!path.getParent().equals(root) || !Files.isRegularFile(path)) throw new IllegalStateException("Stored file missing");
        return path;
    }

    public void deleteUncommitted(String storageKey) {
        try { Files.deleteIfExists(resolve(storageKey)); } catch (Exception ignored) { /* best-effort cleanup only */ }
    }

    private String validateAndDetect(byte[] bytes, String ext) {
        if ("pdf".equals(ext) && starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if ("png".equals(ext) && starts(bytes, new byte[]{(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
        if (("jpg".equals(ext) || "jpeg".equals(ext)) && bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) return "image/jpeg";
        if ("txt".equals(ext)) {
            try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException ex) { throw new BadRequest("Text files must be valid UTF-8"); }
            for (byte b : bytes) if (b == 0) throw new BadRequest("Text file contains a null byte");
            return "text/plain";
        }
        throw new BadRequest("Unsupported file type or file signature mismatch. Allowed: PDF, PNG, JPG, JPEG, TXT");
    }
    private boolean starts(byte[] actual, byte[] prefix) {
        if (actual.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (actual[i] != prefix[i]) return false;
        return true;
    }
    private String cleanFilename(String name) {
        if (name == null || name.isBlank()) throw new BadRequest("Filename is required");
        String leaf = name.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\u0000]", "_").trim();
        if (leaf.isBlank()) throw new BadRequest("Filename is invalid");
        return leaf.length() > 180 ? leaf.substring(leaf.length() - 180) : leaf;
    }
    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
