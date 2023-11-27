package org.appxi.dictionary;

import org.appxi.util.ext.Compression;
import org.appxi.util.ext.Node;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class DictionaryBuilderTest {
    public static void main(String[] args) throws Exception {
        final Node<Dictionary.Entry> entryRoot = new Node<>(Dictionary.Entry.ofCategory("ROOT"));

        entryRoot.add(Dictionary.Entry.of("title1", "text1"));
        entryRoot.add(Dictionary.Entry.of("title2", "text2"));
        entryRoot.add(Dictionary.Entry.of("title3", "text3"));

        final String name = "test";
        final File file = DictionaryBuilder.build(
                entryRoot, StandardCharsets.UTF_8, Compression.zip, "", name, Path.of("repo11")
        );
        file.deleteOnExit();

        // 设置默认的词典库
        Dictionaries.def.add(file.toPath());

        // 根据词典ID获取词典实例
        Dictionary dictionary = Dictionaries.find(name);
        System.out.println(dictionary.size());

        // 列表该词典实例的所有词条（名称）
        dictionary.forEach(entry -> System.out.println(entry.title()));

        // 列出该词典实例的所有词条（名称和内容）
        dictionary.forEach(entry -> {
            // 忽略非真实词条的目录
            if (entry.isCategory()) {
                return;
            }

            System.out.println(entry.title()); // 显示词条名称
            System.out.println(entry.contentText()); // 显示词条内容（原始）
//            System.out.println(DictionaryHelper.toTextDocument(entry)); // 显示用于最终展示的词条内容（TEXT）
//            System.out.println(DictionaryHelper.toHtmlDocument(entry)); // 显示用于最终展示的词条内容（HTML，内置图片将以base64码嵌入img标签）
        });

        dictionary.readEntryTree().printTree(((entryNode, entry) -> entry.title() + " >>> " + entry.contentText()));
    }
}
