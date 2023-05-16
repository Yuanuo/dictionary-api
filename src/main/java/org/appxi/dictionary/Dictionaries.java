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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class Dictionaries {
    private static final Map<File, Dictionary> REGISTERED_DICTIONARIES = new HashMap<>();
    private static final Map<String, Dictionary> MANAGED_DICTIONARIES = new LinkedHashMap<>();

    public final List<Dictionary> list;

    private Dictionaries(Collection<Dictionary> list) {
        this.list = new ArrayList<>(list);
    }

    public static void discover(Path... paths) {
        for (Path path : paths) {
            try {
                if (Files.notExists(path)) {
                    continue;
                }
                if (Files.isDirectory(path)) {
                    Files.walkFileTree(path, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            register(file.toFile());
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    register(path.toFile());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void register(File... files) {
        for (File file : files) {
            final String fileName = file.getName();

            if (!file.isFile() || !file.exists() || !file.canRead()
                || !fileName.toLowerCase().endsWith(Dictionary.FILE_SUFFIX)) {
                continue;
            }

            final String name = fileName.substring(0, fileName.lastIndexOf('.'));
            final String id = name.hashCode() + "_" + file.length();

            Dictionary dictionary = REGISTERED_DICTIONARIES.get(file);
            if (null == dictionary) {
                dictionary = new Dictionary(id, name, file);
                REGISTERED_DICTIONARIES.put(file, dictionary);
            }

            if (!MANAGED_DICTIONARIES.containsKey(id)) {
                MANAGED_DICTIONARIES.put(id, dictionary);
            }
        }
    }

    public static void unregister(File... files) {
        for (File file : files) {
            Dictionary dictionary = REGISTERED_DICTIONARIES.get(file);
            if (null != dictionary) {
                MANAGED_DICTIONARIES.remove(dictionary.id);
            }
        }
    }

    public static void clear() {
        MANAGED_DICTIONARIES.clear();
    }

    public static Dictionary getDictionary(String idOrName) {
        for (Dictionary dictionary : MANAGED_DICTIONARIES.values()) {
            if (Objects.equals(idOrName, dictionary.id) || Objects.equals(idOrName, dictionary.name)) {
                return dictionary;
            }
        }
        return null;
    }

    public static Dictionary getDictionary(Predicate<Dictionary> dictionaryPredicate) {
        return MANAGED_DICTIONARIES.values().stream().filter(dictionaryPredicate).findFirst().orElse(null);
    }

    public static Dictionaries getDictionaries() {
        return getDictionaries(null);
    }

    public static Dictionaries getDictionaries(Predicate<Dictionary> dictionaryPredicate) {
        return new Dictionaries(null == dictionaryPredicate
                ? MANAGED_DICTIONARIES.values()
                : MANAGED_DICTIONARIES.values().stream().filter(dictionaryPredicate).toList());
    }

    /**
     * 直接查词无过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords 关键词
     * @return 结果列表
     */
    public static Iterator<Dictionary.Entry> search(String keywords) {
        return search(keywords, null);
    }

    /**
     * 直接查词无条目过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords            关键词
     * @param dictionaryPredicate 词典过滤器
     * @return 结果列表
     */
    public static Iterator<Dictionary.Entry> search(String keywords,
                                                    Predicate<Dictionary> dictionaryPredicate) {
        return search(keywords, null, dictionaryPredicate);
    }

    /**
     * 直接查词且支持条目过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords            关键词
     * @param entryPredicate      条目过滤器
     * @param dictionaryPredicate 词典过滤器
     * @return 结果列表
     */
    public static Iterator<Dictionary.Entry> search(String keywords,
                                                    Predicate<Dictionary.Entry> entryPredicate,
                                                    Predicate<Dictionary> dictionaryPredicate) {
        final StringBuilder buf = new StringBuilder(null == keywords ? "" : keywords);
        final SearchType searchType = SearchType.detect(buf);
        return search(buf.toString(), searchType, entryPredicate, dictionaryPredicate);
    }

    /**
     * 按指定匹配模式查词，不会从所查词中检测匹配模式
     *
     * @param keywords            关键词
     * @param searchType          匹配模式
     * @param entryPredicate      条目过滤器
     * @param dictionaryPredicate 词典过滤器
     * @return 结果列表
     */
    public static Iterator<Dictionary.Entry> search(String keywords,
                                                    SearchType searchType,
                                                    Predicate<Dictionary.Entry> entryPredicate,
                                                    Predicate<Dictionary> dictionaryPredicate) {
        return getDictionaries(dictionaryPredicate).search(keywords, searchType, entryPredicate);
    }

    public int totalDictionaries() {
        return this.list.size();
    }

    public long totalEntries() {
        return this.list.stream().mapToLong(Dictionary::size).sum();
    }

    /**
     * 按指定匹配模式查词，不会从所查词中检测匹配模式
     *
     * @param keywords       关键词
     * @param searchType     匹配模式
     * @param entryPredicate 条目过滤器
     * @return 结果列表
     */
    public Iterator<Dictionary.Entry> search(String keywords,
                                             SearchType searchType,
                                             Predicate<Dictionary.Entry> entryPredicate) {
        if (list.size() < 40 || keywords.isEmpty() || keywords.replaceAll("[*\"' ]+", "").isEmpty()) {
            return new DictionariesSearcher(list, keywords, searchType, entryPredicate);
        }

        return list.parallelStream()
                .flatMap(dictionary -> {
                    final List<Dictionary.Entry> list = new ArrayList<>();
                    final DictionarySearcher searcher = new DictionarySearcher(dictionary, keywords, searchType, entryPredicate);
                    while (searcher.hasNext()) {
                        list.add(searcher.next());
                    }
                    return list.stream();
                })
                .toList().iterator();
    }
}
