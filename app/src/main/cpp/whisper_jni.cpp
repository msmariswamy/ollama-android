#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ollama_mobile_whisper_WhisperContext_nativeInit(JNIEnv* env, jclass, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context* ctx = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) LOGE("Failed to load whisper model");
    else LOGI("Whisper model loaded");
    return (jlong)(intptr_t)ctx;
}

JNIEXPORT jstring JNICALL
Java_com_ollama_mobile_whisper_WhisperContext_nativeTranscribe(JNIEnv* env, jclass, jlong ptr, jfloatArray pcm) {
    auto* ctx = reinterpret_cast<whisper_context*>(ptr);
    if (!ctx) return env->NewStringUTF("");

    jsize len = env->GetArrayLength(pcm);
    jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);

    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads                   = 8;
    wp.translate                   = false;
    wp.no_context                  = true;
    wp.single_segment              = false;
    wp.language                    = "en";
    wp.print_realtime              = false;
    wp.print_progress              = false;
    wp.print_timestamps            = false;

    int rc = whisper_full(ctx, wp, samples, (int)len);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);

    if (rc != 0) { LOGE("whisper_full error %d", rc); return env->NewStringUTF(""); }

    std::string text;
    int n = whisper_full_n_segments(ctx);
    for (int i = 0; i < n; i++) {
        const char* seg = whisper_full_get_segment_text(ctx, i);
        if (seg) text += seg;
    }
    // strip leading spaces that whisper commonly adds
    size_t start = text.find_first_not_of(' ');
    if (start != std::string::npos) text = text.substr(start);

    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_ollama_mobile_whisper_WhisperContext_nativeFree(JNIEnv*, jclass, jlong ptr) {
    auto* ctx = reinterpret_cast<whisper_context*>(ptr);
    if (ctx) whisper_free(ctx);
}

} // extern "C"
