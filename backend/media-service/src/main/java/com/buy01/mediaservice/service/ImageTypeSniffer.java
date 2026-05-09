package com.buy01.mediaservice.service;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ImageTypeSniffer {

    public Optional<String> detectContentType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return Optional.empty();
        }

        if (startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of("image/png");
        }
        if (startsWithAscii(bytes, "GIF87a") || startsWithAscii(bytes, "GIF89a")) {
            return Optional.of("image/gif");
        }
        if (startsWithAscii(bytes, "BM")) {
            return Optional.of("image/bmp");
        }
        if (startsWithAscii(bytes, "RIFF") && bytes.length > 12 && matchesAscii(bytes, 8, "WEBP")) {
            return Optional.of("image/webp");
        }

        return Optional.empty();
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] bytes, String value) {
        return matchesAscii(bytes, 0, value);
    }

    private boolean matchesAscii(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (bytes[offset + i] != (byte) value.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
