package com.connecthub.translationservice.service;

import com.connecthub.translationservice.dto.TranslationRequest;
import com.connecthub.translationservice.dto.TranslationResponse;
import com.connecthub.translationservice.service.provider.TranslationJob;
import com.connecthub.translationservice.service.provider.TranslationProvider;
import com.connecthub.translationservice.service.provider.TranslationProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
/**
 * Wraps the external translation provider and falls back to local phrase/word
 * replacement when the provider is unavailable.
 */
public class TranslationService {

    private static final String SERVICE_PROVIDER = "translation-service";
    private static final String OFFLINE_PROVIDER = "offline-fallback";
    private static final Pattern WORD_PATTERN = Pattern.compile("(?U)\\b[\\p{L}']+\\b");
    private static final Map<String, Character.UnicodeScript[]> TARGET_SCRIPTS = Map.ofEntries(
            Map.entry("hi", new Character.UnicodeScript[]{Character.UnicodeScript.DEVANAGARI}),
            Map.entry("mr", new Character.UnicodeScript[]{Character.UnicodeScript.DEVANAGARI}),
            Map.entry("gu", new Character.UnicodeScript[]{Character.UnicodeScript.GUJARATI}),
            Map.entry("bn", new Character.UnicodeScript[]{Character.UnicodeScript.BENGALI}),
            Map.entry("pa", new Character.UnicodeScript[]{Character.UnicodeScript.GURMUKHI}),
            Map.entry("kn", new Character.UnicodeScript[]{Character.UnicodeScript.KANNADA}),
            Map.entry("ml", new Character.UnicodeScript[]{Character.UnicodeScript.MALAYALAM}),
            Map.entry("ta", new Character.UnicodeScript[]{Character.UnicodeScript.TAMIL}),
            Map.entry("te", new Character.UnicodeScript[]{Character.UnicodeScript.TELUGU}),
            Map.entry("ja", new Character.UnicodeScript[]{
                    Character.UnicodeScript.HIRAGANA,
                    Character.UnicodeScript.KATAKANA,
                    Character.UnicodeScript.HAN
            })
    );
    private static final Map<String, String> LANGUAGE_ALIASES = Map.ofEntries(
            Map.entry("english", "en"),
            Map.entry("spanish", "es"),
            Map.entry("french", "fr"),
            Map.entry("german", "de"),
            Map.entry("hindi", "hi"),
            Map.entry("\u0939\u093f\u0902\u0926\u0940", "hi"),
            Map.entry("japanese", "ja"),
            Map.entry("portuguese", "pt"),
            Map.entry("italian", "it"),
            Map.entry("kannada", "kn"),
            Map.entry("malayalam", "ml"),
            Map.entry("tamil", "ta"),
            Map.entry("telugu", "te"),
            Map.entry("marathi", "mr"),
            Map.entry("gujarati", "gu"),
            Map.entry("bengali", "bn"),
            Map.entry("punjabi", "pa")
    );

    private final List<TranslationProvider> translationProviders;

    public TranslationService(List<TranslationProvider> translationProviders) {
        this.translationProviders = List.copyOf(translationProviders);
    }

    /**
     * Normalizes the incoming request, calls the external provider, and falls
     * back to local translation rules when the provider cannot respond.
     */
    public TranslationResponse translate(TranslationRequest request) {
        String originalText = request == null ? null : request.getText();
        String targetLang = request == null ? null : request.getTargetLang();
        String sourceLang = request == null ? null : request.getSourceLang();
        String effectiveSourceLang = StringUtils.hasText(sourceLang) ? sourceLang : "auto";

        if (!StringUtils.hasText(originalText) || !StringUtils.hasText(targetLang)) {
            return failure(originalText, effectiveSourceLang, targetLang,
                    "Text and target language are required");
        }

        String normalizedTargetLang = normalizeLanguageCode(targetLang);
        String normalizedSourceLang = normalizeLanguageCode(effectiveSourceLang);

        TranslationJob job = new TranslationJob(
                originalText,
                normalizedSourceLang,
                normalizedTargetLang,
                effectiveSourceLang
        );

        for (TranslationProvider provider : translationProviders) {
            Optional<TranslationProviderResult> result = provider.translate(job);
            if (result.isPresent()) {
                TranslationProviderResult providerResult = result.get();
                if (isAcceptableProviderTranslation(job, providerResult)) {
                    return success(originalText, effectiveSourceLang, normalizedTargetLang, providerResult);
                }
                log.warn("Ignoring low-quality translation from provider {} for target {}", providerResult.provider(), normalizedTargetLang);
            }
        }

        log.warn("All remote translation providers failed; using offline fallback");
        TranslationResponse fallbackResponse = fallbackTranslate(originalText, effectiveSourceLang, normalizedTargetLang);
        if (fallbackResponse != null) {
            return fallbackResponse;
        }

        log.warn("Offline fallback was rejected as low quality for source={} target={}", effectiveSourceLang, normalizedTargetLang);
        return failure(originalText, effectiveSourceLang, normalizedTargetLang,
                "Translation providers are temporarily unavailable");
    }

    private TranslationResponse failure(String originalText,
                                        String sourceLang,
                                        String targetLang,
                                        String error) {
        return TranslationResponse.builder()
                .originalText(originalText)
                .correctedText(null)
                .translatedText(null)
                .sourceLanguage(sourceLang)
                .targetLanguage(targetLang)
                .provider(SERVICE_PROVIDER)
                .success(false)
                .error(error)
                .build();
    }

