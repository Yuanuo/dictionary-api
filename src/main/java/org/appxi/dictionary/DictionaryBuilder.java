package org.appxi.dictionary;

import org.appxi.holder.IntHolder;
import org.appxi.util.FileHelper;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class DictionaryBuilder {
    public static void build(Dictionary dictionary, List<DictEntry.Node> entryItems) throws Exception {
        build(dictionary, entryItems.stream().map(v -> (DictEntryExt) v).toArray(DictEntryExt[]::new));
    }

    public static void build(Dictionary dictionary, DictEntryExt... entryItems) throws Exception {
        final DictEntryExt rootEntry = ofCategory("ROOT");
        rootEntry.children.addAll(Arrays.asList(entryItems));

        final List<DictEntryExt> flatEntryList = new ArrayList<>(List.of(rootEntry));

        final HashMap<Integer, Integer> distinctPositions = new HashMap<>();
        final IntHolder idxPosition = new IntHolder(DictionaryModel.HEADER_SIZE);
        final IntHolder binPosition = new IntHolder(0);
        for (int i = 0; i < flatEntryList.size(); i++) {
            DictEntryExt entry = flatEntryList.get(i);
            entry.prepare(distinctPositions, idxPosition, binPosition);
            if (entry.isCategory()) {
                flatEntryList.addAll(entry.children.stream().map(child -> {
                    child.pid = entry.id;
                    return (DictEntryExt) child;
                }).toList());
            }
        }
        //
        //
        FileHelper.makeDirs(dictionary.dataDir);
        // build index
        RandomAccessFile idxFile = new RandomAccessFile(DictionaryModel.resolveIdxFile(dictionary), "rw");
        idxFile.setLength(idxPosition.value);
        idxFile.seek(0);
        idxFile.writeLong(0L);
        idxFile.writeInt(flatEntryList.size());
        for (DictEntryExt entry : flatEntryList) {
            assert entry.id == idxFile.getFilePointer();
            //
            idxFile.writeShort(DictEntryExt.ENTRY_BASE_SIZE + entry.titleBytes.length);
            idxFile.writeInt(entry.pid);
            idxFile.writeShort(entry.type);
            idxFile.writeInt(entry.contentMark);
            idxFile.writeShort(entry.title().length());
            idxFile.writeShort(entry.titleBytes.length);
            idxFile.write(entry.titleBytes);
        }
        idxFile.close();

        // build data
        RandomAccessFile datFile = new RandomAccessFile(DictionaryModel.resolveDatFile(dictionary), "rw");
        datFile.setLength(binPosition.value);
        datFile.seek(0);
        for (DictEntryExt entry : flatEntryList) {
            if (entry.isCategory()) {
                continue;
            }
            // 重复的内容不需写入
            if (null == entry.contentBytes) {
                continue;
            }
            datFile.writeInt(entry.contentBytes.length);
            datFile.write(entry.contentBytes);
        }
        datFile.close();
        //
        dictionary.conf.save();
    }

    public static class DictEntryExt extends DictEntry.Node {
        private static final byte ENTRY_BASE_SIZE = 16;
        private byte[] titleBytes, contentBytes;

        private DictEntryExt(int type, String title, String content) {
            super(type, title, content);
        }

        private void prepare(HashMap<Integer, Integer> distinctPositions, IntHolder idxPosition, IntHolder binPosition) {
            this.titleBytes = this.title().getBytes(DictionaryModel.CHARSET);
            if (this.isCategory()) {
                this.contentMark = this.children.size();
            } else {
                int distinctKey = this.contentText().hashCode();
                if (distinctPositions.containsKey(distinctKey)) {
                    // 已经存在相同内容，仅指向该内容位置
                    this.contentMark = distinctPositions.get(distinctKey);
                    // 标记为null，表示是重复内容无需写入
                    this.contentBytes = null;
                } else {
                    this.contentMark = binPosition.value;
                    // 记录下此内容的位置
                    distinctPositions.put(distinctKey, this.contentMark);
                    this.contentBytes = this.contentText().getBytes(DictionaryModel.CHARSET);
                    binPosition.value += 4 + this.contentBytes.length;
                }
            }

            this.id = idxPosition.value;
            idxPosition.value += ENTRY_BASE_SIZE + this.titleBytes.length;
        }
    }

    public static DictEntryExt of(String title, String content) {
        // 对于词条直接存储为小写字符，以提升查词性能
        return new DictEntryExt(DictEntry.TYPE_ITEM, title.toLowerCase(), content);
    }

    public static DictEntryExt ofCategory(String title) {
        return new DictEntryExt(DictEntry.TYPE_CATEGORY, title, null);
    }
}
