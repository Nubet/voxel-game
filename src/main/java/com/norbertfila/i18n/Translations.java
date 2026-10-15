package com.norbertfila.i18n;

import java.util.Objects;
import java.util.ResourceBundle;

public final class Translations {
    private Language language;
    private ResourceBundle messages;

    public Translations(Language language) {
        setLanguage(language);
    }

    public Language language() {
        return language;
    }

    public void setLanguage(Language language) {
        Objects.requireNonNull(language);
        ResourceBundle next = ResourceBundle.getBundle(
                "i18n.messages",
                language.locale(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        this.language = language;
        messages = next;
    }

    public String text(String key, Object... arguments) {
        String pattern = messages.getString(key);
        return arguments.length == 0 ? pattern : String.format(language.locale(), pattern, arguments);
    }
}