    private TranslationResponse success(String originalText,
                                        String fallbackSourceLang,
                                        String fallbackTargetLang,
                                        TranslationProviderResult providerResult) {
        return success(
                originalText,
                normalizeLanguageCode(firstNonBlank(providerResult.sourceLanguage(), fallbackSourceLang)),
                normalizeLanguageCode(firstNonBlank(providerResult.targetLanguage(), fallbackTargetLang)),
                firstNonBlank(providerResult.correctedText(), originalText),
                providerResult.translatedText(),
                providerResult.provider()
        );
    }

    private TranslationResponse success(String originalText,
                                        String sourceLang,
                                        String targetLang,
                                        String correctedText,
                                        String translatedText,
                                        String provider) {
        return TranslationResponse.builder()
                .originalText(originalText)
                .correctedText(correctedText)
                .translatedText(translatedText)
                .sourceLanguage(sourceLang)
                .targetLanguage(targetLang)
                .provider(provider)
                .success(true)
                .build();
    }

    /**
     * Returns a successful response even when the external provider is down by
     * using the built-in offline phrase and word maps.
     */
    private TranslationResponse fallbackTranslate(String originalText, String sourceLang, String targetLang) {
        String translatedText = offlineTranslate(originalText, sourceLang, targetLang);
        if (!isAcceptableOfflineTranslation(originalText, sourceLang, targetLang, translatedText)) {
            return null;
        }

        return success(
                originalText,
                sourceLang,
                targetLang,
                originalText,
                translatedText,
                OFFLINE_PROVIDER
        );
    }

    private boolean isAcceptableOfflineTranslation(String originalText,
                                                   String sourceLang,
                                                   String targetLang,
                                                   String translatedText) {
        if (!StringUtils.hasText(translatedText)) {
            return false;
        }

        String normalizedTarget = normalizeLanguageCode(targetLang);
        String normalizedSource = normalizeLanguageCode(sourceLang);
        if (!Objects.equals(normalizedTarget, normalizedSource)
                && normalizeForComparison(originalText).equals(normalizeForComparison(translatedText))) {
            return false;
        }

        if (isLatinTargetLanguage(normalizedTarget)) {
            return countNonLatinLetters(translatedText) == 0;
        }

        return containsExpectedTargetScript(normalizedTarget, translatedText);
    }

    private boolean isAcceptableProviderTranslation(TranslationJob job, TranslationProviderResult result) {
        if (result == null || !StringUtils.hasText(result.translatedText())) {
            return false;
        }

        String targetLanguage = normalizeLanguageCode(firstNonBlank(result.targetLanguage(), job.targetLanguage()));
        String sourceLanguage = normalizeLanguageCode(firstNonBlank(result.sourceLanguage(), job.sourceLanguage()));
        String translatedText = result.translatedText();
        if (!Objects.equals(targetLanguage, sourceLanguage)
                && normalizeForComparison(job.originalText()).equals(normalizeForComparison(translatedText))) {
            return false;
        }

        if (isLatinTargetLanguage(targetLanguage)) {
            return countNonLatinLetters(translatedText) == 0;
        }

        return containsExpectedTargetScript(targetLanguage, translatedText);
    }

    private boolean isLatinTargetLanguage(String languageCode) {
        return switch (languageCode == null ? "" : languageCode) {
            case "en", "es", "fr", "de", "pt", "it" -> true;
            default -> false;
        };
    }

    private boolean containsExpectedTargetScript(String languageCode, String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        Character.UnicodeScript[] scripts = TARGET_SCRIPTS.get(languageCode);
        if (scripts == null || scripts.length == 0) {
            return true;
        }

        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (!Character.isLetter(codePoint)) {
                continue;
            }

            Character.UnicodeScript actualScript = Character.UnicodeScript.of(codePoint);
            for (Character.UnicodeScript expectedScript : scripts) {
                if (actualScript == expectedScript) {
                    return true;
                }
            }
        }

