package com.ollama.mobile.network;

import com.ollama.mobile.model.OpenAIChatRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

public interface OpenAIApiService {

    @Streaming
    @POST("v1/chat/completions")
    Call<ResponseBody> chat(@Body OpenAIChatRequest request);
}
