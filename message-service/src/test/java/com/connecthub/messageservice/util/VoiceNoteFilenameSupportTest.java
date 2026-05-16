package com.connecthub.messageservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceNoteFilenameSupportTest {

    @Test
    void supportedChecksAcceptKnownExtensionsAndContentTypes() {
        assertThat(VoiceNoteFilenameSupport.isSupportedVoiceNote("note.webm", null)).isTrue();
        assertThat(VoiceNoteFilenameSupport.isSupportedVoiceNote("blob", "audio/webm")).isTrue();
        assertThat(VoiceNoteFilenameSupport.isSupportedVoiceNote("note.txt", "audio/plain")).isFalse();
        assertThat(VoiceNoteFilenameSupport.hasSupportedExtension("note.MP3")).isTrue();
        assertThat(VoiceNoteFilenameSupport.hasSupportedExtension("note")).isFalse();
    }

    @Test
    void normalizeVoiceNoteFilenameBuildsStableNamesForGenericUploads() {
        assertThat(VoiceNoteFilenameSupport.normalizeVoiceNoteFilename("blob", "audio/webm"))
                .isEqualTo("voice-note.webm");
        assertThat(VoiceNoteFilenameSupport.normalizeVoiceNoteFilename("recording.", "audio/mpeg"))
                .isEqualTo("voice-note.mp3");
        assertThat(VoiceNoteFilenameSupport.normalizeVoiceNoteFilename("meeting", "audio/x-wav"))
                .isEqualTo("meeting.wav");
        assertThat(VoiceNoteFilenameSupport.normalizeVoiceNoteFilename("already.webm", "audio/webm"))
                .isEqualTo("already.webm");
    }

    @Test
    void inferExtensionUnderstandsKnownAliasesAndIgnoresUnknownTypes() {
        assertThat(VoiceNoteFilenameSupport.inferExtension("audio/webm;codecs=opus")).isEqualTo("webm");
        assertThat(VoiceNoteFilenameSupport.inferExtension("video/webm")).isEqualTo("webm");
        assertThat(VoiceNoteFilenameSupport.inferExtension("audio/x-flac")).isEqualTo("flac");
        assertThat(VoiceNoteFilenameSupport.inferExtension("application/pdf")).isNull();
        assertThat(VoiceNoteFilenameSupport.inferExtension(null)).isNull();
    }
}
