package org.appxi.dictionary;

import org.appxi.holder.IntHolder;
import org.appxi.util.ext.Node;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Dictionary implements AutoCloseable {
    public static final String FILE_SUFFIX = ".tomed";

    public final String id, name;

    public final File file;

    private RandomAccessFile fileAccessor;
    private PartMappedReader titlePartMappedReader, contentPartMappedReader;

    private long version = -1;
    private Charset charset;
    private Compression compression;
    private int size = -1;
    private Object metadata;

    private final File extraResourcesFile;
    private ZipFile extraResourcesAccessor;

    Dictionary(String id, String name, File file) {
        this.id = id;
        this.name = name;
        this.file = file;
        this.extraResourcesFile = new File(file + "_extra");
    }

    @Override
    public void close() throws Exception {
        if (this.titlePartMappedReader != null) {
            this.titlePartMappedReader.close();
            this.titlePartMappedReader = null;
        }
        if (this.contentPartMappedReader != null) {
            this.contentPartMappedReader.close();
            this.contentPartMappedReader = null;
        }
        if (this.fileAccessor != null) {
            this.fileAccessor.close();
            this.fileAccessor = null;
        }
        if (this.extraResourcesAccessor != null) {
            this.extraResourcesAccessor.close();
            this.extraResourcesAccessor = null;
        }
        //
        this.version = -1;
        this.size = -1;
    }

    private RandomAccessFile ensureFileAccessor() {
        if (null == this.fileAccessor) {
            try {
                this.fileAccessor = new RandomAccessFile(this.file, "r");

                //
                this.version = this.fileAccessor.readLong();

                //
                final short charsetType = this.fileAccessor.readShort();
                this.charset = switch (charsetType) {
                    case 1 -> StandardCharsets.UTF_16BE;
                    case 2 -> StandardCharsets.UTF_16LE;
                    case 3 -> StandardCharsets.US_ASCII;
                    case 4 -> StandardCharsets.ISO_8859_1;
                    default -> StandardCharsets.UTF_8;
                };

                //
                this.compression = Compression.values()[this.fileAccessor.readByte()];

                //
                this.metadata = this.fileAccessor.getFilePointer();
                final int metadataBytesLength = this.fileAccessor.readInt();
                this.fileAccessor.skipBytes(metadataBytesLength);

                //
                this.size = this.fileAccessor.readInt();

                //
                final int contentPartPositionsLength = this.fileAccessor.readInt();
                final List<Long> contentPartPositions = new ArrayList<>(contentPartPositionsLength);
                for (int i = 0; i < contentPartPositionsLength; i++) {
                    contentPartPositions.add(this.fileAccessor.readLong());
                }
                final long contentPartFilePointer = contentPartPositions.get(0);
                contentPartPositions.set(0, 0L);
                if (contentPartPositions.size() == 2) {
                    this.contentPartMappedReader = new SinglePartMappedReader(contentPartFilePointer, 0, contentPartPositions.get(1));
                } else {
                    this.contentPartMappedReader = new MultiplePartMappedReader(contentPartFilePointer, contentPartPositions);
                }

                //
                final long titlePartFilePointer = this.fileAccessor.getFilePointer();
                this.titlePartMappedReader = new SinglePartMappedReader(titlePartFilePointer, 0, contentPartFilePointer - titlePartFilePointer);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        return this.fileAccessor;
    }

    public long version() {
        if (this.version < 0) {
            ensureFileAccessor();
        }
        return this.version;
    }

    public Charset charset() {
        if (this.charset == null) {
            ensureFileAccessor();
        }
        return this.charset;
    }

    public String metadata() {
        if (this.metadata instanceof String str) {
            return str;
        }
        if (this.metadata == null) {
            ensureFileAccessor();
        }
        if (this.metadata instanceof Long metadataPartFilePointer) {
            try {
                this.fileAccessor.seek(metadataPartFilePointer);
                final byte[] dataBytes = new byte[this.fileAccessor.readInt()];
                this.fileAccessor.read(dataBytes);
                this.metadata = new String(dataBytes, StandardCharsets.UTF_8);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        return (String) metadata;
    }

    /**
     * 获取总条目数，此函数应该被首先调用。
     *
     * @return 总条目数
     */
    public int size() {
        if (this.size < 0) {
            ensureFileAccessor();
        }
        return this.size;
    }

    /**
     * 根据ID读取词条信息
     */
    public Entry readEntry(int id) {
        Entry entry = new Entry(this);
        entry.id = id;

        this.readEntry(entry);

        return entry;
    }

    /**
     * 读取词条信息
     *
     * @return entrySize
     */
    public short readEntry(Entry entry) {
        final MappedByteBuffer buffer = titlePartMappedReader.position(entry.id);

        final short entrySize = buffer.getShort();
        entry.pid = buffer.getInt();
        entry.type = buffer.get();
        entry.contentMark = buffer.getLong();

        final byte[] dataBytes = new byte[buffer.getShort()];
        buffer.get(dataBytes);
        entry.title = dataBytes;

        return entrySize;
    }

    public void readEntryContent(Entry entry) {
        if (entry.isCategory()) {
            this.readEntryContentList(entry);
        } else {
            this.readEntryContentText(entry);
        }
    }

    /**
     * 读取子条目ID列表
     */
    private void readEntryContentList(Entry entry) {
        final int[] children = new int[(int) entry.contentMark];
        final IntHolder childIdx = new IntHolder(0);
        this.filter((id, pid) -> {
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
    private void readEntryContentText(Entry entry) {
        final MappedByteBuffer buffer = this.contentPartMappedReader.position(entry.contentMark);

        final byte[] dataBytes = new byte[buffer.getInt()];
        buffer.get(dataBytes);
        entry.content = dataBytes;
    }

    public Node<Entry> readEntryTree() {
        final Node<Entry> tree = new Node<>();

        final Map<Integer, Node<Entry>> idMap = new HashMap<>();

        this.forEach(entry -> idMap.put(entry.id, null == tree.value ? tree.setValue(entry) : idMap.get(entry.pid).add(entry)));

        idMap.clear();

        return tree;
    }

    /**
     * 以支持过滤的方式遍历词条ID
     *
     * @param idPidPredicate 回调函数，该函数返回true表示立即中断遍历过程，返回false表示继续遍历
     */
    public void filter(BiPredicate<Integer, Integer> idPidPredicate) {
        final MappedByteBuffer buffer = titlePartMappedReader.position(0);

        int entryPosition = 0;
        for (int i = 0; i < this.size(); i++) {
            short entrySize = buffer.getShort(entryPosition);
            int id = buffer.getInt(entryPosition + 2);
            int pid = buffer.getInt(entryPosition + 6);

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
    public void filter(Predicate<Entry> entryPredicate) {
        int entryPosition = 0;
        for (int i = 0; i < this.size(); i++) {
            Entry entry = new Entry(this);
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
        filter((id, pid) -> {
            idPidConsumer.accept(id, pid);
            return false;
        });
    }

    /**
     * 遍历词条
     *
     * @param entryConsumer 回调函数
     */
    public void forEach(Consumer<Entry> entryConsumer) {
        filter(entry -> {
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
    public Iterator<Entry> search(String keywords) {
        return search(keywords, null);
    }

    /**
     * 直接查词且支持条目过滤；匹配模式从keywords检测:
     * <p>匹配规则：默认1）以词开始：输入 或 输入*；2）以词结尾：*输入；3）以词存在：*输入*；4）以双引号包含精确查词："输入"；</p>
     *
     * @param keywords       要查询的字或词
     * @param entryPredicate 条目过滤器
     * @return 搜索结果
     */
    public Iterator<Entry> search(String keywords, Predicate<Entry> entryPredicate) {
        StringBuilder buf = new StringBuilder(null == keywords ? "" : keywords);
        SearchType searchType = SearchType.detect(buf);
        return search(buf.toString(), searchType, entryPredicate);
    }

    /**
     * 按指定匹配模式查词，不会从所查词中检测匹配模式
     *
     * @param keywords       要查询的字或词
     * @param searchType     默认为TitleStartsWith
     * @param entryPredicate 条目过滤器
     * @return 搜索结果
     */
    public Iterator<Entry> search(String keywords, SearchType searchType, Predicate<Entry> entryPredicate) {
        return new DictionarySearcher(this, keywords, searchType, entryPredicate);
    }

    public boolean hasExtraResources() {
        return this.extraResourcesFile.isFile() && this.extraResourcesFile.exists() && this.extraResourcesFile.length() > 1;
    }

    public InputStream getExtraResource(String name) {
        if (null == this.extraResourcesAccessor) {
            try {
                this.extraResourcesAccessor = new ZipFile(this.extraResourcesFile);
            } catch (Exception e) {
                return new ByteArrayInputStream(new byte[0]);
            }
        }

        try {
            final ZipEntry zipEntry = extraResourcesAccessor.stream()
                    .filter(v -> v.getName().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
            return null == zipEntry ? null : this.extraResourcesAccessor.getInputStream(zipEntry);
        } catch (Exception e) {
            return new ByteArrayInputStream(new byte[0]);
        }
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
        return this.name;
    }

    /**
     * 词条
     */
    public static class Entry implements Comparable<Entry> {
        /**
         * 表示 分类/分组/目录 类型
         */
        public static final int TYPE_CATEGORY = 0;
        /**
         * 表示 具有内容的词条 类型
         */
        public static final int TYPE_ITEM = 1;

        /**
         * 便于访问的当前词条数据端口
         */
        public final Dictionary dictionary;

        /**
         * 词条ID，亦即position
         */
        public int id;
        /**
         * 父级ID，亦即position
         */
        public int pid = -1;
        /**
         * 词条词性/类型，扩展用
         */
        public byte type;

        /**
         * 内部用，词条名称
         */
        Object title;
        /**
         * 内部用，表示内容相关。若当前为Category类型时则为子级列表数；否则为内容数据的position位置。
         *
         * @see #title()
         */
        long contentMark;
        /**
         * 内部用，表示内容。若当前为Category类型时表示子级列表ID（int[]）；否则为内容数据（String）。
         *
         * @see #contentList()
         * @see #contentText()
         */
        Object content;

        Entry(Dictionary dictionary) {
            this.dictionary = dictionary;
        }

        public final boolean isCategory() {
            return this.type == TYPE_CATEGORY;
        }

        /**
         * 获取词条名称
         *
         * @return 词条名称
         */
        public String title() {
            if (this.title instanceof byte[] bytes) {
                this.title = new String(bytes, this.dictionary.charset());
            }
            return (String) this.title;
        }

        /**
         * 获取词条内容，
         * 仅在 {@link #isCategory()} 为false时有意义
         *
         * @return 词条内容
         */
        public String contentText() {
            if (null == this.content) {
                this.dictionary.readEntryContent(this);
            }

            if (this.content instanceof byte[] bytes) {
                this.content = new String(dictionary.compression.decompress(bytes), dictionary.charset());
            }

            return String.valueOf(this.content);
        }

        /**
         * 获取子级列表ID，
         * 仅在 {@link #isCategory()} 为true时有意义
         *
         * @return 子级列表ID
         */
        public int[] contentList() {
            if (null == this.content) {
                this.dictionary.readEntryContent(this);
            }

            if (this.content instanceof int[] arr) {
                return arr;
            }
            return new int[0];
        }

        public static Entry of(String title, String content) {
            final Entry entry = new Entry(null);
            entry.type = TYPE_ITEM;
            entry.title = title.toLowerCase();
            entry.content = content;
            return entry;
        }

        public static Entry ofCategory(String title) {
            final Entry entry = new Entry(null);
            entry.type = TYPE_CATEGORY;
            entry.title = title;
            return entry;
        }

        @Override
        public int compareTo(Entry other) {
            return 0;
        }
    }

    private interface PartMappedReader extends AutoCloseable {
        MappedByteBuffer position(long position);
    }

    private class SinglePartMappedReader implements PartMappedReader {
        final long filePointer, from, size;

        MappedByteBuffer mappedBuffer;

        SinglePartMappedReader(long filePointer, long from, long size) {
            this.filePointer = filePointer;
            this.from = from;
            this.size = size;
        }

        @Override
        public MappedByteBuffer position(long position) {
            if (null == this.mappedBuffer) {
                try {
                    this.mappedBuffer = fileAccessor.getChannel().map(FileChannel.MapMode.READ_ONLY, filePointer + from, size);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
            mappedBuffer.position((int) (position - from));
            return mappedBuffer;
        }

        @Override
        public void close() throws Exception {
            if (null != this.mappedBuffer) {
                this.mappedBuffer.clear();

//                if (mappedBuffer instanceof sun.nio.ch.DirectBuffer directBuffer) {
//                    jdk.internal.ref.Cleaner cleaner = directBuffer.cleaner();
//                    mappedBuffer = null;
//                    cleaner.clean();
//                }
                this.mappedBuffer = null;
            }
        }
    }

    private class MultiplePartMappedReader implements PartMappedReader {
        final SinglePartMappedReader[] singlePartReaders;

        MultiplePartMappedReader(long filePointer, List<Long> partPositions) {
            this.singlePartReaders = new SinglePartMappedReader[partPositions.size()];

            for (int i = 0; i < partPositions.size(); i++) {
                long from = partPositions.get(i++);
                if (i < partPositions.size()) {
                    long size = partPositions.get(i--);
                    this.singlePartReaders[i] = new SinglePartMappedReader(filePointer, from, size);
                }
            }
        }

        @Override
        public MappedByteBuffer position(long position) {
            for (SinglePartMappedReader singlePartReader : singlePartReaders) {
                if (position >= singlePartReader.from && position <= singlePartReader.size) {
                    return singlePartReader.position(position);
                }
            }
            return null;
        }

        @Override
        public void close() throws Exception {
            for (SinglePartMappedReader singlePartReader : singlePartReaders) {
                singlePartReader.close();
            }
        }
    }
}
