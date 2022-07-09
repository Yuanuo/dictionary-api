package org.appxi.dictionary;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static org.appxi.dictionary.DictionaryModel.CHARSET;

/**
 * 词条
 */
public class DictEntry {
    /**
     * 表示 分类/分组/目录 类型
     */
    public static final int TYPE_CATEGORY = 0;
    /**
     * 表示 具有内容的词条 类型
     */
    public static final int TYPE_ITEM = 1;

    /**
     * 便于访问的当前词条数据端口
     */
    public DictionaryModel model;

    /**
     * 词条ID，亦即position
     */
    public int id;
    /**
     * 父级ID，亦即position
     */
    public int pid = -1;
    /**
     * 词条词性/类型，扩展用
     */
    public short type;
    /**
     * 词条名称的字符（非字节）数，取值自title.length()
     */
    public short titleLength;

    /**
     * 内部用，词条名称
     */
    Object title;
    /**
     * 内部用，表示内容相关。若当前为Category类型时则为子级列表数；否则为内容数据的position位置。
     *
     * @see #title()
     */
    int contentMark;
    /**
     * 内部用，表示内容。若当前为Category类型时表示子级列表ID（int[]）；否则为内容数据（String）。
     *
     * @see #contentList()
     * @see #contentText()
     */
    Object content;

    public final boolean isCategory() {
        return this.type == TYPE_CATEGORY;
    }

    /**
     * 便于访问的当前词条所属词典
     */
    public Dictionary dictionary() {
        return this.model.dictionary;
    }

    /**
     * 获取词条名称
     *
     * @return 词条名称
     */
    public String title() {
        if (this.title instanceof byte[] bytes) {
            this.title = new String(bytes, CHARSET);
        }
        return (String) this.title;
    }

    /**
     * 获取词条内容，
     * 仅在 {@link #isCategory()} 为false时有意义
     *
     * @return 词条内容
     */
    public String contentText() {
        if (this.content instanceof byte[] bytes) {
            this.content = new String(bytes, CHARSET);
        }
        return (String) this.content;
    }

    /**
     * 获取子级列表ID，
     * 仅在 {@link #isCategory()} 为true时有意义
     *
     * @return 子级列表ID
     */
    public int[] contentList() {
        return (int[]) this.content;
    }

    /**
     * 具有分数属性的词条，通常仅用于搜索时的结果词条
     */
    public static class Scored extends DictEntry implements Comparable<Scored> {
        public int score;

        public Scored setScore(int score) {
            this.score = score;
            return this;
        }

        public int compareTo(Scored other) {
            return other.score - this.score;
        }
    }

    /**
     * 具有层级关系的词条，通常仅用于构建词典时的输入数据
     */
    public static class Node extends DictEntry {
        public final List<Node> children = new LinkedList<>();

        public Node(int type, String title, String content) {
            this.type = (short) type;
            this.title = title;
            this.content = content;
        }

        public Node ensureChild(String title) {
            Node result = this.children.stream()
                    .filter(v -> Objects.equals(title, v.title())).findFirst()
                    .orElse(null);
            if (null == result) {
                this.children.add(result = of(title, null));
            }
            return result;
        }

        public Node ensureChildren(String path) {
            Node item = this;
            for (String title : path.split("/")) {
                Node child = item.ensureChild(title);
                child.type = TYPE_CATEGORY;
                item = child;
            }
            return item;
        }

        public static Node of(String title, String content) {
            return new Node(TYPE_ITEM, title, content);
        }

        public static Node ofCategory(String title) {
            return new Node(TYPE_CATEGORY, title, null);
        }
    }
}
