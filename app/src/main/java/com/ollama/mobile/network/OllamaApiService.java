package com.ollama.mobile.network;

import com.google.gson.JsonObject;
import com.ollama.mobile.model.OllamaModel;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

import com.ollama.mobile.model.ChatRequest;

public interface OllamaApiService {

    @GET("api/tags")
    Call<OllamaModel.TagsResponse> getTags();

    @Streaming
    @POST("api/chat")
    Call<ResponseBody> chat(@Body ChatRequest request);

    @DELETE("api/delete")
    Call<ResponseBody> deleteModel(@Body JsonObject body);

    @Streaming
    @POST("api/pull")
    Call<ResponseBody> pullModel(@Body JsonObject body);

    @GET(".")
    Call<ResponseBody> healthCheck();
}
