package com.norbertfila.i18n;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.items.ItemId;
import com.norbertfila.ui.BitmapFont;
import com.norbertfila.ui.screens.InventoryScreen;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class TranslationsTest {
    @Test
    void switchesLanguageAndFormatsValuesUsingTheSelectedLocale() {
        var translations = new Translations(Language.ENGLISH);
        assertEquals("Options", translations.text("action.options"));
        assertEquals("Sensitivity: 0.11", translations.text("settings.sensitivity", .11f));
        translations.setLanguage(Language.POLISH);
        assertEquals("Opcje", translations.text("action.options"));
        assertEquals("Czułość: 0,11", translations.text("settings.sensitivity", .11f));
        assertEquals("Kreatywny - slot 9", translations.text("inventory.creative", 9));
        translations.setLanguage(Language.ENGLISH);
        assertEquals("Options", translations.text("action.options"));
        assertThrows(NullPointerException.class, () -> translations.setLanguage(null));
        assertEquals(Language.ENGLISH, translations.language());
        assertThrows(MissingResourceException.class, () -> translations.text("missing.key"));
    }

    @Test
    void catalogsHaveTheSameKeysCompatibleFormatsAndRenderableCharacters() throws Exception {
        Properties english = catalog("messages.properties");
        Properties polish = catalog("messages_pl.properties");
        assertEquals(english.stringPropertyNames(), polish.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            String reference = english.getProperty(key);
            String translated = polish.getProperty(key);
            assertFalse(translated.isBlank(), key);
            assertEquals(formatSpecifiers(reference), formatSpecifiers(translated), key);
            for (String text : new String[] {reference, translated})
                text.codePoints()
                        .forEach(cp -> assertTrue(
                                cp == '\n' || cp == '?' || BitmapFont.DEFAULT.glyph(cp) != '?', key + ": " + text));
        }
        for (Language language : Language.values()) {
            var translations = new Translations(language);
            for (byte block : InventoryScreen.catalogBlocks()) {
                String key = ItemId.translationKey(block);
                assertFalse(translations.text(key).isBlank(), key);
            }
        }
    }

    @Test
    void persistedLanguageCodesAreStableAndUnknownCodesUseEnglish() {
        for (Language language : Language.values()) assertEquals(language, Language.fromCode(language.code()));
        assertEquals(Language.ENGLISH, Language.fromCode("unknown"));
        assertEquals(Language.ENGLISH, Language.fromCode(null));
    }

    @Test
    void englishDoesNotFallBackToTheOperatingSystemLanguage() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("pl"));
            assertEquals("Back", new Translations(Language.ENGLISH).text("action.back"));
        } finally {
            Locale.setDefault(original);
        }
    }

    private static java.util.List<String> formatSpecifiers(String text) {
        return java.util.regex.Pattern.compile("%[.\\d]*[a-z]")
                .matcher(text)
                .results()
                .map(java.util.regex.MatchResult::group)
                .toList();
    }

    private static Properties catalog(String name) throws Exception {
        var properties = new Properties();
        try (var stream = TranslationsTest.class.getResourceAsStream("/i18n/" + name)) {
            assertNotNull(stream);
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
