package org.appxi.dictionary;

/**
 * 词条匹配模式
 */
public enum MatchType {
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
     */
    public static MatchType detect(StringBuilder keywords) {
        MatchType matchType = MatchType.TitleStartsWith;
        if (keywords.isEmpty()) {
            return matchType;
        }

        // 表达式至少有两个字符
        if (keywords.length() > 1) {
            if (keywords.charAt(0) == '"' && keywords.charAt(keywords.length() - 1) == '"' // 以英文双引号包含
                || keywords.charAt(0) == '“' && keywords.charAt(keywords.length() - 1) == '”' // 以中文双引号包含
                || keywords.charAt(0) == '\'' && keywords.charAt(keywords.length() - 1) == '\'' // 英文单引号包含
                || keywords.charAt(0) == '‘' && keywords.charAt(keywords.length() - 1) == '’' // 以中文单引号包含
            ) {
                matchType = MatchType.TitleEquals;
                keywords.deleteCharAt(0);
                keywords.deleteCharAt(keywords.length() - 1);
            } else if (keywords.charAt(0) == '*' && keywords.charAt(keywords.length() - 1) == '*') { // 以英文星号包含
                matchType = MatchType.TitleContains;
                keywords.deleteCharAt(0);
                keywords.deleteCharAt(keywords.length() - 1);
            } else if (keywords.charAt(0) == '*') { // 以英文星号开始
                matchType = MatchType.TitleEndsWith;
                keywords.deleteCharAt(0);
            } else if (keywords.charAt(keywords.length() - 1) == '*') { // 以英文星号结尾
                keywords.deleteCharAt(keywords.length() - 1);
            }
        } else if (!keywords.isEmpty() && keywords.charAt(0) == '*') { // 仅英文星号
            keywords.deleteCharAt(0);
        }
        return matchType;
    }
}