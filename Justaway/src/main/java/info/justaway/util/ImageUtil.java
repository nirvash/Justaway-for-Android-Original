package info.justaway.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.makeramen.roundedimageview.RoundedImageView;
import com.nostra13.universalimageloader.core.DefaultConfigurationFactory;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.DiscCacheUtil;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;
import com.nostra13.universalimageloader.core.assist.MemoryCacheUtil;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.core.assist.SimpleImageLoadingListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

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

        if (displayImageOnCache(url, view, null)) {
            return;
        } else {
            ImageLoader.getInstance().displayImage(url, view);
        }
    }

    public static void displayImageForCrop(String url, ImageView view, final Context context) {
        String tag = (String) view.getTag();
        if (tag != null && tag.equals(url)) {
            return;
        }
        view.setTag(url);

        ImageLoadingListener listener = new SimpleImageLoadingListener() {
            @Override
            public void onLoadingComplete(String imageUri, View view, Bitmap image) {
                ImageView imageView = (ImageView) view;
                if (imageView != null) {
                    // CROP 指定で画像が縦長の時は下を切り詰めてフォーカス位置を上にずらす
                    float w = image.getWidth();
                    float h = image.getHeight();
                    if (h > 0 && h / w > 3.2f / 3.0f) {
                        // ソース画像の高さを縮小してセンターを上に移動させる
                        // 渡された Bitmap はメモリキャッシュに乗っているので変更してはダメ
                        float rate = h/w > 1.5f ? 0.4f : 0.6f;
                        Bitmap resized = Bitmap.createBitmap(image, 0, 0, (int)w, (int)(h * rate));
                        imageView.setImageBitmap(resized);
                    }
                }
            }
        };

        if (displayImageOnCache(url, view, listener)) {
            return;
        } else {
            ImageLoader.getInstance().displayImage(url, view, listener);
        }
    }


    // 4枚サムネイルのとき, グラブルからの投稿のとき
    public static void displayImage(String url, ImageView view, boolean cropByAspect) {
        String tag = (String) view.getTag();
        if (tag != null && tag.equals(url)) {
            return;
        }
        view.setTag(url);

        ImageLoadingListener listener = null;

        if (cropByAspect) { // 4枚サムネイルのとき
            listener = new SimpleImageLoadingListener() {
                @Override
                public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                    ImageView imageView = (ImageView) view;

                    float w = loadedImage.getWidth();
                    float h = loadedImage.getHeight();
                    if (h > 0 && w/h > 1.4f) {
                        // 横長の時はクロップにする
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        // この時点ではすでに view はレイアウトされているので setMaxHeight とかは無意味？
                    }
                }
            };
        }

        if (displayImageOnCache(url, view, listener)) {
            return;
        } else {
            ImageLoader.getInstance().displayImage(url, view, listener);
        }
    }


    public static void displayRoundedImage(String url, ImageView view) {
        String tag = (String) view.getTag();
        if (tag != null && tag.equals(url)) {
            return;
        }
        view.setTag(url);
        DisplayImageOptions options = null;
        if (BasicSettings.getUserIconRoundedOn()) {
            if (sRoundedDisplayImageOptions == null) {
                sRoundedDisplayImageOptions = new DisplayImageOptions.Builder()
                        .cacheInMemory(true)
                        .cacheOnDisc(true)
                        .resetViewBeforeLoading(true)
                        .displayer(new FadeInRoundedBitmapDisplayer(15))
                        .build();
            }
            options = sRoundedDisplayImageOptions;
        }

        if (displayImageOnCache(url, view, null)) {
            return;
        } else {
            ImageLoader.getInstance().displayImage(url, view, options);
        }
    }

    // キャッシュが存在するときは同期ロードする。デフォルトではキャッシュも非同期ロードなのでちらつく
    private static boolean displayImageOnCache(String url, ImageView view, ImageLoadingListener listener) {
        boolean existCache = false;
        List<Bitmap> caches = MemoryCacheUtil.findCachedBitmapsForImageUri(url, ImageLoader.getInstance().getMemoryCache());
        if (caches.size() == 0) {
            File file = DiscCacheUtil.findInCache(url, ImageLoader.getInstance().getDiscCache());
            existCache = file != null;
        } else {
            existCache = true;
        }

        if (existCache) {
            Bitmap bitmap = ImageLoader.getInstance().loadImageSync(url);
            if (bitmap != null) {
                view.setImageBitmap(bitmap);
                if (listener != null) {
                    listener.onLoadingComplete(url, view, bitmap);
                }
                return true;
            }
        }
        return false;
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
            boolean viaGranblueFantasy = StatusUtil.viaGranblueFantasy(status);
            int imageHeight = viaGranblueFantasy ? 300 : 400;
            // 画像を貼るスペースをクリア
            viewGroup.removeAllViews();
            int index = 0;
            for (final String url : imageUrls) {
                RoundedImageView image = new RoundedImageView(context);
                image.setMinimumHeight(150); // 極端に小さい画像対応
                image.setCornerRadius(25.0f);
                image.setBorderWidth(2.0f);
                image.setBorderColor(Color.DKGRAY);
                LinearLayout.LayoutParams layoutParams =
                        new LinearLayout.LayoutParams(0, imageHeight, 1.0f);

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

                boolean cropByAspect = false;

                if (imageUrls.size() > 3) {
                    image.setMaxHeight(imageHeight);
                    image.setAdjustViewBounds(true);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    layoutParams.gravity = Gravity.CENTER_VERTICAL;
                    cropByAspect = true;
                } else {
                    if (viaGranblueFantasy) {
                        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    } else {
                        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                }

                if (imageUrls.size() == 1) {
                    Space space = new Space(context);
                    LinearLayout.LayoutParams dummyParams =
                            new LinearLayout.LayoutParams(20, imageHeight, 0.15f);
                    viewGroup.addView(image, layoutParams);
                    viewGroup.addView(space, dummyParams);
                } else {
                    viewGroup.addView(image, layoutParams);
                }
                if (image.getScaleType() == ImageView.ScaleType.CENTER_CROP) {
                    displayImageForCrop(url, image, context);
                } else {
                    displayImage(url, image, cropByAspect);
                }

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
