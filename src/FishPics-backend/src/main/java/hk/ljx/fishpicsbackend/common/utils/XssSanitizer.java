package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class XssSanitizer {

    private XssSanitizer() {}

    // 去掉所有 HTML 标签，只留纯文本
    public static String clean(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, Safelist.none());
    }

    // 保留 a/b/i/strong/br 等基础标签
    public static String cleanRelaxed(String input) {
        if (input == null) return null;
        return Jsoup.clean(input, Safelist.basic());
    }

    public static String cleanIfNotBlank(String value) {
        return StrUtil.isBlank(value) ? value : clean(value);
    }

}
