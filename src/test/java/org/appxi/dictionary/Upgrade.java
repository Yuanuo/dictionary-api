package org.appxi.dictionary;

import org.appxi.util.ext.Node;

import java.nio.file.Path;

public class Upgrade {
    public static void main(String[] args) {
        Path oldRepo = Path.of("C:\\Devx\\appxi-dictionary.d1");
        Path newRepo = Path.of("C:\\Devx\\appxi-dictionary.dd");
        // 加载词典库
        Dictionaries.def.add(oldRepo);

        try {
            for (Dictionary dictionary : Dictionaries.def.list()) {
                System.out.println(dictionary.name + "  " + dictionary.file);
                final Node<Dictionary.Entry> entryTree = dictionary.readEntryTree();

                entryTree.traverse((depth, node, entry) -> {
                    if (node.hasChildren()) {
                        return;
                    }
                    String text = entry.contentText();
                    if (text.isBlank()) {
                        System.out.println(entry.title());
                    }
                });

//                DictionaryBuilder.build(entryTree,
//                        dictionary.charset(), Compression.zip,
//                        dictionary.metadata(), dictionary.name, newRepo);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
