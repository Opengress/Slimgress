package com.norman0406.slimgress.API.Interface;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class DefaultRequestInterceptor implements Interceptor {

    private final String contentType;

    public DefaultRequestInterceptor(String ct) {
        contentType = ct;
    }

    @NonNull
    public Response intercept(Interceptor.Chain chain)
            throws IOException {

        Request originalRequest = chain.request();
        Request requestWithUserAgent = originalRequest
                .newBuilder()
                .header("Content-Type", contentType)
                .build();

        return chain.proceed(requestWithUserAgent);
    }
}
