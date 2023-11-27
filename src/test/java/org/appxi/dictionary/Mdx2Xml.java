package org.appxi.dictionary;

import org.appxi.dictionary.io.DictFileMdx;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class Mdx2Xml {
    public static void main(String[] args) throws Exception {
        for (Path path : Files.list(Paths.get("E:\\tome\\dict\\GoldenDict.d"))
                .filter(path -> path.getFileName().toString().endsWith(".mdx")).toList()) {
            if (!path.toString().contains("禪林象器箋"))
                continue;
            System.out.println(path);

            final String dictName = path.getFileName().toString().replace(".mdx", "");

            final Element dictXmlBody = Jsoup.parse("").body();

            try {
                final DictFileMdx.MdxFile md = new DictFileMdx.MdxFile(path.toFile());
                dictXmlBody.attr("n", md.getTitle());

                for (int i = 0; i < md.getNumberEntries(); i++) {
                    final String word0 = md.getEntryAt(i).strip();
                    if (word0.equalsIgnoreCase("160"))
                        System.out.println();
                    //
                    if (word0.contains("製作說明") || word0.contains("總目錄")
                        || word0.startsWith(dictName) || word0.contains(dictName)) {
                        System.err.println("SKIP WORD: " + word0);
                        continue;
                    }

                    String word1 = word0
                            .replace("[", "")
                            .replace("]", "")
                            .replaceAll("[【】“”\"'‘’\u0092]+", "")
                            //
                            .strip();
                    if (word1.startsWith(dictName) || word1.contains(dictName)) {
                        System.err.println("SKIP WORD: " + word1);
                        continue;
                    }

                    if (word1.startsWith(":")) {
                        System.err.println("FAKE WORD: " + word1);
                        continue;
                    }

                    //
                    String text = md.getRecordAt(i)
//                            .replace("<a>", "")
//                            .replace("</a>", "")
                            .strip();
                    if (text.isBlank()) {
                        System.out.println();
                    }
                    text = text.replace(" class=MsoNormal", "")
                            .replace("class=MsoNormal", "")
                            .replace(" style='font-family:\"Microsoft Himalaya\"'", "")
                            .replace("style='font-family:\"Microsoft Himalaya\"'", "")
                            .replace("<style type=\"text/css\">p{margin:0}</style>", "")
                            .replace("<link rel=\"stylesheet\" type=\"text/css\" href=\"sf_ecce.css\"/>", "")
                            .replace("<span style=\"float:right;\"><A HREF=\"entry://00 總目錄\">總目錄</a></span><hr color=#AA8A57>", "\r\n")
                            .replace("<a href=\"entry://:about,首页\">返回首页</a>", "")
                            .replaceAll("^(<br>)+|(<br>)+$", "")
                            .replaceAll("<br><br>From：.*<br>(\r\n|[\r\n])", "")
                            .replaceAll("<br>", "\r\n")
                            //
                            .strip();


                    if (text.startsWith(word0 + "　") || text.startsWith(word0 + " ")
                        || text.startsWith(word0 + "\r\n") || text.startsWith(word0 + "\r") || text.startsWith(word0 + "\n")) {
                        text = text.substring(word0.length()).strip();
                    }

                    if (text.startsWith(word1 + "　") || text.startsWith(word1 + " ")
                        || text.startsWith(word1 + "\r\n") || text.startsWith(word1 + "\r") || text.startsWith(word1 + "\n")) {
                        text = text.substring(word1.length()).strip();
                    }

                    if (text.contains(">上一条</a>") || text.contains(">下一条</a>")) {
                        int idx = text.lastIndexOf("\n");
                        if (idx != -1) {
                            String str = text.substring(idx);
                            if (str.contains(">上一条</a>") || str.contains(">下一条</a>")) {
                                text = text.substring(0, idx).strip();
                            }
                        }
                    }
                    text = text.replaceAll("(\r\n|[\r\n])+", "\r\n")
                            .replaceAll("<span(\r\n|[\r\n])", "<span ")
//                            .replace("\\n　", "\r\n")
//                            .replace("\\n", "\r\n")
                            .replaceAll("　　", "\r\n");
                    text = text.lines()
                            .map(String::strip)
                            .collect(Collectors.joining("\r\n"))
////                    ;
////                    text = Jsoup.parse(text).body().html()
                            .replaceAll("([^.!?\"'。！？“”‘’])(\r\n|[\r\n])", "$1 ")
                            .replace(" style='font-family:\"微软雅黑\",\"sans-serif\"'", "")
                            .replace(" style='font-family:\"Calibri\",\"sans-serif\"'", "")
                            .replace("<span class=\"word\"><span class=\"return-phrase\"><span class=\"l\"><span class=\"i\">" + word0 + "</span></span></span><span class=\"phone\"></span>", "")
                    ;
                    //
                    dictXmlBody.appendText("\n")
                            .appendElement("e")
                            .attr("n", word1)
                            .appendCDATA(text);
                }

                dictXmlBody.appendText("\n");
//                saveXml(dictXmlBody.ownerDocument(), Path.of(path + "-111.xml"));

//                System.exit(1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static boolean saveXml(Document document, Path targetFile) {
        return saveDocument(document, targetFile, true);
    }

    public static boolean saveHtml(Document document, Path targetFile) {
        return saveDocument(document, targetFile, false);
    }

    public static boolean saveDocument(Document document, Path targetFile, boolean xmlMode) {
        try {
            Files.createDirectories(targetFile.getParent());
            if (xmlMode)
                document.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.xml);
            Files.writeString(targetFile, document.outerHtml());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
