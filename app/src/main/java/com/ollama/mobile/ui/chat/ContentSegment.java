package com.ollama.mobile.ui.chat;

public abstract class ContentSegment {

    public static final class Text extends ContentSegment {
        public final String markdown;
        public Text(String markdown) { this.markdown = markdown; }
    }

    public static final class Code extends ContentSegment {
        public final String language;
        public final String code;
        public Code(String language, String code) {
            this.language = language;
            this.code = code;
        }
    }
}
