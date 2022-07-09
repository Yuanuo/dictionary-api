package org.appxi.dictionary;

import java.nio.file.Path;
import java.util.Locale;

public class DictionaryBuilderTest {
    static {
        // 设置默认Locale，此项影响词典的默认显示名称
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    }

    public static void main(String[] args) throws Exception {
        // build
        final Path repo = Path.of("../appxi-dictionary/repo");
        DictionaryBuilder.build(
                new Dictionary(repo.resolve("999.test/dict.properties"))
                        .prop("id", "999.test")
                        .prop("type", "dict"),
                DictionaryBuilder.of("title1", "text1"),
                DictionaryBuilder.of("title2", "text2"),
                DictionaryBuilder.of("title3", "text3"));

        // 设置默认的词典库
        DictionaryApi.setupDefaultApi(repo);

        // 根据词典ID获取词典实例
        DictionaryModel dictionaryModel = DictionaryApi.api().getDictionaryModel("999.test");
        System.out.println(dictionaryModel.size());

        // 列表该词典实例的所有词条（名称）
        dictionaryModel.forEach(entry -> System.out.println(entry.title()));

        // 列出该词典实例的所有词条（名称和内容）
        dictionaryModel.forEach(entry -> {
            // 忽略非真实词条的目录
            if (entry.isCategory()) {
                return;
            }
            // 读取词条真实内容
            dictionaryModel.readEntryContentText(entry);
            System.out.println(entry.title()); // 显示词条名称
            System.out.println(entry.contentText()); // 显示词条内容（原始）
//            System.out.println(DictionaryApi.toTextDocument(entry)); // 显示用于最终展示的词条内容（TEXT）
//            System.out.println(DictionaryApi.toHtmlDocument(entry)); // 显示用于最终展示的词条内容（HTML，内置图片将以base64码嵌入img标签）
        });
    }
}
