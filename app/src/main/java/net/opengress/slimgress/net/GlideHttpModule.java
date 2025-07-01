package net.opengress.slimgress.net;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import net.opengress.slimgress.SlimgressApplication;

import java.io.InputStream;

import okhttp3.OkHttpClient;

@GlideModule
public class GlideHttpModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        OkHttpClient existingClient = SlimgressApplication.getInstance().getGame().getHttpClient();

        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(existingClient));
    }
}
