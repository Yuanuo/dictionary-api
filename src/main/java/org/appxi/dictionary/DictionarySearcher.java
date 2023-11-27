package org.appxi.dictionary;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Predicate;

public final class DictionarySearcher implements Iterator<Dictionary.Entry> {
    private final Dictionary dictionary;
    private final MatchType matchType;
    private final byte[] keywordsBytes;
    private final int keywordsBytesLength;
    private final Predicate<Dictionary.Entry> entryPredicate;
    private int index = -1, size = -1;
    private int nextEntryId = 0;
    private Dictionary.Entry nextEntry;

    public DictionarySearcher(Dictionary dictionary, String keywords,
                              MatchType matchType) {
        this(dictionary, keywords, matchType, null);
    }

    public DictionarySearcher(Dictionary dictionary, String keywords,
                              MatchType matchType,
                              Predicate<Dictionary.Entry> entryPredicate) {
        this.dictionary = dictionary;
        this.matchType = matchType.name().startsWith("Title") ? matchType : MatchType.TitleStartsWith;
        this.keywordsBytes = (null == keywords || keywords.isBlank()) ? null : keywords.toLowerCase().getBytes(dictionary.charset());
        this.keywordsBytesLength = null == this.keywordsBytes ? 0 : this.keywordsBytes.length;
        this.entryPredicate = entryPredicate;
    }

    @Override
    public boolean hasNext() {
        if (this.size == -1) {
            this.size = this.dictionary.size();
        }
        // find next entry
        // reset
        this.nextEntry = null;
        //
        while (++this.index < this.size) {
            final EntryEx entry = new EntryEx(this.dictionary).setScore(0);
            entry.id = this.nextEntryId;
            this.nextEntryId += this.dictionary.readEntry(entry);
            // 忽略非实体词条
            if (entry.type == Dictionary.Entry.TYPE_CATEGORY)
                continue;

            final byte[] titleBytes = (byte[]) entry.title;
            final int titleBytesLength = titleBytes.length;

            // 当前词条的字符长度小于指定的keywords长度时，直接认为不符合条件
            // 例如，搜索5个字符，但当前词条仅1-4个字符，此时无法匹配
            if (titleBytesLength < keywordsBytesLength) {
                continue;
            }
            // 允许自定义的词条过滤验证
            if (null != this.entryPredicate && !this.entryPredicate.test(entry))
                continue;

            // 未指定keywords时不做后续验证
            if (keywordsBytesLength == 0) {
                this.nextEntry = entry;
                break;
            }

            // 无论哪种匹配模式，如果完全相等则直接返回
            if (titleBytesLength == keywordsBytesLength && Arrays.equals(titleBytes, keywordsBytes)) {
                this.nextEntry = entry.setScore(100);
                break;
            }
            if (this.matchType == MatchType.TitleStartsWith) {
                // 词条名称的前面部分（与搜索关键词相同长度）匹配搜索关键词的全部部分
                if (Arrays.equals(titleBytes, 0, keywordsBytesLength,
                        keywordsBytes, 0, keywordsBytesLength)) {
                    this.nextEntry = entry.setScore(10);
                    break;
                }
            } else if (this.matchType == MatchType.TitleEquals) {
                // 无需再次验证
            } else if (this.matchType == MatchType.TitleEndsWith) {
                // 词条名称的后面部分（与搜索关键词相同长度）匹配搜索关键词的全部部分
                if (Arrays.equals(titleBytes, titleBytesLength - keywordsBytesLength, titleBytesLength,
                        keywordsBytes, 0, keywordsBytesLength)) {
                    this.nextEntry = entry.setScore(10);
                    break;
                }
            } else if (this.matchType == MatchType.TitleContains) {
                for (int i = 0; i < titleBytesLength; i++) {
                    if (titleBytes[i] == keywordsBytes[0]) {
                        final int toIdx = i + keywordsBytesLength;
                        // 如果从指定位置开始的后面部分（与搜索关键词相同长度）完全匹配搜索关键词的全部部分
                        if (toIdx <= titleBytesLength && Arrays.equals(titleBytes, i, toIdx,
                                keywordsBytes, 0, keywordsBytesLength)) {
                            this.nextEntry = entry.setScore(10);
                            return true;
                        }
                    }
                }
            }
        }
        //
        return this.nextEntry != null;
    }

    @Override
    public Dictionary.Entry next() {
        return this.nextEntry;
    }


    /**
     * 具有分数属性的词条，通常仅用于搜索时的结果词条
     */
    private static final class EntryEx extends Dictionary.Entry {
        private int score;

        EntryEx(Dictionary dictionary) {
            super(dictionary);
        }

        EntryEx setScore(int score) {
            this.score = score;
            return this;
        }

        public int compareTo(Dictionary.Entry other) {
            return ((EntryEx) other).score - this.score;
        }
    }
}
