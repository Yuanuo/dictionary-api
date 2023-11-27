package org.appxi.dictionary;

import java.nio.file.Path;
import java.util.List;

public class TestV2 {
    public static void main(String[] args) {
        final long st = System.currentTimeMillis();

        Dictionaries.def.add(Path.of("repo"));

        System.out.println("discover used time: " + (System.currentTimeMillis() - st));

//        Dictionaries.list().get(0).forEach(entry -> {
//            System.out.println(entry.title());
//            if (entry.isCategory()) {
//                return;
//            }
//
//            System.out.println(entry.contentText());
//            System.out.println();
//        });

        List<String> list = List.of("三藏法数（明·一如等 撰）",
                "中国佛教（中国佛教协会编）",
                "中国大百科全书（摘录）",
                "佛学次第统编（杨卓 编）",
                "佛教人物传 V2.1（中华佛典宝库 编）",
                "佛教器物简述（中华佛典宝库编）",
                "俗语佛源（中国佛教文化研究所编）",
                "唯识名词白话新解（于凌波居士著）",
                "天台教学辞典（释慧岳监修，释会旻主编）",
                "巴英南传佛教辞典（Nyanaponika 编）",
                "法界次第初门（隋·智者大师 撰）",
                "法相辞典（朱芾煌编）",
                "法门名义集（唐·李师政 撰）",
                "祖庭事苑（北宋·陈善卿 编）",
                "禅宗语录辞典 V1.3 （中华佛典宝库编）",
                "禅林象器笺（日·无著道忠撰）",
                "翻译名义集（南宋·法云 著）",
                "藏传佛教辞典（中华佛典宝库编）",
                "释氏要览（北宋·释道诚集）",
                "阅藏知津（蕅益大师著）",
                "阿毗达磨辞典（中华佛典宝库 编）",
                "康熙字典",
                "新华字典（成语）",
                "新华字典（汉字）",
                "新华字典（词语）",
                "翻译名言大集",
                "五译合璧集要",
                "佛学大辞典（丁福保 编）",
                "佛学常见辞汇（陈义孝 编）",
                "南山律学辞典",
                "梵汉词汇表",
                "英汉-汉英-英英佛学词汇（中华佛典宝库 编）"
        );
        Dictionaries.def.search("三毒").forEachRemaining(entry -> {
            if (entry.isCategory()) {
                System.out.println(entry.title());
                return;
            }

            // 读取词条真实内容
//            entry.dictionary().readEntryContentText(entry);
            System.out.println(entry.title()); // 显示词条名称
            System.out.println(entry.contentText()); // 显示词条内容（原始）
        });

        System.out.println("total used time: " + (System.currentTimeMillis() - st));
    }
}
