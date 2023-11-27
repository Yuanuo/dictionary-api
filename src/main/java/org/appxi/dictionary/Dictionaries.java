package org.appxi.dictionary;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class Dictionaries {
    private static final Map<File, Dictionary> cachedFiles = new HashMap<>();

    public static final Dictionaries def = new Dictionaries();

    private final List<Dictionary> list = new ArrayList<>();

    public void add(Path... paths) {
        for (Path path : paths) {
            try {
                if (Files.notExists(path)) {
                    continue;
                }
                if (Files.isDirectory(path)) {
                    Files.walkFileTree(path, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            add(file.toFile());
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    add(path.toFile());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void add(File file) {
        final String fileName = file.getName();

        if (!file.isFile() || !file.exists() || !file.canRead()
            || !fileName.toLowerCase().endsWith(Dictionary.FILE_SUFFIX)) {
            return;
        }

        final String name = fileName.substring(0, fileName.lastIndexOf('.'));
        final String id = name.hashCode() + "_" + file.length();
        final Dictionary dictionary = cachedFiles.computeIfAbsent(file, f -> new Dictionary(id, name, f));

        if (!list.contains(dictionary)) {
            list.add(dictionary);
        }
    }

    public void remove(Path... paths) {
        for (Path path : paths) {
            list.removeIf(d -> d.file.toPath().startsWith(path));
        }
    }

    public void clear() {
        this.list.clear();
    }

    public int size() {
        return this.list.size();
    }

    public long sizeEntries() {
        return this.list.stream().mapToLong(Dictionary::size).sum();
    }

    public List<Dictionary> list() {
        return Collections.unmodifiableList(this.list);
    }

    public Dictionaries filtered(Predicate<Dictionary> predicate) {
        final Dictionaries result = new Dictionaries();
        result.list.addAll(null == predicate ? this.list : this.list.stream().filter(predicate).toList());
        return result;
    }

    public Dictionaries shuffled() {
        final Dictionaries result = new Dictionaries();
        result.list.addAll(this.list);
        Collections.shuffle(result.list);
        return result;
    }

    public static Dictionary find(String idOrName) {
        for (Dictionary dictionary : cachedFiles.values()) {
            if (Objects.equals(idOrName, dictionary.id) || Objects.equals(idOrName, dictionary.name)) {
                return dictionary;
            }
        }
        return null;
    }

    /**
     * 直接查词无过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords 关键词
     * @return 结果列表
     */
    public Iterator<Dictionary.Entry> search(String keywords) {
        return search(keywords, (Predicate<Dictionary.Entry>) null);
    }

    /**
     * 直接查词无条目过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords  关键词
     * @param matchType 匹配模式
     * @return 结果列表
     */
    public Iterator<Dictionary.Entry> search(String keywords, MatchType matchType) {
        return search(keywords, matchType, null);
    }

    /**
     * 直接查词且支持条目过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords       关键词
     * @param entryPredicate 条目过滤器
     * @return 结果列表
     */
    public Iterator<Dictionary.Entry> search(String keywords,
                                             Predicate<Dictionary.Entry> entryPredicate) {
        final StringBuilder buf = new StringBuilder(null == keywords ? "" : keywords);
        final MatchType matchType = MatchType.detect(buf);
        return search(buf.toString(), matchType, entryPredicate);
    }

    /**
     * 按指定匹配模式查词，不会从所查词中检测匹配模式
     *
     * @param keywords       关键词
     * @param matchType      匹配模式
     * @param entryPredicate 条目过滤器
     * @return 结果列表
     */
    public Iterator<Dictionary.Entry> search(String keywords,
                                             MatchType matchType,
                                             Predicate<Dictionary.Entry> entryPredicate) {
        if (list.size() < 40 || keywords.isEmpty() || keywords.replaceAll("[*\"' ]+", "").isEmpty()) {
            return new Searcher(list, keywords, matchType, entryPredicate);
        }

        return list.parallelStream()
                .flatMap(dictionary -> {
                    final List<Dictionary.Entry> result = new ArrayList<>();
                    final DictionarySearcher searcher = new DictionarySearcher(dictionary, keywords, matchType, entryPredicate);
                    while (searcher.hasNext()) {
                        result.add(searcher.next());
                    }
                    return result.stream();
                })
                .toList().iterator();
    }

    public static final class Searcher implements Iterator<Dictionary.Entry> {
        private final Iterator<Dictionary> dictionaryIterator;

        private final String keywords;
        private final MatchType matchType;
        private final Predicate<Dictionary.Entry> entryPredicate;
        private Iterator<Dictionary.Entry> searcher;
        private Dictionary.Entry nextEntry;

        public Searcher(Collection<Dictionary> dictionaries,
                        String keywords,
                        MatchType matchType,
                        Predicate<Dictionary.Entry> entryPredicate) {
            this.dictionaryIterator = dictionaries.iterator();

            this.keywords = keywords;
            this.matchType = matchType;
            this.entryPredicate = entryPredicate;
        }

        @Override
        public boolean hasNext() {
            if (null == this.searcher) {
                if (!this.dictionaryIterator.hasNext()) {
                    return false;
                }
                this.searcher = this.dictionaryIterator.next().search(keywords, matchType, entryPredicate);
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
}
