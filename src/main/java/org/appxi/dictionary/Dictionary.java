package org.appxi.dictionary;

import org.appxi.prefs.PreferencesInProperties;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public class Dictionary {
    public final Path dataDir;
    public final PreferencesInProperties conf;

    public final String id;

    private Charset charset;

    public Dictionary(Path confFile) {
        this.dataDir = confFile.getParent();
        this.conf = new PreferencesInProperties(confFile, StandardCharsets.UTF_8);

        this.id = this.conf.getString("id", "");
    }

    public Dictionary prop(String key, Object value) {
        this.conf.setProperty(key, value);
        return this;
    }

    public Charset getCharset() {
        if (null == this.charset) {
            this.charset = Charset.forName(this.conf.getString("charset", "UTF-16BE"), StandardCharsets.UTF_16BE);
        }
        return this.charset;
    }

    public void setCharset(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
        this.conf.setProperty("charset", charset.name());
    }

    public String getName() {
        return this.getI18nVal("name");
    }

    public String getI18nVal(String key) {
        return this.getI18nVal(key, Locale.getDefault());
    }

    public String getI18nVal(String key, Locale locale) {
        Object val = null;
        // 此处默认 英文 为直接使用key，而非英文则在key后使用语言代码区分
        if (null != locale && locale != Locale.ENGLISH) {
            val = conf.getProperty(key + "_" + locale.toLanguageTag());
            if (null == val) val = conf.getProperty(key + "_" + locale.getLanguage());
        }

        if (null == val) val = conf.getProperty(key);
        return null == val ? null : String.valueOf(val);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dictionary that = (Dictionary) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return this.getName();
    }
}
