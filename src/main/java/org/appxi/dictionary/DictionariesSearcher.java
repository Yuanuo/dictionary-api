package org.appxi.dictionary;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

public final class DictionariesSearcher implements Iterator<Dictionary.Entry> {
    private final Iterator<Dictionary> dictionaryIterator;

    private final String keywords;
    private final SearchType searchType;
    private final Predicate<Dictionary.Entry> entryPredicate;
    private Iterator<Dictionary.Entry> searcher;
    private Dictionary.Entry nextEntry;

    public DictionariesSearcher(Collection<Dictionary> dictionaries,
                                String keywords,
                                SearchType searchType,
                                Predicate<Dictionary.Entry> entryPredicate) {
        this.dictionaryIterator = dictionaries.iterator();

        this.keywords = keywords;
        this.searchType = searchType;
        this.entryPredicate = entryPredicate;
    }

    @Override
    public boolean hasNext() {
        if (null == this.searcher) {
            if (!this.dictionaryIterator.hasNext()) {
                return false;
            }
            this.searcher = this.dictionaryIterator.next().search(keywords, searchType, entryPredicate);
        }

        if (!this.searcher.hasNext()) {
            this.searcher = null;
            return hasNext();
        }
        this.nextEntry = this.searcher.next();
        return true;
    }

    @Override
    public Dictionary.Entry next() {
        return this.nextEntry;
    }
}
