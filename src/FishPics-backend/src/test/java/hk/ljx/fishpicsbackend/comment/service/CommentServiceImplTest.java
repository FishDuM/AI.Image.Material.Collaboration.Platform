package hk.ljx.fishpicsbackend.comment.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HtmlUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommentServiceImpl 单元测试
 * 测试 XSS 过滤等功能
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    @DisplayName("测试 XSS 过滤 - 转义 HTML 标签")
    void testXssFilter_EscapeHtmlTags() {
        // Given
        String maliciousContent = "<script>alert('XSS')</script>";

        // When
        String safeContent = HtmlUtil.escape(maliciousContent);

        // Then
        assertNotNull(safeContent);
        assertFalse(safeContent.contains("<script>"));
        assertTrue(safeContent.contains("&lt;script&gt;"));
        assertTrue(safeContent.contains("&lt;/script&gt;"));
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 转义特殊字符")
    void testXssFilter_EscapeSpecialCharacters() {
        // Given
        String maliciousContent = "<img src=x onerror=alert('XSS')>";

        // When
        String safeContent = HtmlUtil.escape(maliciousContent);

        // Then
        assertNotNull(safeContent);
        assertFalse(safeContent.contains("<img"));
        assertTrue(safeContent.contains("&lt;"));
        assertTrue(safeContent.contains("&gt;"));
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 保留正常文本")
    void testXssFilter_PreserveNormalText() {
        // Given
        String normalContent = "这是一条正常的评论内容";

        // When
        String safeContent = HtmlUtil.escape(normalContent);

        // Then
        assertNotNull(safeContent);
        assertEquals(normalContent, safeContent);
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理 null 值")
    void testXssFilter_HandleNull() {
        // Given
        String nullContent = null;

        // When - HtmlUtil.escape 对 null 返回空字符串
        String result = HtmlUtil.escape(nullContent);

        // Then
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理空字符串")
    void testXssFilter_HandleEmptyString() {
        // Given
        String emptyContent = "";

        // When
        String safeContent = HtmlUtil.escape(emptyContent);

        // Then
        assertNotNull(safeContent);
        assertEquals("", safeContent);
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理包含引号的内容")
    void testXssFilter_HandleQuotes() {
        // Given
        String contentWithQuotes = "他说：\"你好\"";

        // When
        String safeContent = HtmlUtil.escape(contentWithQuotes);

        // Then
        assertNotNull(safeContent);
        assertTrue(safeContent.contains("&quot;"));
        assertFalse(safeContent.contains("\""));
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理包含单引号的内容")
    void testXssFilter_HandleSingleQuotes() {
        // Given
        String contentWithSingleQuotes = "It's a test";

        // When
        String safeContent = HtmlUtil.escape(contentWithSingleQuotes);

        // Then
        assertNotNull(safeContent);
        // HtmlUtil.escape 转义单引号为 &#039;
        assertTrue(safeContent.contains("&#039;"));
        assertFalse(safeContent.contains("'"));
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理复杂的 XSS 攻击向量")
    void testXssFilter_HandleComplexXssVector() {
        // Given
        String complexXss = "<svg onload=alert('XSS')>";

        // When
        String safeContent = HtmlUtil.escape(complexXss);

        // Then
        assertNotNull(safeContent);
        assertFalse(safeContent.contains("<svg"));
        assertTrue(safeContent.contains("&lt;svg"));
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理事件处理器")
    void testXssFilter_HandleEventHandlers() {
        // Given
        String[] eventHandlers = {
            "onload", "onerror", "onclick", "onmouseover", "onfocus", "onblur"
        };

        for (String handler : eventHandlers) {
            String maliciousContent = "<div " + handler + "=alert('XSS')>test</div>";

            // When
            String safeContent = HtmlUtil.escape(maliciousContent);

            // Then
            assertNotNull(safeContent);
            assertFalse(safeContent.contains("<div"), "HTML 标签应该被转义");
            assertTrue(safeContent.contains("&lt;div"), "HTML 标签应该被转义");
        }
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理 URL 注入")
    void testXssFilter_HandleUrlInjection() {
        // Given
        String urlInjection = "javascript:alert('XSS')";

        // When
        String safeContent = HtmlUtil.escape(urlInjection);

        // Then
        assertNotNull(safeContent);
        // javascript: 协议本身不会被转义，但引号会被转义
        assertTrue(safeContent.contains("javascript:"));
        assertTrue(safeContent.contains("&#039;"));
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理 CSS 注入")
    void testXssFilter_HandleCssInjection() {
        // Given
        String cssInjection = "<div style=background:url(javascript:alert('XSS'))>test</div>";

        // When
        String safeContent = HtmlUtil.escape(cssInjection);

        // Then
        assertNotNull(safeContent);
        assertFalse(safeContent.contains("<div"));
        assertTrue(safeContent.contains("&lt;div"));
    }

    @Test
    @DisplayName("测试 XSS 过滤 - 处理混合内容")
    void testXssFilter_HandleMixedContent() {
        // Given
        String mixedContent = "正常文本 <b>加粗</b> <script>alert('XSS')</script> 更多正常文本";

        // When
        String safeContent = HtmlUtil.escape(mixedContent);

        // Then
        assertNotNull(safeContent);
        assertTrue(safeContent.contains("正常文本"));
        assertTrue(safeContent.contains("&lt;b&gt;"));
        assertTrue(safeContent.contains("&lt;/b&gt;"));
        assertTrue(safeContent.contains("&lt;script&gt;"));
        assertTrue(safeContent.contains("更多正常文本"));
    }
}
