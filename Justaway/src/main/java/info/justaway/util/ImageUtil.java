package info.justaway.util;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import java.util.ArrayList;

import info.justaway.JustawayApplication;
import info.justaway.ScaleImageActivity;
import info.justaway.VideoActivity;
import info.justaway.display.FadeInRoundedBitmapDisplayer;
import info.justaway.settings.BasicSettings;
import twitter4j.Status;

public class ImageUtil {
    private static DisplayImageOptions sRoundedDisplayImageOptions;

    public static void init() {
        DisplayImageOptions defaultOptions = new DisplayImageOptions
                .Builder()
                .cacheInMemory(true)
                .cacheOnDisc(true)
                .resetViewBeforeLoading(true)
                .build();

        ImageLoaderConfiguration config = new ImageLoaderConfiguration
                .Builder(JustawayApplication.getApplication())
                .defaultDisplayImageOptions(defaultOptions)
                .build();

        ImageLoader.getInstance().init(config);
    }

    public static void displayImage(String url, ImageView view) {
        String tag = (String) view.getTag();
        if (tag != null && tag.equals(url)) {
            return;
        }
        view.setTag(url);
        ImageLoader.getInstance().displayImage(url, view);
    }

    public static void displayRoundedImage(String url, ImageView view) {
        String tag = (String) view.getTag();
        if (tag != null && tag.equals(url)) {
            return;
        }
        view.setTag(url);
        if (BasicSettings.getUserIconRoundedOn()) {
            if (sRoundedDisplayImageOptions == null) {
                sRoundedDisplayImageOptions = new DisplayImageOptions.Builder()
                        .cacheInMemory(true)
                        .cacheOnDisc(true)
                        .resetViewBeforeLoading(true)
                        .displayer(new FadeInRoundedBitmapDisplayer(5))
                        .build();
            }
            ImageLoader.getInstance().displayImage(url, view, sRoundedDisplayImageOptions);
        } else {
            ImageLoader.getInstance().displayImage(url, view);
        }
    }

    /**
     * ツイートに含まれる画像をサムネイル表示
     *
     * @param context   Activity
     * @param viewGroup サムネイルを表示するView
     * @param status    ツイート
     */
    public static void displayThumbnailImages(final Context context, ViewGroup viewGroup, ViewGroup wrapperViewGroup, TextView play, final Status status) {

        // ツイートに含まれる動画のURLを取得
        final String videoUrl = StatusUtil.getVideoUrl(status);

        // ツイートに含まれる画像のURLをすべて取得
        ArrayList<String> imageUrls = StatusUtil.getImageUrls(status);
        if (imageUrls.size() > 0) {
            viewGroup.setVisibility(View.INVISIBLE);
            wrapperViewGroup.setVisibility(View.INVISIBLE);

            // 画像を貼るスペースをクリア
            viewGroup.removeAllViews();
            int index = 0;
            for (final String url : imageUrls) {
                ImageView image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams layoutParams =
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 240);
                layoutParams.weight = 1.0f;
                if (index > 0) {
                    layoutParams.setMargins(20, 0, 0, 0);
                }
                // layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
                viewGroup.addView(image, layoutParams);
                displayRoundedImage(url, image);

                if (videoUrl.isEmpty()) {
                    // 画像タップで拡大表示（ピンチイン・ピンチアウトいつかちゃんとやる）
                    final int openIndex = index;
                    image.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(v.getContext(), ScaleImageActivity.class);
                            intent.putExtra("status", status);
                            intent.putExtra("index", openIndex);
                            context.startActivity(intent);
                        }
                    });
                } else {
                    // 画像タップで拡大表示（ピンチイン・ピンチアウトいつかちゃんとやる）
                    image.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(v.getContext(), VideoActivity.class);
                            intent.putExtra("videoUrl", videoUrl);
                            context.startActivity(intent);
                        }
                    });

                }
                index++;
            }
            viewGroup.setVisibility(View.VISIBLE);
            wrapperViewGroup.setVisibility(View.VISIBLE);
        } else {
            viewGroup.setVisibility(View.GONE);
            wrapperViewGroup.setVisibility(View.GONE);
        }
        play.setVisibility(videoUrl.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
