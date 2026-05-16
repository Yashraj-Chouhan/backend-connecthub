package com.connecthub.translationservice.controller;

import com.connecthub.translationservice.dto.TranscriptionResponse;
import com.connecthub.translationservice.dto.TranslationRequest;
import com.connecthub.translationservice.dto.TranslationResponse;
import com.connecthub.translationservice.service.SpeechTranscriptionService;
import com.connecthub.translationservice.service.TranslationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
/**
 * Accepts translation requests from other services or clients and delegates the
 * actual work to the translation business layer.
 */
@Validated
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService service;
    private final SpeechTranscriptionService speechTranscriptionService;

    @PostMapping({"/translate", "/api/translate"})
    public TranslationResponse translate(@Valid @RequestBody TranslationRequest request) {
        return service.translate(request);
    }

    @PostMapping(value = {"/transcribe", "/api/transcribe"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(@RequestPart("file") MultipartFile file,
                                            @RequestParam(required = false) String sourceLang) {
        return speechTranscriptionService.transcribe(file, sourceLang);
    }
}
