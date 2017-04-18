package info.justaway.util;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import java.util.ArrayList;

import info.justaway.JustawayApplication;
import info.justaway.R;
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
                LinearLayout.LayoutParams layoutParams =
                        new LinearLayout.LayoutParams(0, 400, 1.0f);

                if (imageUrls.size() == 1) {
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
                } else {
                    if (index == 0) {
                        layoutParams.setMargins(0, 0, 5, 0);
                    } else if (index == imageUrls.size() - 1) {
                        layoutParams.setMargins(5, 0, 0, 0);
                    } else {
                        layoutParams.setMargins(5, 0, 5, 0);
                    }
                }

                if (imageUrls.size() > 3) {
                    image.setAdjustViewBounds(true);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                } else {
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }

                if (imageUrls.size() == 1) {
                    Space space = new Space(context);
                    LinearLayout.LayoutParams dummyParams =
                            new LinearLayout.LayoutParams(20, 280, 0.15f);
                    viewGroup.addView(image, layoutParams);
                    viewGroup.addView(space, dummyParams);
                } else {
                    viewGroup.addView(image, layoutParams);
                }
                displayImage(url, image);

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

    private static int getDp(Context context, int sizeInDp) {
        float scale = context.getResources().getDisplayMetrics().density;
        int dpAsPixels = (int) (sizeInDp * scale + 0.5f);
        return dpAsPixels;
    }
}
