package org.appxi.dictionary;

import java.nio.file.Path;
import java.util.List;

public class DictionaryTest {
    public static void main(String[] args) {
        // 加载词典库
        Dictionaries.def.add(Path.of("repo"));

        // 列出默认的词典库列表
        Dictionaries.def.list().forEach(System.out::println);

        // 根据词典ID获取词条实例
        Dictionary dictionary = Dictionaries.find("佛学辞典汇集");
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
            System.out.println(DictionaryHelper.toTextDocument(entry)); // 显示用于最终展示的词条内容（TEXT）
            System.out.println(DictionaryHelper.toHtmlDocument(entry)); // 显示用于最终展示的词条内容（HTML，内置图片将以base64码嵌入img标签）
        });

        // 在"所有"词典中查询
        // 查询以“三毒”字样开始的词条
        Dictionaries.def.search("三毒");
        // 查询以“三毒”字样开始的词条
        Dictionaries.def.search("三毒*");
        // 查询以“三毒”字样结尾的词条
        Dictionaries.def.search("*三毒");
        // 查询存在“三毒”字样的词条
        Dictionaries.def.search("*三毒*");
        // 精确查询词条名称为“三毒”的词条
        Dictionaries.def.search("\"三毒\"");
        //

        // 在"单一"词典中查询
        // Dictionaries.getDictionary("佛学辞典汇集").search("三毒", ...)

        // 在"某些"词典中查询
        Dictionaries.def.search("三毒", dict -> List.of("id1", "id2", "id3").contains(dict.id));

        // 列出查询结果
        Dictionaries.find("佛学辞典汇集").search("三毒").forEachRemaining(entry -> {
            System.out.println(entry.title()); // 显示词条名称
            System.out.println(entry.contentText()); // 显示词条内容（原始）
        });
    }
}
