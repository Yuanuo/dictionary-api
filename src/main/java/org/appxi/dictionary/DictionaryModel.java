package org.appxi.dictionary;

import org.appxi.holder.IntHolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DictionaryModel implements AutoCloseable {
    public static final byte HEADER_SIZE = 12;

    static File resolveIdxFile(Dictionary d) {
        return d.dataDir.resolve(d.conf.getString("file.idx", "dict-idx.bin")).toFile();
    }

    static File resolveDatFile(Dictionary d) {
        return d.dataDir.resolve(d.conf.getString("file.dat", "dict-dat.bin")).toFile();
    }

    public final Dictionary dictionary;

    private RandomAccessFile idxFile, datFile;
    private MappedByteBuffer idxBuff, datBuff;
    private long version = -1;
    private int size = -1;

    public DictionaryModel(Dictionary dictionary) {
        this.dictionary = dictionary;
    }

    @Override
    public void close() throws Exception {
        if (this.idxBuff != null) {
            this.idxBuff.clear();
        }
        if (this.datBuff != null) {
            this.datBuff.clear();
        }
        if (this.idxFile != null) {
            this.idxFile.close();
        }
        if (this.datFile != null) {
            this.datFile.close();
        }
        //
        this.idxBuff = null;
        this.datBuff = null;
        this.idxFile = null;
        this.datFile = null;
    }

    public final long version() {
        if (this.version < 0) {
            getIdxBuff();
        }
        return this.version;
    }

    /**
     * 获取总条目数，此函数应该被首先调用。
     *
     * @return 总条目数
     */
    public final int size() {
        if (this.size < 0) {
            getIdxBuff();
        }
        return this.size;
    }

    private MappedByteBuffer getIdxBuff() {
        // 初始化IO
        if (null == this.idxBuff) {
            try {
                this.idxFile = new RandomAccessFile(resolveIdxFile(dictionary), "r");
                this.idxBuff = this.idxFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0L, this.idxFile.length());
                this.version = this.idxBuff.getLong();
                this.size = this.idxBuff.getInt();
            } catch (IOException ioe) {
                ioe.printStackTrace(); //for debug
            }
        }
        return this.idxBuff;
    }

    private MappedByteBuffer getDatBuff() {
        // 初始化IO
        if (null == this.datBuff) {
            try {
                this.datFile = new RandomAccessFile(resolveDatFile(dictionary), "r");
                this.datBuff = this.datFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0L, this.datFile.length());
            } catch (IOException ioe) {
                ioe.printStackTrace(); //for debug
            }
        }
        return this.datBuff;
    }

    /**
     * 根据ID读取词条信息
     */
    public DictEntry readEntry(int id) {
        DictEntry entry = new DictEntry();
        entry.model = this;
        entry.id = id;

        this.readEntry(entry);

        return entry;
    }

    /**
     * 读取词条信息
     *
     * @return entrySize
     */
    public short readEntry(DictEntry entry) {
        this.idxBuff.position(entry.id);

        final short entrySize = this.idxBuff.getShort();
        entry.pid = this.idxBuff.getInt();
        entry.type = this.idxBuff.getShort();
        entry.contentMark = this.idxBuff.getInt();
        entry.titleLength = this.idxBuff.getShort();

        byte[] titleBytes = new byte[this.idxBuff.getShort()];
        this.idxBuff.get(titleBytes);
        entry.title = titleBytes;

        return entrySize;
    }

    /**
     * 读取子条目ID列表
     */
    public void readEntryContentList(DictEntry entry) {
        if (!entry.isCategory()) {
            return;
        }

        final int[] children = new int[entry.contentMark];
        final IntHolder childIdx = new IntHolder(0);
        this.forFilter((id, pid) -> {
            if (pid == entry.id) {
                children[childIdx.value++] = id;
            }
            return childIdx.value >= entry.contentMark;
        });
        entry.content = children;
    }

    /**
     * 读取词条内容
     */
    public void readEntryContentText(DictEntry entry) {
        if (entry.isCategory()) {
            return;
        }

        this.getDatBuff().position(entry.contentMark);

        byte[] contentBytes = new byte[this.datBuff.getInt()];
        this.datBuff.get(contentBytes);
        entry.content = contentBytes;
    }

    public void readEntryContent(DictEntry entry) {
        if (entry.isCategory()) {
            this.readEntryContentList(entry);
        } else {
            this.readEntryContentText(entry);
        }
    }

    /**
     * 以支持过滤的方式遍历词条ID
     *
     * @param idPidPredicate 回调函数，该函数返回true表示立即中断遍历过程，返回false表示继续遍历
     */
    public void forFilter(BiPredicate<Integer, Integer> idPidPredicate) {
        int entryPosition = HEADER_SIZE;
        for (int i = 0; i < this.size(); i++) {
            short entrySize = this.idxBuff.getShort(entryPosition);
            int id = this.idxBuff.getInt(entryPosition + 2);
            int pid = this.idxBuff.getInt(entryPosition + 6);

            if (idPidPredicate.test(id, pid)) {
                break;
            }
            entryPosition += entrySize;
        }
    }

    /**
     * 以支持过滤的方式遍历词条
     *
     * @param entryPredicate 回调函数，该函数返回true表示立即中断遍历过程，返回false表示继续遍历
     */
    public void forFilter(Predicate<DictEntry> entryPredicate) {
        int entryPosition = HEADER_SIZE;
        for (int i = 0; i < this.size(); i++) {
            DictEntry entry = new DictEntry();
            entry.model = this;
            entry.id = entryPosition;
            short entrySize = readEntry(entry);

            if (entryPredicate.test(entry)) {
                break;
            }
            entryPosition += entrySize;
        }
    }

    /**
     * 遍历词条ID
     *
     * @param idPidConsumer 回调函数
     */
    public void forEach(BiConsumer<Integer, Integer> idPidConsumer) {
        forFilter((id, pid) -> {
            idPidConsumer.accept(id, pid);
            return false;
        });
    }

    /**
     * 遍历词条
     *
     * @param entryConsumer 回调函数
     */
    public void forEach(Consumer<DictEntry> entryConsumer) {
        forFilter(entry -> {
            entryConsumer.accept(entry);
            return false;
        });
    }

    /**
     * 直接查词无条目过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords 要查询的字或词
     * @return 搜索结果
     */
    public Iterator<DictEntry.Scored> search(String keywords) {
        return search(keywords, null);
    }

    /**
     * 直接查词且支持条目过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords    要查询的字或词
     * @param entryFilter 条目过滤器
     * @return 搜索结果
     */
    public Iterator<DictEntry.Scored> search(String keywords, Predicate<DictEntry> entryFilter) {
        StringBuilder buf = new StringBuilder(null == keywords ? "" : keywords);
        DictEntryExpr entryExpr = DictEntryExpr.detectForTitle(buf);
        return search(buf.toString(), entryExpr, entryFilter);
    }

    /**
     * 按指定匹配模式查词，不会从所查词中检测匹配模式
     *
     * @param keywords    要查询的字或词
     * @param entryExpr   默认为TitleStartsWith
     * @param entryFilter 条目过滤器
     * @return 搜索结果
     */
    public Iterator<DictEntry.Scored> search(String keywords, DictEntryExpr entryExpr, Predicate<DictEntry> entryFilter) {
        if (null == entryExpr || !entryExpr.name().startsWith("Title"))
            entryExpr = DictEntryExpr.TitleStartsWith;

        keywords = null == keywords ? null : keywords.trim().toLowerCase();
        //
        return new SearcherIterator(this, keywords, entryExpr, entryFilter);
    }

    private static final class SearcherIterator implements Iterator<DictEntry.Scored> {
        private final DictionaryModel model;
        private final DictEntryExpr entryExpr;
        private final String keywords;
        private final short keywordsLength;
        private final byte[] keywordsBytes;
        private final Predicate<DictEntry> entryFilter;
        private int entryPosition = HEADER_SIZE;
        private int index = -1, size = -1;
        private DictEntry.Scored nextEntry;

        public SearcherIterator(DictionaryModel model, String keywords, DictEntryExpr entryExpr, Predicate<DictEntry> entryFilter) {
            this.model = model;
            this.entryExpr = entryExpr;
            this.keywords = keywords;
            this.keywordsLength = (short) (null == keywords || keywords.isBlank() ? 0 : keywords.length());
            this.keywordsBytes = this.keywordsLength == 0 ? null : keywords.getBytes(model.dictionary.getCharset());
            this.entryFilter = entryFilter;
        }

        @Override
        public boolean hasNext() {
            if (this.size == -1) {
                this.size = this.model.size();
            }
            // find next entry
            // reset
            this.nextEntry = null;
            //
            while (++this.index < this.size) {
                final DictEntry.Scored entry = new DictEntry.Scored().setScore(0);
                entry.model = this.model;
                entry.id = this.entryPosition;
                short entrySize = this.model.readEntry(entry);
                this.entryPosition += entrySize;
                // skip folder
                if (entry.isCategory())
                    continue;

                // 当前词条的字符长度小于指定的keywords长度时，直接认为不符合条件
                // 例如，搜索5个字符，但当前词条仅1-4个字符，此时无法匹配
                if (keywordsLength > entry.titleLength) {
                    continue;
                }
                // skip not acceptable
                if (null != this.entryFilter && !this.entryFilter.test(entry))
                    continue;

                // 未指定keywords时不做后续验证
                if (keywordsLength == 0) {
                    this.nextEntry = entry;
                    break;
                }

                // 无论哪种匹配模式，如果完全相等则直接返回
                if (entry.titleLength == keywordsLength && Arrays.equals((byte[]) entry.title, keywordsBytes)) {
                    this.nextEntry = entry.setScore(100);
                    break;
                }
                if (this.entryExpr == DictEntryExpr.TitleStartsWith) {
                    if (Arrays.mismatch((byte[]) entry.title, keywordsBytes) == keywordsBytes.length) {
                        this.nextEntry = entry.setScore(10);
                        break;
                    }
                } else if (this.entryExpr == DictEntryExpr.TitleEquals) {
                    // 无需再次验证
                } else {
                    // 仅在需要时再做词条名称解码
                    final String title = entry.title();
                    if (this.entryExpr == DictEntryExpr.TitleEndsWith) {
                        if (title.endsWith(keywords)) {
                            this.nextEntry = entry.setScore(10);
                            break;
                        }
                    } else if (this.entryExpr == DictEntryExpr.TitleContains) {
                        if (title.contains(keywords)) {
                            this.nextEntry = entry.setScore(10);
                            break;
                        }
                    }
                }
            }
            //
            return this.nextEntry != null;
        }

        @Override
        public DictEntry.Scored next() {
            return this.nextEntry;
        }
    }
}
