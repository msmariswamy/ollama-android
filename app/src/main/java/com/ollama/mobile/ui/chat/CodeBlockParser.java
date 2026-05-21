package com.ollama.mobile.ui.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a markdown string into alternating TextSegment / CodeSegment objects.
 * Handles unclosed fences (e.g., during streaming) by treating the trailing
 * content as a code segment with whatever code has arrived so far.
 */
public class CodeBlockParser {

    // Matches: opening fence line (``` or ~~~, optional language tag), code body, closing fence line
    // Group 1 = language tag (may be empty), Group 2 = code body
    private static final Pattern FENCE_PATTERN = Pattern.compile(
            "```([^\\n`]*)\\n(.*?)```",
            Pattern.DOTALL
    );

    // Matches an unclosed opening fence at the end of the string (streaming in progress)
    private static final Pattern OPEN_FENCE_PATTERN = Pattern.compile(
            "```([^\\n`]*)\\n(.*)$",
            Pattern.DOTALL
    );

    public static List<ContentSegment> parse(String markdown) {
        List<ContentSegment> segments = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return segments;

        Matcher m = FENCE_PATTERN.matcher(markdown);
        int lastEnd = 0;

        while (m.find()) {
            // Text before this code block
            String before = markdown.substring(lastEnd, m.start());
            if (!before.trim().isEmpty()) {
                segments.add(new ContentSegment.Text(before));
            }
            String lang = m.group(1) != null ? m.group(1).trim() : "";
            String code = m.group(2) != null ? m.group(2) : "";
            // Strip trailing newline from code body
            if (code.endsWith("\n")) code = code.substring(0, code.length() - 1);
            segments.add(new ContentSegment.Code(lang, code));
            lastEnd = m.end();
        }

        // Remaining text after all closed fences
        String tail = markdown.substring(lastEnd);

        // Check for an unclosed fence (streaming case)
        Matcher openM = OPEN_FENCE_PATTERN.matcher(tail);
        if (openM.find()) {
            String beforeFence = tail.substring(0, openM.start());
            if (!beforeFence.trim().isEmpty()) {
                segments.add(new ContentSegment.Text(beforeFence));
            }
            String lang = openM.group(1) != null ? openM.group(1).trim() : "";
            String code = openM.group(2) != null ? openM.group(2) : "";
            segments.add(new ContentSegment.Code(lang, code));
        } else if (!tail.trim().isEmpty()) {
            segments.add(new ContentSegment.Text(tail));
        }

        return segments;
    }
}
