package net.opengress.slimgress.net;

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
        IOException lastException = null;

        Request originalRequest = chain.request();
        Request requestWithUserAgent = originalRequest
                .newBuilder()
                .header("Content-Type", contentType)
                .build();

        int MAX_RETRIES = 3;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return chain.proceed(requestWithUserAgent);
            } catch (IOException e) {
                lastException = e;
                if (i == MAX_RETRIES - 1) {
                    throw e;
                }
            }
        }
        throw lastException;
    }
}
