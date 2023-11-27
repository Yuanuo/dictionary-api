package org.appxi.dictionary;

import org.appxi.holder.IntHolder;
import org.appxi.holder.LongHolder;
import org.appxi.smartcn.convert.ChineseConvertors;
import org.appxi.util.FileHelper;
import org.appxi.util.ext.Compression;
import org.appxi.util.ext.Node;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DictionaryBuilder {
    private static final byte ENTRY_BASE_SIZE = 17;
    private static final Object AK_CONTENT_BYTES = "AK_contentBytes";
    private static final Object AK_TITLE_BYTES = "AK_titleBytes";
    private static final Object AK_TITLE_ALIAS = "AK_titleAlias";

    public static Node<Dictionary.Entry> alias(Node<Dictionary.Entry> entryNode, String... alias) {
        if (null == alias || alias.length == 0) {
            entryNode.removeAttr(AK_TITLE_ALIAS);
        } else {
            entryNode.attr(AK_TITLE_ALIAS, alias);
        }
        return entryNode;
    }

    public static File build(Charset charset,
                             Compression compression,
                             String metadata,
                             String name, Path repo,
                             Dictionary.Entry... entries) throws Exception {
        final Node<Dictionary.Entry> entryRoot = new Node<>(Dictionary.Entry.ofCategory("ROOT"));
        for (Dictionary.Entry entry : entries) {
            entryRoot.add(entry);
        }
        return build(entryRoot, charset, compression, metadata, name, repo);
    }

    public static File build(Node<Dictionary.Entry> entryRoot,
                             Charset charset,
                             Compression compression,
                             String metadata,
                             String name, Path repo) throws Exception {
        final short charsetType;
        if (StandardCharsets.UTF_16BE.equals(charset)) {
            charsetType = 1;
        } else if (StandardCharsets.UTF_16LE.equals(charset)) {
            charsetType = 2;
        } else if (StandardCharsets.US_ASCII.equals(charset)) {
            charsetType = 3;
        } else if (StandardCharsets.ISO_8859_1.equals(charset)) {
            charsetType = 4;
        } else {
            charsetType = 0;
            charset = StandardCharsets.UTF_8;
        }
        //
        final byte[] metadataBytes = (null == metadata ? "" : metadata).getBytes(StandardCharsets.UTF_8);

        //
        prepareEntryVariants(entryRoot);
        //
        final List<Node<Dictionary.Entry>> entryNodes = new ArrayList<>(List.of(entryRoot));

        //
        final IntHolder titlePartCapacity = new IntHolder(0);
        final LongHolder contentPartCapacity = new LongHolder(0);
        final Map<Integer, Long> hashcodePositions = new HashMap<>();

        final List<Long> contentPartPositions = new ArrayList<>();
        contentPartPositions.add(0L);

        long nextContentMappedBufferLimitPosition = Integer.MAX_VALUE;
        for (int i = 0; i < entryNodes.size(); i++) {
            Node<Dictionary.Entry> entryNode = entryNodes.get(i);
            prepareEntryPosition(entryNode, charset, compression, titlePartCapacity, contentPartCapacity, hashcodePositions);
            if (contentPartCapacity.value > nextContentMappedBufferLimitPosition) {
                contentPartPositions.add(entryNode.value.contentMark);
                nextContentMappedBufferLimitPosition = entryNode.value.contentMark + Integer.MAX_VALUE;
            }

            if (entryNode.value.isCategory()) {
                entryNodes.addAll(entryNode.children.stream().map(child -> {
                    child.value.pid = entryNode.value.id;
                    return child;
                }).toList());
            }
        }
        contentPartPositions.add(contentPartCapacity.value);
        //
        //
        final File file = FileHelper.getNonExistsPath(repo, name, Dictionary.FILE_SUFFIX).toFile();
        file.getParentFile().mkdirs();
        // build
        final RandomAccessFile fileAccessor = new RandomAccessFile(file, "rw");

        final ByteBuffer headBuffer = ByteBuffer.allocate(128 + metadataBytes.length + contentPartPositions.size() * 8);
        headBuffer.putLong(3L); // version
        headBuffer.putShort(charsetType); // charset
        headBuffer.put((byte) compression.ordinal()); // compressed
        headBuffer.putInt(metadataBytes.length); // metadata size
        headBuffer.put(metadataBytes); // metadata bytes
        headBuffer.putInt(entryNodes.size()); // entry size

        //
        headBuffer.putInt(contentPartPositions.size()); // content positions size
        headBuffer.mark();
        contentPartPositions.forEach(headBuffer::putLong); // content position

        final long filePointerOfTitlePart = headBuffer.position();
        final long filePointerOfContentPart = filePointerOfTitlePart + titlePartCapacity.value;
        final long filePointerAfterContentPart = filePointerOfContentPart + contentPartCapacity.value;

        headBuffer.reset();
        headBuffer.putLong(filePointerOfContentPart);
        headBuffer.position((int) filePointerOfTitlePart);

        //

        //
        fileAccessor.setLength(filePointerAfterContentPart);
        fileAccessor.write(Arrays.copyOf(headBuffer.flip().array(), headBuffer.limit()));

        // build title part
        for (Node<Dictionary.Entry> entryNode : entryNodes) {
            assert entryNode.value.id == fileAccessor.getFilePointer() - filePointerOfTitlePart;
            //
            final byte[] titleBytes = entryNode.removeAttr(AK_TITLE_BYTES);
            //
            fileAccessor.writeShort(ENTRY_BASE_SIZE + titleBytes.length);
            fileAccessor.writeInt(entryNode.value.pid);
            fileAccessor.writeByte(entryNode.value.type);
            fileAccessor.writeLong(entryNode.value.contentMark);
            fileAccessor.writeShort(titleBytes.length);
            fileAccessor.write(titleBytes);
        }


        // build content part
        for (Node<Dictionary.Entry> entryNode : entryNodes) {
            if (entryNode.value.isCategory()) {
                continue;
            }
            final byte[] contentBytes = entryNode.removeAttr(AK_CONTENT_BYTES);
            // 重复的内容不需写入
            if (null == contentBytes) {
                continue;
            }

            assert entryNode.value.contentMark == fileAccessor.getFilePointer() - filePointerOfContentPart;

            fileAccessor.writeInt(contentBytes.length);
            fileAccessor.write(contentBytes);
        }

        assert filePointerAfterContentPart == fileAccessor.getFilePointer();

        fileAccessor.close();

        return file;
    }

    private static void prepareEntryPosition(Node<Dictionary.Entry> entryNode,
                                             Charset charset,
                                             Compression compression,
                                             IntHolder titlePartCapacity,
                                             LongHolder contentPartCapacity,
                                             Map<Integer, Long> hashcodePositions) {
        final byte[] titleBytes = entryNode.value.title().getBytes(charset);
        entryNode.attr(AK_TITLE_BYTES, titleBytes);
        entryNode.value.id = titlePartCapacity.value;
        titlePartCapacity.value += ENTRY_BASE_SIZE + titleBytes.length;

        //
        if (entryNode.value.isCategory()) {
            entryNode.value.contentMark = entryNode.children.size();
        } else {
            int hashcode = entryNode.value.contentText().hashCode();
            if (hashcodePositions.containsKey(hashcode)) {
                // 已经存在相同内容，仅指向该内容位置，重复内容无需写入
                entryNode.value.contentMark = hashcodePositions.get(hashcode);
            } else {
                entryNode.value.contentMark = contentPartCapacity.value;
                // 记录下此内容的位置
                hashcodePositions.put(hashcode, entryNode.value.contentMark);

                final byte[] contentBytes = compression.compress(entryNode.value.contentText().getBytes(charset));
                entryNode.attr(AK_CONTENT_BYTES, contentBytes);
                // 计算出下一个数据块的位置
                contentPartCapacity.value += 4 + contentBytes.length;
            }
        }
    }

    private static void prepareEntryVariants(Node<Dictionary.Entry> entryTree) {
        //
        final Set<String> titles = new HashSet<>();
        entryTree.traverse((depth, node, entry) -> {
            if (!node.hasChildren()) {
                titles.add(entry.title());
            }
        });
        //
        final List<Dictionary.Entry> variants = new ArrayList<>();
        entryTree.traverse((depth, node, entry) -> {
            if (node.hasChildren()) {
                return;
            }

            for (String title : new String[]{
                    entry.title().toLowerCase(), // 全小写
                    ChineseConvertors.toHans(entry.title()), // 简体中文
                    ChineseConvertors.toHant(entry.title()) //繁体中文
            }) {
                if (!titles.contains(title)) {
                    System.out.println("Make Variant : " + title);
                    variants.add(Dictionary.Entry.of(title, entry.contentText()));
                    titles.add(title);
                }
            }
        });
        variants.forEach(entryTree::add);
    }
}
