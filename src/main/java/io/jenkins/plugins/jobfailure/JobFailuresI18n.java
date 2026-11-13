package io.jenkins.plugins.jobfailure;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/** Resolves plugin messages in the active Jenkins locale, with English as the fallback. */
public final class JobFailuresI18n {
    private static final String BUNDLE = "io.jenkins.plugins.jobfailure.Messages";

    private JobFailuresI18n() {}

    public static String text(String key, Object... arguments) {
        return text(currentLocale(), key, arguments);
    }

    public static String text(Locale locale, String key, Object... arguments) {
        Locale supported = supportedLocale(locale);
        ResourceBundle messages = ResourceBundle.getBundle(BUNDLE, supported, JobFailuresI18n.class.getClassLoader());
        return new MessageFormat(messages.getString(key), supported).format(arguments);
    }

    public static Locale currentLocale() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        return request == null ? Locale.getDefault() : request.getLocale();
    }

    public static Locale supportedLocale(Locale locale) {
        if (locale != null && "tr".equalsIgnoreCase(locale.getLanguage())) {
            return Locale.forLanguageTag("tr");
        }
        return Locale.ENGLISH;
    }
}
