package com.qinglian.fitness.common;

import org.springframework.http.HttpStatus;

/** 为内容搜索统一校验关键词，并生成使用 ! 转义符的 SQL LIKE 包含匹配参数。 */
public final class LiteralSearch {

    private static final int MAX_QUERY_LENGTH = 80;

    private LiteralSearch() {
    }

    /**
     * 生成参数绑定用的包含匹配模式，英文字母大小写由 SQL 的 lower 统一处理。
     *
     * @param query 可省略；去除首尾空白后最多 80 个字符，空白输入表示不按关键词筛选
     * @return 可空的 LIKE 参数；%、_、! 只按字面匹配，SQL 必须声明 escape '!'
     */
    public static String containsPattern(String query) {
        if (query == null || query.isBlank()) return null;
        String normalized = query.trim();
        if (normalized.codePointCount(0, normalized.length()) > MAX_QUERY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "搜索词不能超过 80 个字符");
        }
        // 必须先转义 ! 自身，再转义用户输入中的通配符，避免扩大数据库匹配范围。
        return "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
