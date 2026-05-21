package com.ollama.mobile.ui.chat;

import android.content.Context;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;

public class MarkwonProvider {

    private static volatile Markwon instance;

    public static Markwon get(Context context) {
        if (instance == null) {
            synchronized (MarkwonProvider.class) {
                if (instance == null) {
                    instance = Markwon.builder(context.getApplicationContext())
                            .usePlugin(StrikethroughPlugin.create())
                            .build();
                }
            }
        }
        return instance;
    }
}
