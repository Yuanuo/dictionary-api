package org.appxi.dictionary.io;

import org.appxi.dictionary.Dictionary;
import org.appxi.util.FileHelper;
import org.appxi.util.ext.Node;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

public class DictFileTxt {
    public static Node<Dictionary.Entry> read(File file) {
        final String dictName = file.getName().replaceFirst("[.][^.]+$", "").strip();
        final Node<Dictionary.Entry> entryTree = new Node<>(Dictionary.Entry.ofCategory(dictName));

        FileHelper.lines(file.toPath(), line -> {
            if (line.isBlank()) {
                return;
            }

            final String[] parts = line.split("】", 2);
            if (parts.length != 2) {
                return;
            }

            String title = parts[0].replace("【", "").strip();
            String content = parts[1].replace("\\\\n", "\n").strip();

            Dictionary.Entry entry = Dictionary.Entry.of(title, content);
            entryTree.add(entry);
        });

        return entryTree;
    }

    public static void save(Path saveDir, String name, Node<Dictionary.Entry> entryTree) {
        final Path file = FileHelper.getNonExistsPath(saveDir, name, ".txt");
        FileHelper.makeParents(file);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            final HashSet<Integer> hashSet = new HashSet<>();
            entryTree.traverse(((level, entryNode, entry) -> {
                try {
                    String title = entry.title()
                            .replace("\t", " ")
                            .replace("】", "")
                            .replace("\u0090", "")
                            .replace("\uFEFF", "")
                            .replaceAll("　+", " ")
                            .strip();
                    if (title.isBlank()) {
                        return;
                    }
                    //
                    String content = entry.contentText()
                            .replaceAll("\r\n|\r|\n", "\\\\n");
                    content = content
                            .replace("spanstyle", "span style")
                            .replace("spanlang", "span lang")
                            .replace(" lang=EN-US", "")
                            .replace(" lang=BO", "")
                            .replace(" style='line-height:18.0pt'", "")
                            .replace(" ", "")
                            .strip();
                    //
                    final String line = "【" + title + "】" + content;
                    final int lineId = line.hashCode();
                    if (hashSet.contains(lineId)) {
                        return;
                    }
                    hashSet.add(lineId);
                    //
                    writer.write(line);
                    writer.newLine();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
