package org.appxi.dictionary;

/**
 * 词条匹配模式
 */
public enum DictEntryExpr {
    /**
     * Title完全匹配
     */
    TitleEquals,
    /**
     * Title包含**
     */
    TitleContains,
    /**
     * Title以**起始
     */
    TitleStartsWith,
    /**
     * Title以**结尾
     */
    TitleEndsWith;

    /**
     * 根据输入keywords动态检测匹配模式，仅针对查询Title的规则；
     *
     * @param keywords 输入
     * @return DictEntryExpr
     */
    public static DictEntryExpr detectForTitle(StringBuilder keywords) {
        DictEntryExpr entryExpr = DictEntryExpr.TitleStartsWith;
        if (keywords.isEmpty()) {
            return entryExpr;
        }

        if (keywords.length() > 2) {
            if (keywords.charAt(0) == '"' && keywords.charAt(keywords.length() - 1) == '"'
                || keywords.charAt(0) == '“' && keywords.charAt(keywords.length() - 1) == '”') {
                entryExpr = DictEntryExpr.TitleEquals;
                keywords.deleteCharAt(0);
                keywords.deleteCharAt(keywords.length() - 1);
            } else if (keywords.charAt(0) == '*' && keywords.charAt(keywords.length() - 1) == '*') {
                entryExpr = DictEntryExpr.TitleContains;
                keywords.deleteCharAt(0);
                keywords.deleteCharAt(keywords.length() - 1);
            } else if (keywords.charAt(0) == '*') {
                entryExpr = DictEntryExpr.TitleEndsWith;
                keywords.deleteCharAt(0);
            } else if (keywords.charAt(keywords.length() - 1) == '*') {
                keywords.deleteCharAt(keywords.length() - 1);
            }
        } else if ("*".equals(keywords.toString())) {
            keywords.deleteCharAt(0);
        }
        return entryExpr;
    }
}