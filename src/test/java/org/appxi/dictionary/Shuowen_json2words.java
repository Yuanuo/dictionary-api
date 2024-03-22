package org.appxi.dictionary;

import org.appxi.holder.RawHolder;
import org.appxi.util.FileHelper;
import org.appxi.util.ext.Compression;
import org.appxi.util.ext.Node;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Shuowen_json2words {
    public static void main(String[] args) throws Exception {
        final Node<Dictionary.Entry> entryRoot = new Node<>(Dictionary.Entry.ofCategory("ROOT"));
        final RawHolder<Node<Dictionary.Entry>> entryGroup = new RawHolder<>();

        Files.list(Path.of("C:\\Devz\\shuowen\\Codes\\data"))
                .sorted(Comparator.comparingInt(path -> Integer.parseInt(path.getFileName().toString().replace(".json", ""))))
                .forEach(path -> {
                    JSONObject json = new JSONObject(FileHelper.readString(path));

                    String word = json.getString("wordhead");
                    String radical = json.getString("radical");
                    if (entryGroup.value == null || !(entryGroup.value.value.title.equals(radical))) {
                        entryGroup.value = entryRoot.add(Dictionary.Entry.ofCategory(radical));
                    }

                    List<String> data = new ArrayList<>();
                    data.add("【拼音】：" + json.optString("pinyin_full", json.optString("pinyin")));
                    data.add("【词义】：" + json.getString("explanation"));

                    data.add("【段注】：");
                    JSONArray duan_notes = json.getJSONArray("duan_notes");
                    for (int i=0;i< duan_notes.length();i++) {
                        JSONObject itm = duan_notes.getJSONObject(i);
                        data.add(itm.getString("explanation"));
                        data.add(itm.getString("note"));
                        data.add("\n");
                    }
                    //
                    entryGroup.value.add(Dictionary.Entry.of(word, String.join("\\\\n", data)));
                });

        final String name = "说文解字（简易版）";
        final File file = DictionaryBuilder.build(
                entryRoot, StandardCharsets.UTF_8, Compression.zip, "", name, Path.of("C:\\Devx\\appxi-dictionary.dd\\2")
        );

    }
}
