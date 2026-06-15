package hk.ljx.fishpicsbackend.common.utils;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * 存储 XSS 防御
 * 入库前用 Jsoup 清除 HTML 标签/危险属性,只保留纯文本
 */
public final class XssSanitizer {

    private XssSanitizer() {}

    /**
     * 清理 HTML 标签,只保留纯文本
     * 例: "<script>alert(1)</script>hello" -> "hello"
     * 例: "<img src=x onerror=alert(1)>" -> ""
     */
    public static String clean(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, Safelist.none());
    }

    /**
     * 宽松版:保留 a / b / i / strong / br 等基础标签
     */
    public static String cleanRelaxed(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, Safelist.basic());
    }

    /**
     * 校验是否含危险 HTML — 用于日志/告警(不阻断,只记录)
     */
    public static boolean containsDangerousHtml(String input) {
        if (input == null) return false;
        String cleaned = clean(input);
        return !cleaned.equals(input);
    }
}