        return false;
    }

    private String offlineTranslate(String originalText, String sourceLang, String targetLang) {
        String normalizedTargetLang = normalizeLanguageCode(targetLang);
        if (!StringUtils.hasText(originalText)) {
            return originalText;
        }

        // If translating to English, we invert the maps of the source language (or all languages if auto)
        if ("en".equals(normalizedTargetLang)) {
            String translated = originalText;
            String normalizedSourceLang = normalizeLanguageCode(sourceLang);
            
            List<String> langsToSearch = new ArrayList<>();
            if (!"auto".equals(normalizedSourceLang) && !"en".equals(normalizedSourceLang)) {
                langsToSearch.add(normalizedSourceLang);
            } else if ("auto".equals(normalizedSourceLang)) {
                langsToSearch.addAll(OFFLINE_PHRASES.keySet());
            }

            for (String lang : langsToSearch) {
                Map<String, String> phrases = OFFLINE_PHRASES.get(lang);
                if (phrases != null) {
                    translated = replacePhrasesInverted(translated, phrases, lang);
                }
                Map<String, String> words = OFFLINE_WORDS.get(lang);
                if (words != null) {
                    translated = replaceWordsInverted(translated, words, lang);
                }
            }
            return translated == null || translated.isBlank() ? originalText : translated;
        }

        Map<String, String> phrases = OFFLINE_PHRASES.get(normalizedTargetLang);
        Map<String, String> words = OFFLINE_WORDS.get(normalizedTargetLang);
        if (phrases == null && words == null) {
            return originalText;
        }

        String translated = phrases == null ? originalText : replacePhrases(originalText, phrases);
        translated = words == null ? translated : replaceWords(translated, words);
        return translated == null || translated.isBlank() ? originalText : translated;
    }

    private String replacePhrasesInverted(String text, Map<String, String> phrases, String lang) {
        String result = text;
        List<Map.Entry<String, String>> orderedEntries = new ArrayList<>(phrases.entrySet());
        // Sort by length of the foreign phrase (value) descending
        orderedEntries.sort((left, right) -> Integer.compare(right.getValue().length(), left.getValue().length()));

        boolean useWordBoundaries = !"ja".equals(lang);

        for (Map.Entry<String, String> entry : orderedEntries) {
            String foreignPhrase = entry.getValue();
            String englishPhrase = entry.getKey();
            
            String regex = useWordBoundaries ? "(?U)(?i)\\b" + Pattern.quote(foreignPhrase) + "\\b" : "(?i)" + Pattern.quote(foreignPhrase);
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(result);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(preserveCase(matcher.group(), englishPhrase)));
            }
            matcher.appendTail(buffer);
            result = buffer.toString();
        }

        return result;
    }

    private String replaceWordsInverted(String text, Map<String, String> words, String lang) {
        String result = text;
        List<Map.Entry<String, String>> orderedEntries = new ArrayList<>(words.entrySet());
        orderedEntries.sort((left, right) -> Integer.compare(right.getValue().length(), left.getValue().length()));

        boolean useWordBoundaries = !"ja".equals(lang);

        for (Map.Entry<String, String> entry : orderedEntries) {
            String foreignWord = entry.getValue();
            String englishWord = entry.getKey();
            
            String regex = useWordBoundaries ? "(?U)(?i)\\b" + Pattern.quote(foreignWord) + "\\b" : "(?i)" + Pattern.quote(foreignWord);
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(result);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(preserveCase(matcher.group(), englishWord)));
            }
            matcher.appendTail(buffer);
            result = buffer.toString();
        }
        return result;
    }

    private String replacePhrases(String text, Map<String, String> phrases) {
        String result = text;
        List<Map.Entry<String, String>> orderedEntries = new ArrayList<>(phrases.entrySet());
        orderedEntries.sort((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()));

        for (Map.Entry<String, String> entry : orderedEntries) {
            Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(entry.getKey()) + "\\b");
            Matcher matcher = pattern.matcher(result);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(preserveCase(matcher.group(), entry.getValue())));
            }
            matcher.appendTail(buffer);
            result = buffer.toString();
        }

        return result;
    }

    private String replaceWords(String text, Map<String, String> words) {
        Matcher matcher = WORD_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String originalWord = matcher.group();
            String translatedWord = words.getOrDefault(originalWord.toLowerCase(Locale.ROOT), originalWord);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(preserveCase(originalWord, translatedWord)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String preserveCase(String source, String translated) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(translated)) {
            return translated;
        }

        if (source.equals(source.toUpperCase(Locale.ROOT))) {
            return translated.toUpperCase(Locale.ROOT);
        }

        if (Character.isUpperCase(source.charAt(0)) && source.substring(1).equals(source.substring(1).toLowerCase(Locale.ROOT))) {
            return Character.toUpperCase(translated.charAt(0)) + translated.substring(1);
        }

        return translated;
    }

    private String normalizeLanguageCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "en";
        }

        String normalized = code.trim().toLowerCase(Locale.ROOT);
        String alias = LANGUAGE_ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }

        int hyphenIndex = normalized.indexOf('-');
        if (hyphenIndex > 0) {
            String baseCode = normalized.substring(0, hyphenIndex);
            alias = LANGUAGE_ALIASES.get(baseCode);
            return alias != null ? alias : baseCode;
        }

        int underscoreIndex = normalized.indexOf('_');
        if (underscoreIndex > 0) {
            String baseCode = normalized.substring(0, underscoreIndex);
            alias = LANGUAGE_ALIASES.get(baseCode);
            return alias != null ? alias : baseCode;
        }

        return normalized;
    }

    private boolean containsNonLatinLetters(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);

            if (!Character.isLetter(codePoint)) {
                continue;
            }

            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script != Character.UnicodeScript.LATIN
                    && script != Character.UnicodeScript.COMMON
                    && script != Character.UnicodeScript.INHERITED) {
                return true;
            }
        }

        return false;
    }

    private int countLatinLetters(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }

        int count = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)
                    && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN) {
                count++;
            }
        }
        return count;
    }

    private int countNonLatinLetters(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }

        int count = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)
                    && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN) {
                count++;
            }
        }
        return count;
    }

    private String normalizeForComparison(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        return text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> translationMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }

    @SafeVarargs
    private static <K, V> Map<K, V> mergeMaps(Map<K, V>... maps) {
        Map<K, V> merged = new LinkedHashMap<>();
        for (Map<K, V> map : maps) {
            merged.putAll(map);
        }
        return Map.copyOf(merged);
    }

    private static final Map<String, Map<String, String>> OFFLINE_PHRASES = mergeMaps(
            Map.of(
            "es", translationMap(
                    "how are you", "como estas",
                    "thank you", "gracias",
                    "good morning", "buenos dias",
                    "good night", "buenas noches",
                    "see you later", "hasta luego",
                    "see you", "hasta luego",
                    "i love you", "te quiero"
            ),
            "fr", translationMap(
                    "how are you", "comment ca va",
                    "thank you", "merci",
                    "good morning", "bonjour",
                    "good night", "bonne nuit",
                    "see you later", "a bientot",
                    "see you", "a bientot",
                    "i love you", "je t'aime"
            ),
            "de", translationMap(
                    "how are you", "wie geht es dir",
                    "thank you", "danke",
                    "good morning", "guten morgen",
                    "good night", "gute nacht",
                    "see you later", "bis spaeter",
                    "see you", "bis bald",
                    "i love you", "ich liebe dich"
            ),
            "hi", translationMap(
                    "how are you", "\u0906\u092A \u0915\u0948\u0938\u0947 \u0939\u0948\u0902",
                    "thank you", "\u0927\u0928\u094D\u092F\u0935\u093E\u0926",
                    "good morning", "\u0938\u0941\u092A\u094D\u0930\u092D\u093E\u0924",
                    "good night", "\u0936\u0941\u092D \u0930\u093E\u0924\u094D\u0930\u093F",
                    "see you later", "\u092B\u093F\u0930 \u092E\u093F\u0932\u0947\u0902\u0917\u0947",
                    "see you", "\u092B\u093F\u0930 \u092E\u093F\u0932\u0947\u0902\u0917\u0947",
                    "i love you", "\u092E\u0948\u0902 \u0924\u0941\u092E\u0938\u0947 \u092A\u094D\u092F\u093E\u0930 \u0915\u0930\u0924\u093E \u0939\u0942\u0901"
            ),
            "ja", translationMap(
                    "how are you", "\u304A\u5143\u6C17\u3067\u3059\u304B",
                    "thank you", "\u3042\u308A\u304C\u3068\u3046\u3054\u3056\u3044\u307E\u3059",
                    "good morning", "\u304A\u306F\u3088\u3046\u3054\u3056\u3044\u307E\u3059",
                    "good night", "\u304A\u3084\u3059\u307F\u306A\u3055\u3044",
                    "see you later", "\u307E\u305F\u5F8C\u3067",
                    "see you", "\u307E\u305F\u306D",
                    "i love you", "\u611B\u3057\u3066\u3044\u307E\u3059"
            ),
            "pt", translationMap(
                    "how are you", "como vai voce",
                    "thank you", "obrigado",
                    "good morning", "bom dia",
                    "good night", "boa noite",
                    "see you later", "ate logo",
                    "see you", "ate logo",
                    "i love you", "eu te amo"
            ),
            "it", translationMap(
                    "how are you", "come stai",
                    "thank you", "grazie",
                    "good morning", "buongiorno",
                    "good night", "buonanotte",
                    "see you later", "a dopo",
                    "see you", "a dopo",
                    "i love you", "ti amo"
            ),
            "gu", translationMap(
                    "how are you", "\u0aa4\u0aae\u0ac7 \u0a95\u0ac7\u0aae \u0a9b\u0acb",
                    "thank you", "\u0a86\u0aad\u0abe\u0ab0",
                    "good morning", "\u0ab8\u0ac1\u0aaa\u0acd\u0ab0\u0aad\u0abe\u0aa4",
                    "good night", "\u0ab6\u0ac1\u0aad \u0ab0\u0abe\u0aa4\u0acd\u0ab0\u0abf",
                    "see you later", "\u0aab\u0ab0\u0ac0 \u0aae\u0ab3\u0ac0\u0ab6\u0ac1\u0a82",
                    "see you", "\u0aab\u0ab0\u0ac0 \u0aae\u0ab3\u0ac0\u0ab6\u0ac1\u0a82",
                    "i love you", "\u0ab9\u0ac1\u0a82 \u0aa4\u0aa8\u0ac7 \u0aaa\u0acd\u0ab0\u0ac7\u0aae \u0a95\u0ab0\u0ac1\u0a82 \u0a9b\u0ac1\u0a82"
            )
            ),
            Map.of(
                    "kn", translationMap(
                            "how are you", "ನೀವು ಹೇಗಿದ್ದೀರಾ",
                            "thank you", "ಧನ್ಯವಾದಗಳು",
                            "good morning", "ಶುಭೋದಯ",
                            "good night", "ಶುಭ ರಾತ್ರಿ",
                            "see you later", "ನಂತರ ಭೇಟಿ ಆಗೋಣ",
                            "see you", "ಮತ್ತೆ ಸಿಗೋಣ",
                            "i love you", "ನಾನು ನಿನ್ನನ್ನು ಪ್ರೀತಿಸುತ್ತೇನೆ"
                    ),
                    "ml", translationMap(
                            "how are you", "നിങ്ങൾക്ക് സുഖമാണോ",
                            "thank you", "നന്ദി",
                            "good morning", "സുപ്രഭാതം",
                            "good night", "ശുഭ രാത്രി",
                            "see you later", "പിന്നീട് കാണാം",
                            "see you", "വീണ്ടും കാണാം",
                            "i love you", "ഞാൻ നിന്നെ സ്നേഹിക്കുന്നു"
                    ),
                    "ta", translationMap(
                            "how are you", "நீங்கள் எப்படி இருக்கிறீர்கள்",
                            "thank you", "நன்றி",
                            "good morning", "காலை வணக்கம்",
                            "good night", "இனிய இரவு",
                            "see you later", "பிறகு சந்திப்போம்",
                            "see you", "மீண்டும் சந்திப்போம்",
                            "i love you", "நான் உன்னை காதலிக்கிறேன்"
                    ),
                    "te", translationMap(
                            "how are you", "మీరు ఎలా ఉన్నారు",
                            "thank you", "ధన్యవాదాలు",
                            "good morning", "శుభోదయం",
                            "good night", "శుభ రాత్రి",
                            "see you later", "తర్వాత కలుద్దాం",
                            "see you", "మళ్లీ కలుద్దాం",
                            "i love you", "నేను నిన్ను ప్రేమిస్తున్నాను"
                    ),
                    "mr", translationMap(
                            "how are you", "तुम्ही कसे आहात",
                            "thank you", "धन्यवाद",
                            "good morning", "शुभ प्रभात",
                            "good night", "शुभ रात्री",
                            "see you later", "नंतर भेटू",
                            "see you", "पुन्हा भेटू",
                            "i love you", "मी तुझ्यावर प्रेम करतो"
                    ),
                    "bn", translationMap(
                            "how are you", "আপনি কেমন আছেন",
                            "thank you", "ধন্যবাদ",
                            "good morning", "সুপ্রভাত",
                            "good night", "শুভ রাত্রি",
                            "see you later", "পরে দেখা হবে",
                            "see you", "আবার দেখা হবে",
                            "i love you", "আমি তোমাকে ভালোবাসি"
                    ),
                    "pa", translationMap(
                            "how are you", "ਤੁਸੀਂ ਕਿਵੇਂ ਹੋ",
                            "thank you", "ਧੰਨਵਾਦ",
                            "good morning", "ਸ਼ੁਭ ਸਵੇਰ",
                            "good night", "ਸ਼ੁਭ ਰਾਤ",
                            "see you later", "ਫਿਰ ਮਿਲਾਂਗੇ",
                            "see you", "ਫਿਰ ਮਿਲਦੇ ਹਾਂ",
                            "i love you", "ਮੈਂ ਤੈਨੂੰ ਪਿਆਰ ਕਰਦਾ ਹਾਂ"
                    )
            )
    );

    private static final Map<String, Map<String, String>> OFFLINE_WORDS = mergeMaps(
            Map.of(
            "es", translationMap(
                    "hello", "hola",
                    "hi", "hola",
                    "hey", "oye",
                    "bye", "adios",
                    "goodbye", "adios",
                    "thanks", "gracias",
                    "please", "por favor",
                    "sorry", "lo siento",
                    "yes", "si",
                    "no", "no",
                    "good", "bueno",
                    "bad", "malo",
                    "friend", "amigo",
                    "message", "mensaje",
                    "chat", "chat",
                    "translate", "traducir",
                    "translation", "traduccion",
                    "send", "enviar",
                    "love", "amor",
                    "help", "ayuda",
                    "need", "necesito",
                    "want", "quiero",
                    "today", "hoy",
                    "tomorrow", "manana",
                    "yesterday", "ayer",
                    "what", "que",
                    "where", "donde",
                    "who", "quien",
                    "why", "por que",
                    "you", "tu",
                    "i", "yo",
                    "we", "nosotros",
                    "they", "ellos"
            ),
            "fr", translationMap(
                    "hello", "bonjour",
                    "hi", "salut",
                    "hey", "salut",
                    "bye", "au revoir",
                    "goodbye", "au revoir",
                    "thanks", "merci",
                    "please", "s'il vous plait",
                    "sorry", "desole",
                    "yes", "oui",
                    "no", "non",
                    "good", "bon",
                    "bad", "mauvais",
                    "friend", "ami",
                    "message", "message",
                    "chat", "discussion",
                    "translate", "traduire",
                    "translation", "traduction",
                    "send", "envoyer",
                    "love", "amour",
                    "help", "aide",
                    "need", "besoin",
                    "want", "veux",
                    "today", "aujourd'hui",
                    "tomorrow", "demain",
                    "yesterday", "hier",
                    "what", "quoi",
                    "where", "ou",
                    "who", "qui",
                    "why", "pourquoi",
                    "you", "tu",
                    "i", "je",
                    "we", "nous",
                    "they", "ils"
            ),
            "de", translationMap(
                    "hello", "hallo",
                    "hi", "hallo",
                    "hey", "hey",
                    "bye", "tschuss",
                    "goodbye", "tschuss",
                    "thanks", "danke",
                    "please", "bitte",
                    "sorry", "entschuldigung",
                    "yes", "ja",
                    "no", "nein",
                    "good", "gut",
                    "bad", "schlecht",
                    "friend", "freund",
                    "message", "nachricht",
                    "chat", "chat",
                    "translate", "ubersetzen",
                    "translation", "ubersetzung",
                    "send", "senden",
                    "love", "liebe",
                    "help", "hilfe",
                    "need", "brauche",
                    "want", "will",
                    "today", "heute",
                    "tomorrow", "morgen",
                    "yesterday", "gestern",
                    "what", "was",
                    "where", "wo",
                    "who", "wer",
                    "why", "warum",
                    "you", "du",
                    "i", "ich",
                    "we", "wir",
                    "they", "sie"
            ),
            "hi", translationMap(
                    "hello", "\u0928\u092E\u0938\u094D\u0924\u0947",
                    "hi", "\u0928\u092E\u0938\u094D\u0924\u0947",
                    "hey", "\u0928\u092E\u0938\u094D\u0924\u0947",
                    "bye", "\u0905\u0932\u0935\u093F\u0926\u093E",
                    "goodbye", "\u0905\u0932\u0935\u093F\u0926\u093E",
                    "thanks", "\u0927\u0928\u094D\u092F\u0935\u093E\u0926",
                    "please", "\u0915\u0943\u092A\u092F\u093E",
                    "sorry", "\u092E\u093E\u092B \u0915\u0930\u0947\u0902",
                    "yes", "\u0939\u093E\u0902",
                    "no", "\u0928\u0939\u0940\u0902",
                    "good", "\u0905\u091A\u094D\u091B\u093E",
                    "bad", "\u092C\u0941\u0930\u093E",
                    "friend", "\u0926\u094B\u0938\u094D\u0924",
                    "message", "\u0938\u0902\u0926\u0947\u0936",
                    "chat", "\u092C\u093E\u0924\u091A\u0940\u0924",
                    "translate", "\u0905\u0928\u0941\u0935\u093E\u0926",
                    "translation", "\u0905\u0928\u0941\u0935\u093E\u0926",
                    "send", "\u092D\u0947\u091C\u0947\u0902",
                    "love", "\u092A\u094D\u092F\u093E\u0930",
                    "help", "\u092E\u0926\u0926",
                    "need", "\u091A\u093E\u0939\u093F\u090F",
                    "want", "\u091A\u093E\u0939\u0924\u0947 \u0939\u0948\u0902",
                    "today", "\u0906\u091C",
                    "tomorrow", "\u0915\u0932",
                    "yesterday", "\u0915\u0932",
                    "what", "\u0915\u094D\u092F\u093E",
                    "where", "\u0915\u0939\u093E\u0901",
                    "who", "\u0915\u094C\u0928",
                    "why", "\u0915\u094D\u092F\u094B\u0902",
                    "you", "\u0906\u092A",
                    "i", "\u092E\u0948\u0902",
                    "we", "\u0939\u092E",
                    "they", "\u0935\u0947"
            ),
            "ja", translationMap(
                    "hello", "\u3053\u3093\u306B\u3061\u306F",
                    "hi", "\u3053\u3093\u306B\u3061\u306F",
                    "hey", "\u3084\u3042",
                    "bye", "\u3055\u3088\u3046\u306A\u3089",
                    "goodbye", "\u3055\u3088\u3046\u306A\u3089",
                    "thanks", "\u3042\u308A\u304C\u3068\u3046",
                    "please", "\u304A\u9858\u3044\u3057\u307E\u3059",
                    "sorry", "\u3054\u3081\u3093\u306A\u3055\u3044",
                    "yes", "\u306F\u3044",
                    "no", "\u3044\u3044\u3048",
                    "good", "\u826F\u3044",
                    "bad", "\u60AA\u3044",
                    "friend", "\u53CB\u9054",
                    "message", "\u30E1\u30C3\u30BB\u30FC\u30B8",
                    "chat", "\u30C1\u30E3\u30C3\u30C8",
                    "translate", "\u7FFB\u8A33",
                    "translation", "\u7FFB\u8A33",
                    "send", "\u9001\u4FE1",
                    "love", "\u611B",
                    "help", "\u52A9\u3051",
                    "need", "\u5FC5\u8981",
                    "want", "\u6B32\u3057\u3044",
                    "today", "\u4ECA\u65E5",
                    "tomorrow", "\u660E\u65E5",
                    "yesterday", "\u6628\u65E5",
                    "what", "\u4F55",
                    "where", "\u3069\u3053",
                    "who", "\u8AB0",
                    "why", "\u306A\u305C",
                    "you", "\u3042\u306A\u305F",
                    "i", "\u79C1",
                    "we", "\u79C1\u305F\u3061",
                    "they", "\u5F7C\u3089"
            ),
            "pt", translationMap(
                    "hello", "ola",
                    "hi", "ola",
                    "hey", "oi",
                    "bye", "tchau",
                    "goodbye", "tchau",
                    "thanks", "obrigado",
                    "please", "por favor",
                    "sorry", "desculpe",
                    "yes", "sim",
                    "no", "nao",
                    "good", "bom",
                    "bad", "ruim",
                    "friend", "amigo",
                    "message", "mensagem",
                    "chat", "bate-papo",
                    "translate", "traduzir",
                    "translation", "traducao",
                    "send", "enviar",
                    "love", "amor",
                    "help", "ajuda",
                    "need", "preciso",
                    "want", "quero",
                    "today", "hoje",
                    "tomorrow", "amanha",
                    "yesterday", "ontem",
                    "what", "o que",
                    "where", "onde",
                    "who", "quem",
                    "why", "porque",
                    "you", "voce",
                    "i", "eu",
                    "we", "nos",
                    "they", "eles"
            ),
            "it", translationMap(
                    "hello", "ciao",
                    "hi", "ciao",
                    "hey", "ehi",
                    "bye", "arrivederci",
                    "goodbye", "arrivederci",
                    "thanks", "grazie",
                    "please", "per favore",
                    "sorry", "scusa",
                    "yes", "si",
                    "no", "no",
                    "good", "buono",
                    "bad", "cattivo",
                    "friend", "amico",
                    "message", "messaggio",
                    "chat", "chat",
                    "translate", "tradurre",
                    "translation", "traduzione",
                    "send", "inviare",
                    "love", "amore",
                    "help", "aiuto",
                    "need", "ho bisogno",
                    "want", "voglio",
                    "today", "oggi",
                    "tomorrow", "domani",
                    "yesterday", "ieri",
                    "what", "cosa",
                    "where", "dove",
                    "who", "chi",
                    "why", "perche",
                    "you", "tu",
                    "i", "io",
                    "we", "noi",
                    "they", "loro"
            ),
            "gu", translationMap(
                    "hello", "\u0ab9\u0ac7\u0ab2\u0acb",
                    "hi", "\u0ab9\u0ac7\u0ab2\u0acb",
                    "こんにちは", "\u0ab9\u0ac7\u0ab2\u0acb",
                    "hey", "\u0ab9\u0ac7\u0ab2\u0acb",
                    "bye", "\u0a86\u0ab5\u0a9c\u0acb",
                    "goodbye", "\u0a86\u0ab5\u0a9c\u0acb",
                    "thanks", "\u0a86\u0aad\u0abe\u0ab0",
                    "please", "\u0a95\u0ac3\u0aaa\u0abe",
                    "sorry", "\u0aae\u0abe\u0aab \u0a95\u0ab0\u0acb",
                    "yes", "\u0ab9\u0abe",
                    "no", "\u0aa8\u0abe",
                    "good", "\u0ab8\u0abe\u0ab0\u0ac1\u0a82",
                    "bad", "\u0a96\u0ab0\u0abe\u0aac",
                    "friend", "\u0aae\u0abf\u0aa4\u0acd\u0ab0",
                    "message", "\u0ab8\u0a82\u0aa6\u0ac7\u0ab6",
                    "chat", "\u0ab5\u0abe\u0aa4\u0a9a\u0ac0\u0aa4",
                    "translate", "\u0aad\u0abe\u0ab7\u0abe\u0a82\u0aa4\u0ab0",
                    "translation", "\u0aad\u0abe\u0ab7\u0abe\u0a82\u0aa4\u0ab0",
                    "send", "\u0aae\u0acb\u0a95\u0ab2\u0acb",
                    "love", "\u0aaa\u0acd\u0ab0\u0ac7\u0aae",
                    "help", "\u0aae\u0aa6\u0aa6",
                    "need", "\u0a9c\u0ab0\u0ac2\u0ab0",
                    "want", "\u0a88\u0a9a\u0acd\u0a9b\u0ac1\u0a82 \u0a9b\u0ac1\u0a82",
                    "today", "\u0a86\u0a9c\u0ac7",
                    "tomorrow", "\u0a86\u0ab5\u0aa4\u0ac0\u0a95\u0abe\u0ab2\u0ac7",
                    "yesterday", "\u0a97\u0a88\u0a95\u0abe\u0ab2\u0ac7",
                    "what", "\u0ab6\u0ac1\u0a82",
                    "where", "\u0a95\u0acd\u0aaf\u0abe\u0a82",
                    "who", "\u0a95\u0acb\u0aa3",
                    "why", "\u0ab6\u0abe \u0aae\u0abe\u0a9f\u0ac7",
                    "you", "\u0aa4\u0aae\u0ac7",
                    "i", "\u0ab9\u0ac1\u0a82",
                    "we", "\u0a85\u0aae\u0ac7",
                    "they", "\u0aa4\u0ac7\u0a93"
            )
            ),
            Map.of(
                    "kn", translationMap(
                            "hello", "ನಮಸ್ಕಾರ",
                            "hi", "ನಮಸ್ಕಾರ",
                            "hey", "ನಮಸ್ಕಾರ",
                            "bye", "ವಿದಾಯ",
                            "goodbye", "ವಿದಾಯ",
                            "thanks", "ಧನ್ಯವಾದಗಳು",
                            "please", "ದಯವಿಟ್ಟು",
                            "sorry", "ಕ್ಷಮಿಸಿ",
                            "yes", "ಹೌದು",
                            "no", "ಇಲ್ಲ",
                            "good", "ಒಳ್ಳೆಯದು",
                            "bad", "ಕೆಟ್ಟದು",
                            "friend", "ಸ್ನೇಹಿತ",
                            "message", "ಸಂದೇಶ",
                            "chat", "ಚಾಟ್",
                            "translate", "ಅನುವಾದ",
                            "translation", "ಅನುವಾದ",
                            "send", "ಕಳುಹಿಸಿ",
                            "love", "ಪ್ರೀತಿ",
                            "help", "ಸಹಾಯ",
                            "need", "ಬೇಕು",
                            "want", "ಬಯಸುತ್ತೇನೆ",
                            "today", "ಇಂದು",
                            "tomorrow", "ನಾಳೆ",
                            "yesterday", "ನಿನ್ನೆ",
                            "what", "ಏನು",
                            "where", "ಎಲ್ಲಿ",
                            "who", "ಯಾರು",
                            "why", "ಏಕೆ",
                            "you", "ನೀವು",
                            "i", "ನಾನು",
                            "we", "ನಾವು",
                            "they", "ಅವರು"
                    ),
                    "ml", translationMap(
                            "hello", "നമസ്കാരം",
                            "hi", "നമസ്കാരം",
                            "hey", "ഹലോ",
                            "bye", "വിട",
                            "goodbye", "വിട",
                            "thanks", "നന്ദി",
                            "please", "ദയവായി",
                            "sorry", "ക്ഷമിക്കണം",
                            "yes", "അതെ",
                            "no", "ഇല്ല",
                            "good", "നല്ലത്",
                            "bad", "മോശം",
                            "friend", "സുഹൃത്ത്",
                            "message", "സന്ദേശം",
                            "chat", "ചാറ്റ്",
                            "translate", "പരിഭാഷ",
                            "translation", "പരിഭാഷ",
                            "send", "അയയ്ക്കുക",
                            "love", "സ്നേഹം",
                            "help", "സഹായം",
                            "need", "ആവശ്യം",
                            "want", "ആഗ്രഹിക്കുന്നു",
                            "today", "ഇന്ന്",
                            "tomorrow", "നാളെ",
                            "yesterday", "ഇന്നലെ",
                            "what", "എന്ത്",
                            "where", "എവിടെ",
                            "who", "ആർ",
                            "why", "എന്തുകൊണ്ട്",
                            "you", "നിങ്ങൾ",
                            "i", "ഞാൻ",
                            "we", "ഞങ്ങൾ",
                            "they", "അവർ"
                    ),
                    "ta", translationMap(
                            "hello", "வணக்கம்",
                            "hi", "வணக்கம்",
                            "hey", "வணக்கம்",
                            "bye", "பிரியாவிடை",
                            "goodbye", "பிரியாவிடை",
                            "thanks", "நன்றி",
                            "please", "தயவுசெய்து",
                            "sorry", "மன்னிக்கவும்",
                            "yes", "ஆம்",
                            "no", "இல்லை",
                            "good", "நல்லது",
                            "bad", "மோசம்",
                            "friend", "நண்பர்",
                            "message", "செய்தி",
                            "chat", "அரட்டை",
                            "translate", "மொழிபெயர்க்க",
                            "translation", "மொழிபெயர்ப்பு",
                            "send", "அனுப்பு",
                            "love", "அன்பு",
                            "help", "உதவி",
                            "need", "வேண்டும்",
                            "want", "விரும்புகிறேன்",
                            "today", "இன்று",
                            "tomorrow", "நாளை",
                            "yesterday", "நேற்று",
                            "what", "என்ன",
                            "where", "எங்கே",
                            "who", "யார்",
                            "why", "ஏன்",
                            "you", "நீங்கள்",
                            "i", "நான்",
                            "we", "நாம்",
                            "they", "அவர்கள்"
                    ),
                    "te", translationMap(
                            "hello", "నమస్కారం",
                            "hi", "నమస్కారం",
                            "hey", "నమస్కారం",
                            "bye", "వీడ్కోలు",
                            "goodbye", "వీడ్కోలు",
                            "thanks", "ధన్యవాదాలు",
                            "please", "దయచేసి",
                            "sorry", "క్షమించండి",
                            "yes", "అవును",
                            "no", "కాదు",
                            "good", "మంచి",
                            "bad", "చెడు",
                            "friend", "స్నేహితుడు",
                            "message", "సందేశం",
                            "chat", "చాట్",
                            "translate", "అనువదించు",
                            "translation", "అనువాదం",
                            "send", "పంపండి",
                            "love", "ప్రేమ",
                            "help", "సహాయం",
                            "need", "అవసరం",
                            "want", "కావాలి",
                            "today", "ఈ రోజు",
                            "tomorrow", "రేపు",
                            "yesterday", "నిన్న",
                            "what", "ఏమి",
                            "where", "ఎక్కడ",
                            "who", "ఎవరు",
                            "why", "ఎందుకు",
                            "you", "మీరు",
                            "i", "నేను",
                            "we", "మేము",
                            "they", "వారు"
                    ),
                    "mr", translationMap(
                            "hello", "नमस्कार",
                            "hi", "नमस्कार",
                            "hey", "नमस्कार",
                            "bye", "निरोप",
                            "goodbye", "निरोप",
                            "thanks", "धन्यवाद",
                            "please", "कृपया",
                            "sorry", "माफ करा",
                            "yes", "हो",
                            "no", "नाही",
                            "good", "चांगले",
                            "bad", "वाईट",
                            "friend", "मित्र",
                            "message", "संदेश",
                            "chat", "गप्पा",
                            "translate", "भाषांतर करा",
                            "translation", "भाषांतर",
                            "send", "पाठवा",
                            "love", "प्रेम",
                            "help", "मदत",
                            "need", "गरज",
                            "want", "पाहिजे",
                            "today", "आज",
                            "tomorrow", "उद्या",
                            "yesterday", "काल",
                            "what", "काय",
                            "where", "कुठे",
                            "who", "कोण",
                            "why", "का",
                            "you", "तुम्ही",
                            "i", "मी",
                            "we", "आम्ही",
                            "they", "ते"
                    ),
                    "bn", translationMap(
                            "hello", "নমস্কার",
                            "hi", "নমস্কার",
                            "hey", "হ্যালো",
                            "bye", "বিদায়",
                            "goodbye", "বিদায়",
                            "thanks", "ধন্যবাদ",
                            "please", "অনুগ্রহ করে",
                            "sorry", "দুঃখিত",
                            "yes", "হ্যাঁ",
                            "no", "না",
                            "good", "ভালো",
                            "bad", "খারাপ",
                            "friend", "বন্ধু",
                            "message", "বার্তা",
                            "chat", "চ্যাট",
                            "translate", "অনুবাদ",
                            "translation", "অনুবাদ",
                            "send", "পাঠান",
                            "love", "ভালোবাসা",
                            "help", "সাহায্য",
                            "need", "প্রয়োজন",
                            "want", "চাই",
                            "today", "আজ",
                            "tomorrow", "আগামীকাল",
                            "yesterday", "গতকাল",
                            "what", "কি",
                            "where", "কোথায়",
                            "who", "কে",
                            "why", "কেন",
                            "you", "আপনি",
                            "i", "আমি",
                            "we", "আমরা",
                            "they", "তারা"
                    ),
                    "pa", translationMap(
                            "hello", "ਸਤ ਸ੍ਰੀ ਅਕਾਲ",
                            "hi", "ਸਤ ਸ੍ਰੀ ਅਕਾਲ",
                            "hey", "ਸਤ ਸ੍ਰੀ ਅਕਾਲ",
                            "bye", "ਅਲਵਿਦਾ",
                            "goodbye", "ਅਲਵਿਦਾ",
                            "thanks", "ਧੰਨਵਾਦ",
                            "please", "ਕਿਰਪਾ ਕਰਕੇ",
                            "sorry", "ਮਾਫ਼ ਕਰਨਾ",
                            "yes", "ਹਾਂ",
                            "no", "ਨਹੀਂ",
                            "good", "ਚੰਗਾ",
                            "bad", "ਮਾੜਾ",
                            "friend", "ਦੋਸਤ",
                            "message", "ਸੁਨੇਹਾ",
                            "chat", "ਚੈਟ",
                            "translate", "ਅਨੁਵਾਦ ਕਰੋ",
                            "translation", "ਅਨੁਵਾਦ",
                            "send", "ਭੇਜੋ",
                            "love", "ਪਿਆਰ",
                            "help", "ਮਦਦ",
                            "need", "ਲੋੜ",
                            "want", "ਚਾਹੁੰਦਾ ਹਾਂ",
                            "today", "ਅੱਜ",
                            "tomorrow", "ਕੱਲ੍ਹ",
                            "yesterday", "ਕੱਲ੍ਹ",
                            "what", "ਕੀ",
                            "where", "ਕਿੱਥੇ",
                            "who", "ਕੌਣ",
                            "why", "ਕਿਉਂ",
                            "you", "ਤੁਸੀਂ",
                            "i", "ਮੈਂ",
                            "we", "ਅਸੀਂ",
                            "they", "ਉਹ"
                    )
            )
    );

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
