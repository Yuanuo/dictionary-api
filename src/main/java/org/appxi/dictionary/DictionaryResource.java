package org.appxi.dictionary;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

class DictionaryResource implements AutoCloseable {
    private final Dictionary dictionary;
    private SevenZFile sevenZFile;

    public DictionaryResource(Dictionary dictionary) {
        this.dictionary = dictionary;
    }

    public InputStream getInputStream(String name) throws IOException {
        if (null == sevenZFile) {
            File file = this.dictionary.dataDir.resolve("dict-res.7z").toFile();
            if (!file.exists())
                return null;
            sevenZFile = new SevenZFile(file);
        }

        for (SevenZArchiveEntry entry : sevenZFile.getEntries()) {
            if (entry.getName().equalsIgnoreCase(name))
                return sevenZFile.getInputStream(entry);
        }
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void close() throws Exception {
        if (null != this.sevenZFile) {
            this.sevenZFile.close();
            this.sevenZFile = null;
        }
    }
}
