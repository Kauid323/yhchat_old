package com.nago8.chat.old.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.nago8.chat.old.ImagePreviewActivity;
import com.nago8.chat.old.R;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 HTML 富文本消息片段高保真解析并渲染为 Android 原生 Material UI 组件
 */
public class HtmlNativeRenderer {

    private static final String TAG = "HtmlNativeRenderer";

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "<(div|span|details|summary|img|p|table|blockquote|pre|code|h[1-6]|ul|ol|li|a|b|i|strong|em|font|hr|br)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final LruCache<String, HtmlNode> HTML_AST_CACHE = new LruCache<>(256);

    /**
     * 判断文本是否包含 HTML 标签需要进行原生组件渲染
     */
    public static boolean isHtml(String text) {
        if (text == null) return false;
        int len = text.length();
        if (len < 3) return false;
        int openIdx = text.indexOf('<');
        if (openIdx == -1 || openIdx >= len - 2) return false;
        if (text.indexOf('>', openIdx + 1) == -1) return false;
        return HTML_TAG_PATTERN.matcher(text).find();
    }

    /**
     * 极速去除 HTML 标签，用于引用预览和日志纯文本显示
     */
    public static String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        if (!html.contains("<")) return html;
        return html.replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&middot;", "·")
                .trim();
    }

    public static boolean isDarkMode(Context context) {
        if (context == null) return false;
        if (PrefUtils.isDarkModeEnabled(context)) return true;
        int nightMode = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * 解析 HTML 并渲染为 Android 原生 View 树
     */
    public static View render(Context context, String html, boolean isMine, boolean isEdited, int maxBubbleWidth) {
        if (context == null) return new View(context);

        boolean isDark = isDarkMode(context);
        HtmlNode rootNode = parseHtml(html);

        LinearLayout rootContainer = new LinearLayout(context);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Map<String, String> baseInheritedStyles = new HashMap<>();
        if (isMine) {
            baseInheritedStyles.put("color", "#FFFFFF");
        } else {
            baseInheritedStyles.put("color", isDark ? "#E0E0E0" : "#111111");
        }
        baseInheritedStyles.put("font-size", "14px");

        renderChildren(context, rootNode, baseInheritedStyles, rootContainer, isMine, isDark, maxBubbleWidth);

        if (rootContainer.getChildCount() == 1) {
            View single = rootContainer.getChildAt(0);
            rootContainer.removeView(single);
            return single;
        }

        return rootContainer;
    }

    /**
     * 直接向目标容器渲染 HTML 原生组件，消除多余的包裹 Layout
     */
    public static void renderInto(Context context, ViewGroup container, String html, boolean isMine, boolean isEdited, int maxBubbleWidth) {
        if (context == null || container == null) return;
        boolean isDark = isDarkMode(context);
        HtmlNode rootNode = parseHtml(html);

        Map<String, String> baseInheritedStyles = new HashMap<>();
        if (isMine) {
            baseInheritedStyles.put("color", "#FFFFFF");
        } else {
            baseInheritedStyles.put("color", isDark ? "#E0E0E0" : "#111111");
        }
        baseInheritedStyles.put("font-size", "14px");

        renderChildren(context, rootNode, baseInheritedStyles, container, isMine, isDark, maxBubbleWidth);
    }

    // ======================================================================================
    // HTML Node Data Structure & Tokenizer
    // ======================================================================================

    public static class HtmlNode {
        public String tag = "";
        public String text = "";
        public Map<String, String> attributes = new LinkedHashMap<>();
        public Map<String, String> styles = new LinkedHashMap<>();
        public List<HtmlNode> children = new ArrayList<>();
        public boolean isSelfClosing = false;

        public boolean isTextNode() {
            return "text".equalsIgnoreCase(tag) || (!TextUtils.isEmpty(text) && children.isEmpty() && tag.isEmpty());
        }

        public String getStyle(String key) {
            return styles.get(key.toLowerCase(Locale.ROOT));
        }

        public String getAttribute(String key) {
            return attributes.get(key.toLowerCase(Locale.ROOT));
        }
    }

    private static boolean isSelfClosingTag(String tag) {
        if (tag == null) return false;
        String t = tag.toLowerCase(Locale.ROOT);
        return "img".equals(t) || "br".equals(t) || "hr".equals(t) || "input".equals(t) || "meta".equals(t) || "link".equals(t);
    }

    public static HtmlNode parseHtml(String html) {
        if (html == null || html.isEmpty()) {
            HtmlNode root = new HtmlNode();
            root.tag = "root";
            return root;
        }

        synchronized (HTML_AST_CACHE) {
            HtmlNode cached = HTML_AST_CACHE.get(html);
            if (cached != null) {
                return cached;
            }
        }

        HtmlNode root = new HtmlNode();
        root.tag = "root";

        Deque<HtmlNode> stack = new ArrayDeque<>();
        stack.push(root);

        int i = 0;
        int len = html.length();

        while (i < len) {
            char c = html.charAt(i);
            if (c == '<') {
                int closeBracket = html.indexOf('>', i);
                if (closeBracket == -1) {
                    String rest = html.substring(i);
                    addTextNode(stack.peek(), rest);
                    break;
                }

                String rawTagContent = html.substring(i + 1, closeBracket).trim();
                i = closeBracket + 1;

                if (rawTagContent.startsWith("!--")) {
                    int endComment = html.indexOf("-->", i - 1);
                    if (endComment != -1) {
                        i = endComment + 3;
                    }
                    continue;
                }

                if (rawTagContent.startsWith("/")) {
                    String closeTagName = rawTagContent.substring(1).trim().toLowerCase(Locale.ROOT);
                    while (stack.size() > 1) {
                        HtmlNode top = stack.pop();
                        if (top.tag.equalsIgnoreCase(closeTagName)) {
                            break;
                        }
                    }
                } else {
                    boolean selfClosing = rawTagContent.endsWith("/") || isSelfClosingTag(extractTagName(rawTagContent));
                    String tagContent = rawTagContent;
                    if (tagContent.endsWith("/")) {
                        tagContent = tagContent.substring(0, tagContent.length() - 1).trim();
                    }

                    HtmlNode node = parseTag(tagContent);
                    node.isSelfClosing = selfClosing;

                    if (stack.peek() != null) {
                        stack.peek().children.add(node);
                    }

                    if (!selfClosing) {
                        stack.push(node);
                    }
                }
            } else {
                int nextTag = html.indexOf('<', i);
                String textSegment;
                if (nextTag == -1) {
                    textSegment = html.substring(i);
                    i = len;
                } else {
                    textSegment = html.substring(i, nextTag);
                    i = nextTag;
                }
                if (!textSegment.isEmpty()) {
                    addTextNode(stack.peek(), textSegment);
                }
            }
        }

        synchronized (HTML_AST_CACHE) {
            HTML_AST_CACHE.put(html, root);
        }

        return root;
    }

    private static String extractTagName(String tagContent) {
        int spaceIdx = -1;
        for (int i = 0; i < tagContent.length(); i++) {
            char ch = tagContent.charAt(i);
            if (Character.isWhitespace(ch) || ch == '/' || ch == '>') {
                spaceIdx = i;
                break;
            }
        }
        if (spaceIdx != -1) {
            return tagContent.substring(0, spaceIdx).trim().toLowerCase(Locale.ROOT);
        }
        return tagContent.trim().toLowerCase(Locale.ROOT);
    }

    private static HtmlNode parseTag(String tagContent) {
        HtmlNode node = new HtmlNode();
        String tagName = extractTagName(tagContent);
        node.tag = tagName;

        Pattern attrPattern = Pattern.compile("([a-zA-Z0-9_-]+)\\s*=\\s*([\"'])(.*?)\\2|([a-zA-Z0-9_-]+)\\s*=\\s*([^\\s>]+)|([a-zA-Z0-9_-]+)");
        Matcher matcher = attrPattern.matcher(tagContent.substring(tagName.length()));

        while (matcher.find()) {
            String key = null;
            String value = "";
            if (matcher.group(1) != null) {
                key = matcher.group(1).toLowerCase(Locale.ROOT);
                value = matcher.group(3);
            } else if (matcher.group(4) != null) {
                key = matcher.group(4).toLowerCase(Locale.ROOT);
                value = matcher.group(5);
            } else if (matcher.group(6) != null) {
                key = matcher.group(6).toLowerCase(Locale.ROOT);
                value = "";
            }

            if (key != null) {
                node.attributes.put(key, value);
                if ("style".equals(key)) {
                    parseInlineStyles(value, node.styles);
                }
            }
        }

        return node;
    }

    private static void parseInlineStyles(String styleStr, Map<String, String> styles) {
        if (TextUtils.isEmpty(styleStr)) return;
        String[] rules = styleStr.split(";");
        for (String rule : rules) {
            String trimmed = rule.trim();
            if (trimmed.isEmpty()) continue;
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx != -1) {
                String k = trimmed.substring(0, colonIdx).trim().toLowerCase(Locale.ROOT);
                String v = trimmed.substring(colonIdx + 1).trim();
                styles.put(k, v);
            }
        }
    }

    private static void addTextNode(HtmlNode parent, String text) {
        if (parent == null || TextUtils.isEmpty(text)) return;
        String unescaped = unescapeHtml(text);
        if (unescaped.isEmpty()) return;

        if (!parent.children.isEmpty()) {
            HtmlNode lastChild = parent.children.get(parent.children.size() - 1);
            if (lastChild.isTextNode()) {
                lastChild.text += unescaped;
                return;
            }
        }
        HtmlNode textNode = new HtmlNode();
        textNode.tag = "text";
        textNode.text = unescaped;
        parent.children.add(textNode);
    }

    private static String unescapeHtml(String input) {
        if (input == null) return "";
        if (input.indexOf('&') == -1) return input;
        return input.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&middot;", "·")
                .replace("&bull;", "•");
    }

    // ======================================================================================
    // View Tree Rendering & CSS Inheritance
    // ======================================================================================

    /**
     * 根据 CSS 标准，仅继承文本属性（color, font-size, font-weight, line-height, text-align, word-break 等），
     * 绝不继承 display: flex, margin, padding, background, border 等容器盒模型属性！
     */
    private static Map<String, String> getInheritableStyles(Map<String, String> parentStyles) {
        Map<String, String> inheritable = new HashMap<>();
        if (parentStyles == null || parentStyles.isEmpty()) return inheritable;

        String[] inheritableKeys = {"color", "font-size", "font-weight", "font-style", "font-family", "line-height", "text-align", "word-break"};
        for (String key : inheritableKeys) {
            String val = parentStyles.get(key);
            if (val != null) {
                inheritable.put(key, val);
            }
        }
        return inheritable;
    }

    private static boolean isInlineNode(HtmlNode node) {
        if (node == null) return false;
        if (node.isTextNode()) return true;

        String tag = node.tag.toLowerCase(Locale.ROOT);
        if ("details".equals(tag) || "summary".equals(tag) || "hr".equals(tag) || "table".equals(tag)
                || "ul".equals(tag) || "ol".equals(tag) || "br".equals(tag)) {
            return false;
        }

        String display = node.getStyle("display");
        if ("flex".equalsIgnoreCase(display) || "inline-flex".equalsIgnoreCase(display)
                || "block".equalsIgnoreCase(display) || "grid".equalsIgnoreCase(display)) {
            return false;
        }

        if ("div".equals(tag) || "p".equals(tag) || "blockquote".equals(tag)
                || "section".equals(tag) || "article".equals(tag) || "header".equals(tag) || "footer".equals(tag)) {
            return false;
        }

        return !"img".equals(tag);

        // span, b, strong, i, em, u, s, del, a, font, code, small, sub, sup, text
    }

    public static boolean areAllChildrenInline(HtmlNode node) {
        if (node == null || node.children == null || node.children.isEmpty()) return true;
        for (HtmlNode child : node.children) {
            if (!isInlineNode(child)) {
                return false;
            }
            if (!areAllChildrenInline(child)) {
                return false;
            }
        }
        return true;
    }

    private static void renderChildren(Context context, HtmlNode parentNode, Map<String, String> inheritedStyles, ViewGroup container, boolean isMine, boolean isDark, int maxBubbleWidth) {
        if (parentNode == null || parentNode.children == null || parentNode.children.isEmpty()) return;

        boolean isFlexRow = container instanceof LinearLayout && ((LinearLayout) container).getOrientation() == LinearLayout.HORIZONTAL;
        int gapDp = 0;
        if (isFlexRow) {
            String gapStr = parentNode.getStyle("gap");
            if (!TextUtils.isEmpty(gapStr)) {
                gapDp = parseDimension(context, gapStr, 0);
            }
        }

        Map<String, String> passDownStyles = getInheritableStyles(inheritedStyles);

        int i = 0;
        int total = parentNode.children.size();
        while (i < total) {
            HtmlNode child = parentNode.children.get(i);
            if (isInlineNode(child)) {
                List<HtmlNode> inlineGroup = new ArrayList<>();
                while (i < total && isInlineNode(parentNode.children.get(i))) {
                    inlineGroup.add(parentNode.children.get(i));
                    i++;
                }
                View inlineTextView = renderInlineGroup(context, inlineGroup, passDownStyles, isMine, isDark, maxBubbleWidth);
                if (inlineTextView != null) {
                    if (isFlexRow && gapDp > 0 && container.getChildCount() > 0) {
                        LinearLayout.LayoutParams lp = (inlineTextView.getLayoutParams() instanceof LinearLayout.LayoutParams)
                                ? (LinearLayout.LayoutParams) inlineTextView.getLayoutParams()
                                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.leftMargin = gapDp;
                        inlineTextView.setLayoutParams(lp);
                    }
                    container.addView(inlineTextView);
                }
            } else {
                View childView = renderNode(context, child, passDownStyles, isMine, isDark, maxBubbleWidth);
                if (childView != null) {
                    if (isFlexRow && gapDp > 0 && container.getChildCount() > 0) {
                        ViewGroup.LayoutParams lp = childView.getLayoutParams();
                        if (lp instanceof LinearLayout.LayoutParams) {
                            ((LinearLayout.LayoutParams) lp).leftMargin = gapDp;
                            childView.setLayoutParams(lp);
                        } else {
                            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            nlp.leftMargin = gapDp;
                            childView.setLayoutParams(nlp);
                        }
                    }
                    container.addView(childView);
                }
                i++;
            }
        }
    }

    @Nullable
    private static View renderNode(Context context, HtmlNode node, Map<String, String> inheritedStyles, boolean isMine, boolean isDark, int maxBubbleWidth) {
        if (node == null) return null;

        Map<String, String> effectiveStyles = new HashMap<>(inheritedStyles);
        effectiveStyles.putAll(node.styles);

        String tag = node.tag.toLowerCase(Locale.ROOT);

        // 1. <details> & <summary>
        if ("details".equals(tag)) {
            return renderDetailsNode(context, node, effectiveStyles, isMine, isDark, maxBubbleWidth);
        }

        // 2. <img>
        if ("img".equals(tag)) {
            return renderImageNode(context, node, effectiveStyles, maxBubbleWidth);
        }

        // 3. <hr>
        if ("hr".equals(tag)) {
            View hr = new View(context);
            hr.setBackgroundColor(ContextCompat.getColor(context, R.color.divider_color));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
            lp.topMargin = dp(context, 4);
            lp.bottomMargin = dp(context, 4);
            hr.setLayoutParams(lp);
            return hr;
        }

        // 4. <br>
        if ("br".equals(tag)) {
            View spacer = new View(context);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 4)));
            return spacer;
        }

        // 5. Flex container (display: flex)
        String display = node.getStyle("display");
        String flexDirection = node.getStyle("flex-direction");
        boolean isFlex = "flex".equalsIgnoreCase(display) || "inline-flex".equalsIgnoreCase(display);
        boolean isRow = isFlex && !"column".equalsIgnoreCase(flexDirection);

        if (isFlex && isRow) {
            LinearLayout flexLayout = new LinearLayout(context);
            flexLayout.setOrientation(LinearLayout.HORIZONTAL);

            String alignItems = node.getStyle("align-items");
            if ("center".equalsIgnoreCase(alignItems)) {
                flexLayout.setGravity(Gravity.CENTER_VERTICAL);
            } else if ("flex-end".equalsIgnoreCase(alignItems)) {
                flexLayout.setGravity(Gravity.BOTTOM);
            } else {
                flexLayout.setGravity(Gravity.TOP);
            }

            applyCommonContainerStyles(context, flexLayout, node.styles, isDark);
            renderChildren(context, node, effectiveStyles, flexLayout, isMine, isDark, maxBubbleWidth);
            return flexLayout;
        }

        // 6. 检查是否有左侧边框（如引用块 border-left: 3px solid #2563eb）
        String borderLeftStr = node.getStyle("border-left");
        if (!TextUtils.isEmpty(borderLeftStr)) {
            return renderQuoteBlockWithBorder(context, node, effectiveStyles, isMine, isDark, maxBubbleWidth);
        }

        // 7. 如果该容器的所有子节点均为行内元素，直接扁平化渲染为单一 TextView（0 额外 Layout 嵌套）
        if (areAllChildrenInline(node)) {
            TextView singleTextView = renderInlineBlockTextView(context, node, effectiveStyles, isMine, isDark, maxBubbleWidth);
            if (singleTextView != null) {
                return singleTextView;
            }
        }

        // 8. 如果是一个透明且无边框/内边距的单纯包裹 Block，并且只有一个子节点，直接穿透渲染该子节点并合并外层样式
        if (!hasContainerStyling(node.styles) && node.children.size() == 1) {
            HtmlNode singleChild = node.children.get(0);
            Map<String, String> mergedChildStyles = new HashMap<>(effectiveStyles);
            mergedChildStyles.putAll(singleChild.styles);
            View childView = renderNode(context, singleChild, mergedChildStyles, isMine, isDark, maxBubbleWidth);
            if (childView != null) {
                applyCommonContainerStyles(context, childView, node.styles, isDark);
                return childView;
            }
        }

        // 9. 多子节点或包含非行内元素的 Block 容器（垂直 LinearLayout）
        LinearLayout blockContainer = new LinearLayout(context);
        blockContainer.setOrientation(LinearLayout.VERTICAL);
        applyCommonContainerStyles(context, blockContainer, node.styles, isDark);
        renderChildren(context, node, effectiveStyles, blockContainer, isMine, isDark, maxBubbleWidth);
        return blockContainer;
    }

    private static boolean hasContainerStyling(Map<String, String> styles) {
        if (styles == null || styles.isEmpty()) return false;
        return styles.containsKey("background") || styles.containsKey("background-color")
                || styles.containsKey("padding") || styles.containsKey("padding-top")
                || styles.containsKey("padding-bottom") || styles.containsKey("padding-left")
                || styles.containsKey("padding-right") || styles.containsKey("border")
                || styles.containsKey("border-left") || styles.containsKey("border-radius");
    }

    // ======================================================================================
    // Specialized Node Renderers & Custom Drawables
    // ======================================================================================

    public static class QuoteBorderDrawable extends Drawable {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float cornerRadius;
        private final float borderWidth;

        public QuoteBorderDrawable(int bgColor, int borderColor, float cornerRadius, float borderWidth) {
            this.bgPaint.setColor(bgColor);
            this.bgPaint.setStyle(Paint.Style.FILL);
            this.borderPaint.setColor(borderColor);
            this.borderPaint.setStyle(Paint.Style.FILL);
            this.cornerRadius = cornerRadius;
            this.borderWidth = borderWidth;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            rect.set(bounds);
            if (bgPaint.getColor() != 0) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);
            }
            if (borderWidth > 0 && borderPaint.getColor() != 0) {
                RectF stripeRect = new RectF(bounds.left, bounds.top, bounds.left + borderWidth, bounds.bottom);
                canvas.drawRoundRect(stripeRect, cornerRadius, cornerRadius, borderPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            bgPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            bgPaint.setColorFilter(colorFilter);
            borderPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * 渲染带左侧彩色线条的引用块（如 border-left: 3px solid #2563eb）
     * 优先单层扁平化单一 TextView + QuoteBorderDrawable，完全消除嵌套 Layout 和 weight=1f 带来的多次测量卡顿！
     */
    private static View renderQuoteBlockWithBorder(Context context, HtmlNode node, Map<String, String> effectiveStyles, boolean isMine, boolean isDark, int maxBubbleWidth) {
        String borderLeftStr = node.getStyle("border-left");
        int borderWidth = dp(context, 3);
        int borderColor = isDark ? 0xFF60A5FA : 0xFF2563EB;

        if (!TextUtils.isEmpty(borderLeftStr)) {
            String[] parts = borderLeftStr.trim().split("\\s+");
            for (String p : parts) {
                if (p.endsWith("px")) {
                    borderWidth = parseDimension(context, p, borderWidth);
                } else if (p.startsWith("#") || p.startsWith("rgb")) {
                    borderColor = parseColor(context, p, isDark, borderColor);
                }
            }
        }

        String bgStr = node.getStyle("background");
        if (TextUtils.isEmpty(bgStr)) bgStr = node.getStyle("background-color");
        int bgColor = isDark ? 0xFF1E2530 : 0xFFEDF2F7;
        if (!TextUtils.isEmpty(bgStr)) {
            bgColor = parseColor(context, bgStr, isDark, bgColor);
        }

        String borderRadiusStr = node.getStyle("border-radius");
        int cornerRadius = !TextUtils.isEmpty(borderRadiusStr) ? parseDimension(context, borderRadiusStr, dp(context, 3)) : dp(context, 3);

        if (areAllChildrenInline(node)) {
            TextView quoteTv = new TextView(context);
            SpannableStringBuilder builder = new SpannableStringBuilder();
            appendNodeSpannable(context, node, effectiveStyles, builder, isMine, isDark);

            int emojiSize = dp(context, 18);
            CharSequence displayText = FengEmojiRenderer.apply(context, builder, emojiSize);
            quoteTv.setText(displayText, TextView.BufferType.SPANNABLE);

            String fontSizeStr = effectiveStyles.get("font-size");
            float fontSizeSp = parseFontSize(fontSizeStr, 12f);
            quoteTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);

            String colorStr = effectiveStyles.get("color");
            int defaultColor = isDark ? 0xFF94A3B8 : 0xFF4A5568;
            int textColor = parseColor(context, colorStr, isDark, defaultColor);
            quoteTv.setTextColor(textColor);

            String lineHeightStr = effectiveStyles.get("line-height");
            if (!TextUtils.isEmpty(lineHeightStr)) {
                try {
                    if (lineHeightStr.endsWith("px")) {
                        int lh = parseDimension(context, lineHeightStr, 0);
                        if (lh > 0) TextViewCompat.setLineHeight(quoteTv, lh);
                    } else {
                        float multiplier = Float.parseFloat(lineHeightStr);
                        quoteTv.setLineSpacing(0, Math.max(1.0f, multiplier));
                    }
                } catch (Exception ignored) {}
            }

            applyCommonContainerStyles(context, quoteTv, node.styles, isDark);

            quoteTv.setBackground(new QuoteBorderDrawable(bgColor, borderColor, cornerRadius, borderWidth));
            int currentPadLeft = quoteTv.getPaddingLeft();
            quoteTv.setPadding(currentPadLeft + borderWidth + dp(context, 4), quoteTv.getPaddingTop(), quoteTv.getPaddingRight(), quoteTv.getPaddingBottom());

            return quoteTv;
        }

        LinearLayout quoteCard = new LinearLayout(context);
        quoteCard.setOrientation(LinearLayout.VERTICAL);
        applyCommonContainerStyles(context, quoteCard, node.styles, isDark);

        // 设置高效单层背景，包含左侧边框条
        quoteCard.setBackground(new QuoteBorderDrawable(bgColor, borderColor, cornerRadius, borderWidth));

        // 在已有 padding 上增加左侧边框及间距
        int currentPadLeft = quoteCard.getPaddingLeft();
        quoteCard.setPadding(currentPadLeft + borderWidth + dp(context, 4), quoteCard.getPaddingTop(), quoteCard.getPaddingRight(), quoteCard.getPaddingBottom());

        renderChildren(context, node, effectiveStyles, quoteCard, isMine, isDark, maxBubbleWidth);
        return quoteCard;
    }

    private static TextView renderInlineBlockTextView(Context context, HtmlNode blockNode, Map<String, String> effectiveStyles, boolean isMine, boolean isDark, int maxBubbleWidth) {
        if (blockNode == null) return null;

        TextView textView = new TextView(context);
        SpannableStringBuilder builder = new SpannableStringBuilder();

        appendNodeSpannable(context, blockNode, effectiveStyles, builder, isMine, isDark);

        if (builder.length() == 0) {
            return null;
        }

        int emojiSize = dp(context, 18);
        CharSequence displayText = FengEmojiRenderer.apply(context, builder, emojiSize);
        textView.setText(displayText, TextView.BufferType.SPANNABLE);

        // 字体大小
        String fontSizeStr = effectiveStyles.get("font-size");
        float fontSizeSp = parseFontSize(fontSizeStr, 13f);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);

        // 文字颜色
        String colorStr = effectiveStyles.get("color");
        int defaultColor = isMine ? 0xFFFFFFFF : (isDark ? 0xFFE0E0E0 : 0xFF111111);
        int textColor = parseColor(context, colorStr, isDark, defaultColor);
        textView.setTextColor(textColor);

        // 行高
        String lineHeightStr = effectiveStyles.get("line-height");
        if (!TextUtils.isEmpty(lineHeightStr)) {
            try {
                if (lineHeightStr.endsWith("px")) {
                    int lh = parseDimension(context, lineHeightStr, 0);
                    if (lh > 0) TextViewCompat.setLineHeight(textView, lh);
                } else {
                    float multiplier = Float.parseFloat(lineHeightStr);
                    textView.setLineSpacing(0, Math.max(1.0f, multiplier));
                }
            } catch (Exception ignored) {}
        }

        applyCommonContainerStyles(context, textView, blockNode.styles, isDark);

        return textView;
    }

    private static View renderDetailsNode(Context context, HtmlNode detailsNode, Map<String, String> effectiveStyles, boolean isMine, boolean isDark, int maxBubbleWidth) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        applyCommonContainerStyles(context, container, detailsNode.styles, isDark);

        HtmlNode summaryNode = null;
        List<HtmlNode> contentChildren = new ArrayList<>();
        for (HtmlNode child : detailsNode.children) {
            if ("summary".equalsIgnoreCase(child.tag) && summaryNode == null) {
                summaryNode = child;
            } else {
                contentChildren.add(child);
            }
        }

        // 1. Summary Header (扁平化单一 TextView，提升渲染效率)
        String summaryColorStr = summaryNode != null ? summaryNode.getStyle("color") : null;
        int summaryColor = parseColor(context, summaryColorStr, isDark, ContextCompat.getColor(context, R.color.app_primary));

        String summaryFontSizeStr = summaryNode != null ? summaryNode.getStyle("font-size") : null;
        float summaryFontSize = parseFontSize(summaryFontSizeStr, 11f);
        String summaryTitle = extractNodeText(summaryNode, context.getString(R.string.action_details));

        TextView tvHeader = new TextView(context);
        tvHeader.setText("▶ " + summaryTitle);
        tvHeader.setTextColor(summaryColor);
        tvHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, summaryFontSize);
        tvHeader.setTypeface(null, Typeface.BOLD);
        tvHeader.setClickable(true);
        tvHeader.setFocusable(true);
        tvHeader.setBackgroundResource(R.drawable.bg_item_ripple);
        tvHeader.setPadding(0, dp(context, 2), 0, dp(context, 2));

        container.addView(tvHeader);

        // 2. Details Content Container (initially GONE)
        LinearLayout detailsContent = new LinearLayout(context);
        detailsContent.setOrientation(LinearLayout.VERTICAL);
        detailsContent.setVisibility(View.GONE);
        detailsContent.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));

        HtmlNode tempContentParent = new HtmlNode();
        tempContentParent.children.addAll(contentChildren);
        renderChildren(context, tempContentParent, effectiveStyles, detailsContent, isMine, isDark, maxBubbleWidth);

        container.addView(detailsContent);

        // 3. Toggle Animation and Arrow Switch
        tvHeader.setOnClickListener(v -> {
            boolean isExpanding = (detailsContent.getVisibility() == View.GONE);
            if (isExpanding) {
                detailsContent.setVisibility(View.VISIBLE);
                detailsContent.setAlpha(0f);
                detailsContent.animate().alpha(1f).setDuration(180).start();
                tvHeader.setText("▼ " + summaryTitle);
            } else {
                detailsContent.setVisibility(View.GONE);
                tvHeader.setText("▶ " + summaryTitle);
            }
        });

        return container;
    }

    private static View renderImageNode(Context context, HtmlNode node, Map<String, String> effectiveStyles, int maxBubbleWidth) {
        String src = node.getAttribute("src");
        if (TextUtils.isEmpty(src)) return new View(context);

        String finalSrc = src.trim();
        if (finalSrc.startsWith("//")) {
            finalSrc = "https:" + finalSrc;
        } else if (finalSrc.startsWith("/")) {
            finalSrc = "https://chat-img.jwznb.com" + finalSrc;
        }

        String widthStr = node.getStyle("width");
        if (TextUtils.isEmpty(widthStr)) widthStr = node.getAttribute("width");

        String heightStr = node.getStyle("height");
        if (TextUtils.isEmpty(heightStr)) heightStr = node.getAttribute("height");

        int widthPx = parseDimension(context, widthStr, -1);
        int heightPx = parseDimension(context, heightStr, -1);

        String borderRadiusStr = node.getStyle("border-radius");
        boolean isCircle = "50%".equals(borderRadiusStr) || "100%".equals(borderRadiusStr)
                || (widthPx > 0 && widthPx == heightPx && borderRadiusStr != null && borderRadiusStr.contains("px") && parseDimension(context, borderRadiusStr, 0) >= widthPx / 2);
        int cornerRadius = !isCircle && !TextUtils.isEmpty(borderRadiusStr) ? parseDimension(context, borderRadiusStr, 0) : 0;

        // 检查用户是否在设置中开启了“不加载 HTML 图片”
        boolean disablePreload = PrefUtils.isDisableHtmlImagePreload(context);
        if (disablePreload) {
            return renderImagePlaceholder(context, finalSrc, widthPx, heightPx, isCircle, cornerRadius, maxBubbleWidth);
        }

        ImageView imageView = new ImageView(context);
        imageView.setAdjustViewBounds(true);

        if (widthPx > 0 && heightPx > 0) {
            imageView.setLayoutParams(new LinearLayout.LayoutParams(widthPx, heightPx));
        } else if (widthPx > 0) {
            imageView.setLayoutParams(new LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
            imageView.setMaxWidth(widthPx);
        } else {
            int defaultMax = Math.min(maxBubbleWidth, dp(context, 180));
            imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            imageView.setMaxWidth(defaultMax);
        }

        int reqWidth = widthPx > 0 ? widthPx : Math.min(maxBubbleWidth > 0 ? maxBubbleWidth : dp(context, 200), dp(context, 260));
        int reqHeight = heightPx > 0 ? heightPx : reqWidth;

        // 对七牛云或jwznb图片使用智能采样参数，大幅削减大图网络传输和内存开销
        String loadUrl = ImageUtils.appendQiniuParam(finalSrc, reqWidth, reqHeight);

        try {
            RequestOptions options = new RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(reqWidth, reqHeight)
                    .dontAnimate();

            if (isCircle) {
                options = options.transform(new CircleCrop());
            } else if (cornerRadius > 0) {
                options = options.transform(new CenterCrop(), new RoundedCorners(cornerRadius));
            }

            // 支持 jwznb 图片统一携带 Referer 请求头加载
            Object modelToLoad;
            if (loadUrl.contains("jwznb.com")) {
                modelToLoad = new GlideUrl(loadUrl, new LazyHeaders.Builder()
                        .addHeader("Referer", "https://myapp.jwznb.com")
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .build());
            } else {
                modelToLoad = loadUrl;
            }

            Glide.with(context)
                    .load(modelToLoad)
                    .apply(options)
                    .into(imageView);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load HTML image: " + loadUrl, e);
        }

        final String previewUrl = finalSrc;
        if (widthPx <= 0 || widthPx > dp(context, 48)) {
            imageView.setClickable(true);
            imageView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(context, ImagePreviewActivity.class);
                    intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, previewUrl);
                    context.startActivity(intent);
                } catch (Exception ignored) {}
            });
        }

        return imageView;
    }

    /**
     * 当开启“不加载 HTML 图片”时渲染优雅占位，点击可直接打开图片预览器查看大图
     */
    private static View renderImagePlaceholder(Context context, String finalSrc, int widthPx, int heightPx, boolean isCircle, int cornerRadius, int maxBubbleWidth) {
        boolean isSmallAvatar = isCircle || (widthPx > 0 && widthPx <= dp(context, 32));

        if (isSmallAvatar) {
            int size = (widthPx > 0) ? widthPx : dp(context, 24);
            ImageView avatarPlaceholder = new ImageView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            avatarPlaceholder.setLayoutParams(lp);
            avatarPlaceholder.setImageResource(R.drawable.ic_image);
            avatarPlaceholder.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            avatarPlaceholder.setPadding(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(isDarkMode(context) ? 0xFF374151 : 0xFFE5E7EB);
            if (isCircle) {
                bg.setShape(GradientDrawable.OVAL);
            } else {
                bg.setCornerRadius(cornerRadius > 0 ? cornerRadius : dp(context, 4));
            }
            avatarPlaceholder.setBackground(bg);
            avatarPlaceholder.setColorFilter(isDarkMode(context) ? 0xFF9CA3AF : 0xFF6B7280);

            avatarPlaceholder.setClickable(true);
            avatarPlaceholder.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(context, ImagePreviewActivity.class);
                    intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, finalSrc);
                    context.startActivity(intent);
                } catch (Exception ignored) {}
            });
            return avatarPlaceholder;
        }

        // 大图 / 消息内容图片占位卡片
        LinearLayout placeholder = new LinearLayout(context);
        placeholder.setOrientation(LinearLayout.HORIZONTAL);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setClickable(true);
        placeholder.setFocusable(true);

        int padH = dp(context, 10);
        int padV = dp(context, 6);
        placeholder.setPadding(padH, padV, padH, padV);

        if (widthPx > 0 && heightPx > 0) {
            placeholder.setLayoutParams(new LinearLayout.LayoutParams(widthPx, heightPx));
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(context, 2);
            lp.bottomMargin = dp(context, 2);
            placeholder.setLayoutParams(lp);
            placeholder.setMinimumWidth(dp(context, 100));
            placeholder.setMinimumHeight(dp(context, 36));
        }

        boolean isDark = isDarkMode(context);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(isDark ? 0xFF2A2E33 : 0xFFF1F5F9);
        cardBg.setCornerRadius(cornerRadius > 0 ? cornerRadius : dp(context, 6));
        cardBg.setStroke(dp(context, 1), isDark ? 0xFF3F454C : 0xFFCBD5E1);
        placeholder.setBackground(cardBg);

        ImageView icon = new ImageView(context);
        int iconSize = dp(context, 16);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.rightMargin = dp(context, 6);
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_image);
        int primaryColor = ContextCompat.getColor(context, R.color.app_primary);
        icon.setColorFilter(primaryColor);
        placeholder.addView(icon);

        TextView tvText = new TextView(context);
        tvText.setText(context.getString(R.string.html_image_tap_to_view));
        tvText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tvText.setTextColor(primaryColor);
        placeholder.addView(tvText);

        placeholder.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, ImagePreviewActivity.class);
                intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, finalSrc);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        });

        return placeholder;
    }

    /**
     * 将一组连续的行内节点（span, b, strong, i, em, a, text）合并渲染为一个完整富文本 TextView
     */
    private static View renderInlineGroup(Context context, List<HtmlNode> inlineNodes, Map<String, String> baseStyles, boolean isMine, boolean isDark, int maxBubbleWidth) {
        if (inlineNodes == null || inlineNodes.isEmpty()) return null;

        TextView textView = new TextView(context);
        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (HtmlNode node : inlineNodes) {
            appendNodeSpannable(context, node, baseStyles, builder, isMine, isDark);
        }

        int emojiSize = dp(context, 18);
        CharSequence displayText = FengEmojiRenderer.apply(context, builder, emojiSize);
        textView.setText(displayText, TextView.BufferType.SPANNABLE);

        // 字体大小与颜色基准
        String fontSizeStr = baseStyles.get("font-size");
        float fontSizeSp = parseFontSize(fontSizeStr, 13f);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);

        String colorStr = baseStyles.get("color");
        int defaultColor = isMine ? 0xFFFFFFFF : (isDark ? 0xFFE0E0E0 : 0xFF111111);
        int textColor = parseColor(context, colorStr, isDark, defaultColor);
        textView.setTextColor(textColor);

        // 行高
        String lineHeightStr = baseStyles.get("line-height");
        if (!TextUtils.isEmpty(lineHeightStr)) {
            try {
                if (lineHeightStr.endsWith("px")) {
                    int lh = parseDimension(context, lineHeightStr, 0);
                    if (lh > 0) TextViewCompat.setLineHeight(textView, lh);
                } else {
                    float multiplier = Float.parseFloat(lineHeightStr);
                    textView.setLineSpacing(0, Math.max(1.0f, multiplier));
                }
            } catch (Exception ignored) {}
        }

        // 如果单个节点含有内边距或外边距
        if (inlineNodes.size() == 1) {
            applyCommonContainerStyles(context, textView, inlineNodes.get(0).styles, isDark);
        }

        return textView;
    }

    private static void appendNodeSpannable(Context context, HtmlNode node, Map<String, String> inheritedStyles, SpannableStringBuilder ssb, boolean isMine, boolean isDark) {
        if (node == null) return;

        Map<String, String> effectiveStyles = new HashMap<>(inheritedStyles);
        effectiveStyles.putAll(node.styles);

        int start = ssb.length();

        if (node.isTextNode()) {
            ssb.append(node.text);
        } else {
            if (!TextUtils.isEmpty(node.text)) {
                ssb.append(node.text);
            }
            for (HtmlNode child : node.children) {
                appendNodeSpannable(context, child, effectiveStyles, ssb, isMine, isDark);
            }
        }

        int end = ssb.length();
        if (end <= start) return;

        // 1. Color (仅当当前节点显式定义了 color 时施加 Span，避免跨层覆盖与重复分配)
        String colorStr = node.styles.get("color");
        if (!TextUtils.isEmpty(colorStr)) {
            int defaultColor = isMine ? 0xFFFFFFFF : (isDark ? 0xFFE0E0E0 : 0xFF111111);
            int color = parseColor(context, colorStr, isDark, defaultColor);
            ssb.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 2. Font Size (仅当当前节点显式定义了 font-size 时施加 Span)
        String fontSizeStr = node.styles.get("font-size");
        if (!TextUtils.isEmpty(fontSizeStr)) {
            float sizeSp = parseFontSize(fontSizeStr, 13f);
            int sizePx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, context.getResources().getDisplayMetrics()));
            ssb.setSpan(new AbsoluteSizeSpan(sizePx), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 3. Bold & Italic
        String fontWeightStr = node.styles.get("font-weight");
        String fontStyleStr = node.styles.get("font-style");
        boolean isBold = "bold".equalsIgnoreCase(fontWeightStr) || "600".equals(fontWeightStr)
                || "700".equals(fontWeightStr) || "800".equals(fontWeightStr) || "900".equals(fontWeightStr)
                || "b".equalsIgnoreCase(node.tag) || "strong".equalsIgnoreCase(node.tag);
        boolean isItalic = "italic".equalsIgnoreCase(fontStyleStr) || "i".equalsIgnoreCase(node.tag) || "em".equalsIgnoreCase(node.tag);

        if (isBold && isItalic) {
            ssb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (isBold) {
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (isItalic) {
            ssb.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 4. Underline & Strikethrough
        if ("u".equalsIgnoreCase(node.tag)) {
            ssb.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if ("s".equalsIgnoreCase(node.tag) || "del".equalsIgnoreCase(node.tag)) {
            ssb.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 5. <a> Link Clickable
        if ("a".equalsIgnoreCase(node.tag)) {
            final String href = node.getAttribute("href");
            if (!TextUtils.isEmpty(href)) {
                ssb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        if (!InternalLinkUtils.handleUrl(context, href)) {
                            try {
                                String openUrl = href;
                                if (!openUrl.startsWith("http://") && !openUrl.startsWith("https://") && !openUrl.startsWith("yunhu://")) {
                                    openUrl = "http://" + openUrl;
                                }
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(openUrl));
                                context.startActivity(intent);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to open link: " + href, e);
                            }
                        }
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(true);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static String extractNodeText(HtmlNode node, String fallback) {
        if (node == null) return fallback;
        if (!TextUtils.isEmpty(node.text)) return node.text;

        StringBuilder sb = new StringBuilder();
        collectTextRecursive(node, sb);
        String res = sb.toString().trim();
        return !res.isEmpty() ? res : fallback;
    }

    private static void collectTextRecursive(HtmlNode node, StringBuilder sb) {
        if (node == null) return;
        if (!TextUtils.isEmpty(node.text)) {
            sb.append(node.text);
        }
        for (HtmlNode child : node.children) {
            collectTextRecursive(child, sb);
        }
    }

    // ======================================================================================
    // Common Style Utilities
    // ======================================================================================

    private static void applyCommonContainerStyles(Context context, View view, Map<String, String> styles, boolean isDark) {
        if (styles == null || styles.isEmpty()) return;

        // Margins
        String marginStr = styles.get("margin");
        String marginTopStr = styles.get("margin-top");
        String marginBottomStr = styles.get("margin-bottom");
        String marginLeftStr = styles.get("margin-left");
        String marginRightStr = styles.get("margin-right");

        boolean hasMargin = !TextUtils.isEmpty(marginStr) || !TextUtils.isEmpty(marginTopStr)
                || !TextUtils.isEmpty(marginBottomStr) || !TextUtils.isEmpty(marginLeftStr) || !TextUtils.isEmpty(marginRightStr);

        if (hasMargin) {
            LinearLayout.LayoutParams params = (view.getLayoutParams() instanceof LinearLayout.LayoutParams)
                    ? (LinearLayout.LayoutParams) view.getLayoutParams()
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            if (!TextUtils.isEmpty(marginStr)) {
                int m = parseDimension(context, marginStr, 0);
                params.setMargins(m, m, m, m);
            }
            if (!TextUtils.isEmpty(marginTopStr)) {
                params.topMargin = parseDimension(context, marginTopStr, 0);
            }
            if (!TextUtils.isEmpty(marginBottomStr)) {
                params.bottomMargin = parseDimension(context, marginBottomStr, 0);
            }
            if (!TextUtils.isEmpty(marginLeftStr)) {
                params.leftMargin = parseDimension(context, marginLeftStr, 0);
            }
            if (!TextUtils.isEmpty(marginRightStr)) {
                params.rightMargin = parseDimension(context, marginRightStr, 0);
            }

            view.setLayoutParams(params);
        }

        // Padding
        String paddingStr = styles.get("padding");
        String paddingTopStr = styles.get("padding-top");
        String paddingBottomStr = styles.get("padding-bottom");
        String paddingLeftStr = styles.get("padding-left");
        String paddingRightStr = styles.get("padding-right");

        boolean hasPadding = !TextUtils.isEmpty(paddingStr) || !TextUtils.isEmpty(paddingTopStr)
                || !TextUtils.isEmpty(paddingBottomStr) || !TextUtils.isEmpty(paddingLeftStr) || !TextUtils.isEmpty(paddingRightStr);

        if (hasPadding) {
            int padLeft = view.getPaddingLeft();
            int padTop = view.getPaddingTop();
            int padRight = view.getPaddingRight();
            int padBottom = view.getPaddingBottom();

            if (!TextUtils.isEmpty(paddingStr)) {
                String[] parts = paddingStr.trim().split("\\s+");
                if (parts.length == 1) {
                    int p = parseDimension(context, parts[0], 0);
                    padLeft = padTop = padRight = padBottom = p;
                } else if (parts.length == 2) {
                    int py = parseDimension(context, parts[0], 0);
                    int px = parseDimension(context, parts[1], 0);
                    padTop = padBottom = py;
                    padLeft = padRight = px;
                } else if (parts.length == 4) {
                    padTop = parseDimension(context, parts[0], 0);
                    padRight = parseDimension(context, parts[1], 0);
                    padBottom = parseDimension(context, parts[2], 0);
                    padLeft = parseDimension(context, parts[3], 0);
                }
            }

            if (!TextUtils.isEmpty(paddingTopStr)) padTop = parseDimension(context, paddingTopStr, padTop);
            if (!TextUtils.isEmpty(paddingBottomStr)) padBottom = parseDimension(context, paddingBottomStr, padBottom);
            if (!TextUtils.isEmpty(paddingLeftStr)) padLeft = parseDimension(context, paddingLeftStr, padLeft);
            if (!TextUtils.isEmpty(paddingRightStr)) padRight = parseDimension(context, paddingRightStr, padRight);

            view.setPadding(padLeft, padTop, padRight, padBottom);
        }

        // Background & Border Radius
        String bgStr = styles.get("background");
        if (TextUtils.isEmpty(bgStr)) bgStr = styles.get("background-color");
        String borderRadiusStr = styles.get("border-radius");
        String borderStr = styles.get("border");

        if (!TextUtils.isEmpty(bgStr) || !TextUtils.isEmpty(borderRadiusStr) || !TextUtils.isEmpty(borderStr)) {
            GradientDrawable drawable = new GradientDrawable();

            if (!TextUtils.isEmpty(bgStr)) {
                int parsedBg = parseColor(context, bgStr, isDark, Color.TRANSPARENT);
                if (isDark) {
                    if (parsedBg == 0xFFF8F9FA || parsedBg == 0xFFFFFFFF || parsedBg == 0xFFF1F3F5) {
                        parsedBg = 0xFF282C30;
                    } else if (parsedBg == 0xFFEDF2F7 || parsedBg == 0xFFEDF2F8) {
                        parsedBg = 0xFF1E2530;
                    }
                }
                drawable.setColor(parsedBg);
            }

            if (!TextUtils.isEmpty(borderRadiusStr)) {
                int radius = parseDimension(context, borderRadiusStr, 0);
                drawable.setCornerRadius(radius);
            }

            if (!TextUtils.isEmpty(borderStr)) {
                drawable.setStroke(dp(context, 1), isDark ? 0x33FFFFFF : 0x1A000000);
            }

            view.setBackground(drawable);
        }
    }

    private static int parseDimension(Context context, String dimStr, int defaultVal) {
        if (TextUtils.isEmpty(dimStr)) return defaultVal;
        String s = dimStr.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("px")) {
                float val = Float.parseFloat(s.substring(0, s.length() - 2).trim());
                return dp(context, Math.round(val));
            } else if (s.endsWith("dp") || s.endsWith("dip")) {
                float val = Float.parseFloat(s.replaceAll("[^0-9.]", "").trim());
                return dp(context, Math.round(val));
            } else if (s.endsWith("em") || s.endsWith("rem")) {
                float val = Float.parseFloat(s.replaceAll("[^0-9.]", "").trim());
                return dp(context, Math.round(val * 14));
            } else {
                float val = Float.parseFloat(s.replaceAll("[^0-9.]", "").trim());
                return dp(context, Math.round(val));
            }
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static float parseFontSize(String sizeStr, float defaultSp) {
        if (TextUtils.isEmpty(sizeStr)) return defaultSp;
        String s = sizeStr.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("px") || s.endsWith("sp") || s.endsWith("pt")) {
                return Float.parseFloat(s.replaceAll("[^0-9.]", "").trim());
            } else if (s.endsWith("em") || s.endsWith("rem")) {
                float multiplier = Float.parseFloat(s.replaceAll("[^0-9.]", "").trim());
                return defaultSp * multiplier;
            } else {
                return Float.parseFloat(s.replaceAll("[^0-9.]", "").trim());
            }
        } catch (Exception e) {
            return defaultSp;
        }
    }

    private static int parseColor(Context context, String colorStr, boolean isDark, int defaultColor) {
        if (TextUtils.isEmpty(colorStr)) return defaultColor;
        String s = colorStr.trim().toLowerCase(Locale.ROOT);

        try {
            if (s.startsWith("#")) {
                if (s.length() == 4) { // #RGB -> #RRGGBB
                    char r = s.charAt(1);
                    char g = s.charAt(2);
                    char b = s.charAt(3);
                    s = "#" + r + r + g + g + b + b;
                }
                int parsed = Color.parseColor(s);
                return adaptColorForDarkTheme(parsed, isDark);
            } else if (s.startsWith("rgb")) {
                Pattern rgbPattern = Pattern.compile("rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)");
                Matcher m = rgbPattern.matcher(s);
                if (m.find()) {
                    int r = Integer.parseInt(m.group(1));
                    int g = Integer.parseInt(m.group(2));
                    int b = Integer.parseInt(m.group(3));
                    int parsed = Color.rgb(r, g, b);
                    return adaptColorForDarkTheme(parsed, isDark);
                }
            } else {
                switch (s) {
                    case "transparent": return Color.TRANSPARENT;
                    case "black": return isDark ? 0xFFE0E0E0 : Color.BLACK;
                    case "white": return isDark ? 0xFF282C30 : Color.WHITE;
                    case "red": return 0xFFEF4444;
                    case "green": return 0xFF10B981;
                    case "blue": return isDark ? 0xFF60A5FA : 0xFF2563EB;
                    case "gray":
                    case "grey": return isDark ? 0xFF94A3B8 : 0xFF6B7280;
                }
            }
        } catch (Exception ignored) {}

        return defaultColor;
    }

    private static int adaptColorForDarkTheme(int color, boolean isDark) {
        if (!isDark) return color;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        if (r < 60 && g < 60 && b < 60) {
            return 0xFFF1F5F9;
        } else if (r < 150 && g < 150 && b < 150 && Math.abs(r - g) < 20 && Math.abs(g - b) < 20) {
            return 0xFF94A3B8;
        }

        return color;
    }

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
