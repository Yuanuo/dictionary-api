package org.appxi.dictionary;

import org.appxi.dictionary.io.DictFileTxt;

import java.nio.file.Path;

public class DictFileTxtTest1 {
    public static void main(String[] args) {

        Path oldRepo = Path.of("repo");
        Path newRepo = Path.of("../appxi-dictionary.repo.txt/");
        // 加载词典库
        Dictionaries.def.add(oldRepo);

        try {
            for (Dictionary dictionary : Dictionaries.def.list()) {
//                if (!"阿毗達磨辭典".equals(dictionary.name)){
//                    continue;
//                }
                System.out.println(dictionary.name);
                DictFileTxt.save(newRepo, dictionary.name, dictionary.readEntryTree());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
