package com.ollama.mobile.network;

import com.ollama.mobile.model.AnthropicRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

public interface AnthropicApiService {

    @Streaming
    @POST("v1/messages")
    Call<ResponseBody> messages(@Body AnthropicRequest request);
}
