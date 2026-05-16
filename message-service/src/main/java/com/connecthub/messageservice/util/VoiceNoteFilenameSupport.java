package com.connecthub.messageservice.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class VoiceNoteFilenameSupport {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "flac", "m4a", "mp3", "mp4", "mpeg", "mpga", "ogg", "wav", "webm"
    );

    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.ofEntries(
            Map.entry("audio/flac", "flac"),
            Map.entry("audio/x-flac", "flac"),
            Map.entry("audio/m4a", "m4a"),
            Map.entry("audio/x-m4a", "m4a"),
            Map.entry("audio/mp3", "mp3"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp4", "mp4"),
            Map.entry("audio/mpga", "mpga"),
            Map.entry("audio/ogg", "ogg"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/wave", "wav"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/webm", "webm"),
            Map.entry("video/webm", "webm")
    );

    private VoiceNoteFilenameSupport() {
    }

    public static boolean isSupportedVoiceNote(String filename, String contentType) {
        return hasSupportedExtension(filename) || inferExtension(contentType) != null;
    }

    public static boolean hasSupportedExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }

        int extensionSeparatorIndex = filename.lastIndexOf('.');
        if (extensionSeparatorIndex < 0 || extensionSeparatorIndex == filename.length() - 1) {
            return false;
        }

        String extension = filename.substring(extensionSeparatorIndex + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    public static String normalizeVoiceNoteFilename(String filename, String contentType) {
        String cleanedFilename = StringUtils.cleanPath(StringUtils.hasText(filename) ? filename : "voice-note");
        if (hasSupportedExtension(cleanedFilename)) {
            return cleanedFilename;
        }

        String inferredExtension = inferExtension(contentType);
        if (inferredExtension == null) {
            return cleanedFilename;
        }

        String baseName = stripExtension(cleanedFilename);
        if (!StringUtils.hasText(baseName) || isGenericPlaceholder(baseName)) {
            baseName = "voice-note";
        }

        return baseName + "." + inferredExtension;
    }

    public static String inferExtension(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }

        String normalizedContentType = contentType.trim().toLowerCase(Locale.ROOT);
        int parameterSeparatorIndex = normalizedContentType.indexOf(';');
        if (parameterSeparatorIndex >= 0) {
            normalizedContentType = normalizedContentType.substring(0, parameterSeparatorIndex).trim();
        }

        return CONTENT_TYPE_TO_EXTENSION.get(normalizedContentType);
    }

    private static boolean isGenericPlaceholder(String filename) {
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty()
                || "blob".equals(normalized)
                || "file".equals(normalized)
                || "audio".equals(normalized)
                || "recording".equals(normalized);
    }

    private static String stripExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return filename;
        }

        int extensionSeparatorIndex = filename.lastIndexOf('.');
        String baseName = extensionSeparatorIndex > 0 ? filename.substring(0, extensionSeparatorIndex) : filename;
        int endIndex = baseName.length();
        while (endIndex > 0 && baseName.charAt(endIndex - 1) == '.') {
            endIndex--;
        }
        return baseName.substring(0, endIndex).trim();
    }
}
