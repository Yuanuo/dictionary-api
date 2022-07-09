# AppXI Dictionary
## 自用的、易用的，高性能的词典实现方案！

**特别说明：部分词典数据来自FoDict2佛典词库！**

## 简易API使用方法：

```java
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

public class DictionaryTest {
    static {
        // 设置默认的词典库
        DictionaryApi.setupDefaultApi(Path.of("../appxi-dictionary/repo"));
        // 或者，同时使用多个库
        // DictionaryApi managedApi1 = new DictionaryApi(Path.of("../appxi-dictionary/repo1"));
        // DictionaryApi managedApi2 = new DictionaryApi(Path.of("../appxi-dictionary/repo2"));

        // 设置默认Locale，此项影响词典的默认显示名称
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    }

    public static void main(String[] args) {
        // 列出默认的词典库列表
        DictionaryApi.api().forEach(System.out::println);

        // 根据词典ID获取词条实例
        DictionaryModel dictionaryModel = DictionaryApi.api().getDictionaryModel("007.EC-CE-EE-j");
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
            entry.model.readEntryContentText(entry);
            System.out.println(entry.title()); // 显示词条名称
            System.out.println(entry.contentText()); // 显示词条内容（原始）
            System.out.println(DictionaryHelper.toTextDocument(entry)); // 显示用于最终展示的词条内容（TEXT）
            System.out.println(DictionaryHelper.toHtmlDocument(entry)); // 显示用于最终展示的词条内容（HTML，内置图片将以base64码嵌入img标签）
        });

        // 在"所有"词典中查询
        // 查询以“三毒”字样开始的词条
        DictionaryApi.api().search("三毒", null);
        // 查询以“三毒”字样开始的词条
        DictionaryApi.api().search("三毒*", null);
        // 查询以“三毒”字样结尾的词条
        DictionaryApi.api().search("*三毒", null);
        // 查询存在“三毒”字样的词条
        DictionaryApi.api().search("*三毒*", null);
        // 精确查询词条名称为“三毒”的词条
        DictionaryApi.api().search("\"三毒\"", null);
        //

        // 在"单一"词典中查询
        // DictionaryApi.api().getDictionaryModel("007.EC-CE-EE-j").search("三毒", ...)

        // 在"某些"词典中查询
        DictionaryApi.api().search("三毒", dict -> Arrays.asList("id1", "id2", "id3").contains(dict.id));

        // 列出查询结果
        dictionaryModel.search("三毒").forEachRemaining(entry -> {
            // 读取词条真实内容
            entry.model.readEntryContentText(entry);
            System.out.println(entry.title()); // 显示词条名称
            System.out.println(entry.contentText()); // 显示词条内容（原始）
        });
    }
}

```
