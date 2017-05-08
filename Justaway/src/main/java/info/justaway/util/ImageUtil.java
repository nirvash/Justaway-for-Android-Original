package info.justaway.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.annotation.NonNull;
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
import com.nostra13.universalimageloader.core.assist.deque.LIFOLinkedBlockingDeque;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.justaway.JustawayApplication;
import info.justaway.ScaleImageActivity;
import info.justaway.VideoActivity;
import info.justaway.display.FadeInRoundedBitmapDisplayer;
import info.justaway.settings.BasicSettings;
import info.justaway.task.LoadImageTask;
import twitter4j.Status;

public class ImageUtil {
    private static final String TAG = ImageUtil.class.getSimpleName();
    private static DisplayImageOptions sRoundedDisplayImageOptions;

    public static void init(Context context) {
        DisplayImageOptions defaultOptions = new DisplayImageOptions
                .Builder()
                .cacheInMemory(true)
                .cacheOnDisc(true)
                .resetViewBeforeLoading(true)
                .build();

        Executor executor = DefaultConfigurationFactory.createExecutor(
                ImageLoaderConfiguration.Builder.DEFAULT_THREAD_POOL_SIZE,
                ImageLoaderConfiguration.Builder.DEFAULT_THREAD_PRIORITY - 2,
                ImageLoaderConfiguration.Builder.DEFAULT_TASK_PROCESSING_TYPE);

        ImageLoaderConfiguration config = new ImageLoaderConfiguration
                .Builder(JustawayApplication.getApplication())
                .defaultDisplayImageOptions(defaultOptions)
                .taskExecutor(executor)
                .build();

        ImageLoader.getInstance().init(config);
        LoadImageTask.initEngine();
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

    public static void displayImageFaceDetect(String url, ImageView view) {
        String tag = (String) view.getTag();
        if (tag != null && tag.equals(url)) {
            return;
        }
        view.setTag(url);


        ImageLoadingListener listener = new SimpleImageLoadingListener() {
            @Override
            public void onLoadingComplete(String imageUri, View view, Bitmap image) {
                if (!BasicSettings.enableFaceDetection()) {
                    return;
                }
                int maxHeight = 400;
                ImageView imageView = (ImageView) view;
                if (imageView != null) {
                    float w = image.getWidth();
                    float h = image.getHeight();
                    FaceCrop faceCrop = FaceCrop.get(imageUri, maxHeight, w, h);
                    Bitmap cropImage = faceCrop != null ? faceCrop.drawRegion(image) : null;
                    if (cropImage != null) {
                        imageView.setImageBitmap(cropImage);
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

    public static void displayImageForCrop(String url, ImageView view) {
        String tag = (String) view.getTag();
        if (tag != null && tag.equals(url)) {
            return;
        }
        view.setTag(url);

        ImageLoadingListener listener = new SimpleImageLoadingListener() {
            @Override
            public void onLoadingComplete(String imageUri, View view, Bitmap image) {
                ImageView imageView = (ImageView) view;
                ImageUtil.setImageWithFaceCrop(imageUri, imageView, image, false);
            }
        };

        if (displayImageOnCache(url, view, listener)) {
            return;
        } else {
            ImageLoader.getInstance().displayImage(url, view, listener);
        }
    }

    private static void setImageWithFaceCrop(String imageUri, ImageView imageView, Bitmap image, boolean setImage) {
        int maxHeight = 400;
        if (imageView != null) {
            // CROP 指定で一定比率のときは画像の下を切り詰めてフォーカス位置を上にずらす
            float w = image.getWidth();
            float h = image.getHeight();
            if (w > 0 && h / w > 300.0f / 600.0f) {
                try {
                    Bitmap cropImage = null;
                    if (BasicSettings.enableFaceDetection()) {
                        FaceCrop faceCrop =FaceCrop.get(imageUri, maxHeight, w, h);
                        cropImage = faceCrop != null ? faceCrop.invoke(image) : null;
                    }

                    if (cropImage != null) {
                        imageView.setImageBitmap(cropImage);
                    } else {
                        // ソース画像の高さを縮小してセンターを上に移動させる
                        // 渡された Bitmap はメモリキャッシュに乗っているので変更してはダメ
                        float rate = h / w > 1.5f ? 0.4f : 0.6f;
                        Bitmap resized = Bitmap.createBitmap(image, 0, 0, (int) w, (int) (h * rate));
                        imageView.setImageBitmap(resized);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    if (setImage) {
                        imageView.setImageBitmap(image);
                    }
                }
            } else if (setImage) {
                imageView.setImageBitmap(image);
            }
        }
    }


    // 4枚サムネイルのとき, グラブルからの投稿のとき
    public static void displayImage(String url, ImageView view, final boolean cropByAspect) {
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
                    ImageUtil.setImageWithCrop(imageView, loadedImage, cropByAspect);
                }
            };
        }

        if (displayImageOnCache(url, view, listener)) {
            return;
        } else {
            ImageLoader.getInstance().displayImage(url, view, listener);
        }
    }

    private static void setImageWithCrop(ImageView imageView, Bitmap loadedImage, boolean cropByAspect) {
        if (cropByAspect) {
            float w = loadedImage.getWidth();
            float h = loadedImage.getHeight();
            if (h > 0 && w / h > 1.4f) {
                // 横長の時はクロップにする
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                // この時点ではすでに view はレイアウトされているので setMaxHeight とかは無意味？
            }
        }
    }

    private static void setImageWithCrop(LoadImageTask.Result entry, ImageView imageView, boolean cropByAspect) {
        Bitmap image = entry.bitmap;

        if (cropByAspect) {
            float w = image.getWidth();
            float h = image.getHeight();
            if (h > 0 && w / h > 1.4f) {
                // 横長の時はクロップにする
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                // この時点ではすでに view はレイアウトされているので setMaxHeight とかは無意味？
            }
        } else {
            image = FaceCrop.cropFace(image, entry.rect, entry.maxHeight);
        }

        imageView.setImageBitmap(image);
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
        // そもそも PostResume 時に Page がフラグメントを全部作り直してたのが悪かっただけ？
        // 無駄な再生成をやめたらチラつかなくなったので元の処理に戻している
        return false;
    }

    private static boolean displayImageOnCacheImpl(String url, ImageView view, ImageLoadingListener listener) {
        if (hasCache(url)) {
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

    public static boolean hasCache(String url) {
        boolean hasCache;
        List<Bitmap> caches = MemoryCacheUtil.findCachedBitmapsForImageUri(url, ImageLoader.getInstance().getMemoryCache());
        if (caches.size() == 0) {
            File file = DiscCacheUtil.findInCache(url, ImageLoader.getInstance().getDiscCache());
            hasCache = file != null;
        } else {
            hasCache = true;
        }
        return hasCache;
    }

    /**
     * ツイートに含まれる画像をサムネイル表示
     *
     * @param context   Activity
     * @param viewGroup サムネイルを表示するView
     * @param status    ツイート
     */
    public static void displayThumbnailImages(final Context context, final ViewGroup viewGroup, final ViewGroup wrapperViewGroup, TextView play, final Status status) {
        // ツイートに含まれる動画のURLを取得
        final String videoUrl = StatusUtil.getVideoUrl(status);

        // ツイートに含まれる画像のURLをすべて取得
        final ArrayList<String> imageUrls = StatusUtil.getImageUrls(status);
        if (imageUrls.size() > 0) {
            final boolean viaGranblueFantasy = StatusUtil.viaGranblueFantasy(status);
            final int imageHeight = viaGranblueFantasy ? 300 : 450;
            // 画像を貼るスペースをクリア
            viewGroup.removeAllViews();
            viewGroup.setVisibility(View.INVISIBLE);
            wrapperViewGroup.setVisibility(View.INVISIBLE);
            Space spacer = new Space(context);
            LinearLayout.LayoutParams dummyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, imageHeight);
            viewGroup.addView(spacer, dummyParams);

            new LoadImageTask(imageUrls, !viaGranblueFantasy, imageHeight) {
                @Override
                protected void onPostExecute() {
                    // 画像を貼るスペースをクリア
                    viewGroup.removeAllViews();

                    layoutThumbnails();

                    viewGroup.setVisibility(View.VISIBLE);
                    wrapperViewGroup.setVisibility(View.VISIBLE);
                }

                private void layoutThumbnails() {
                    if (layoutFourThumbnailsInSquare()) {
                        return;
                    }

                    if (layoutFourThumbnailsSpecial()) {
                        return;
                    }


                    int index = 0;
                    for (final Result entry : mBitmaps) {
                        RoundedImageView image = getRoundedImageView();
                        LinearLayout.LayoutParams layoutParams =
                                new LinearLayout.LayoutParams(0, mHeight, 1.0f);

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
                            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
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
                            LinearLayout.LayoutParams dummyParams = new LinearLayout.LayoutParams(20, imageHeight, 0.15f);
                            viewGroup.addView(image, layoutParams);
                            viewGroup.addView(space, dummyParams);
                        } else {
                            viewGroup.addView(image, layoutParams);
                        }
                        if (image.getScaleType() == ImageView.ScaleType.CENTER_CROP) {
                            setImageWithCrop(entry, image, false);
                        } else {
                            setImageWithCrop(entry, image, cropByAspect);
                        }

                        setClickListener(index, image);
                        index++;
                    }
                }

                // Land, Land, Hori, Hori or Hori, Hori, Land, Land, or Hori, Land, Land, Hori
                private boolean layoutFourThumbnailsSpecial() {
                    if (mBitmaps.size() <= 3) {
                        return false;
                    }

                    int pairPosition = -1;
                    if (mBitmaps.get(0).isLandscape() && mBitmaps.get(1).isLandscape()) {
                        pairPosition = 0;
                    } else if (mBitmaps.get(1).isLandscape() && mBitmaps.get(2).isLandscape()) {
                        pairPosition = 1;
                    } else if (mBitmaps.get(2).isLandscape() && mBitmaps.get(3).isLandscape()) {
                        pairPosition = 2;
                    } else {
                        return false;
                    }

                    int maxHeight = mHeight;
                    // タイルレイアウト
                    LinearLayout view = new LinearLayout(context);
                    view.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams layoutParams2 =
                            new LinearLayout.LayoutParams(0, maxHeight, 1.0f);
                    if (pairPosition == 0) {
                        layoutParams2.setMargins(0, 0, 5, 0);
                    } else if (pairPosition == 1) {
                        layoutParams2.setMargins(5, 0, 5, 0);
                    } else if (pairPosition == 2) {
                        layoutParams2.setMargins(5, 0, 0, 0);
                    }
                    view.setLayoutParams(layoutParams2);

                    int index = 0;
                    int height = (maxHeight - 10) / 2;
                    for (final Result entry : mBitmaps) {
                        RoundedImageView image = getRoundedImageView();
                        LinearLayout.LayoutParams layoutParams = null;

                        if (pairPosition == index || pairPosition + 1 == index) {
                            layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
                        } else {
                            layoutParams = new LinearLayout.LayoutParams(0, maxHeight, 1.0f);
                            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            image.setMaxHeight(maxHeight);
                        }

                        if (pairPosition == index) {
                            layoutParams.setMargins(0, 0, 0, 10);
                        } else if (pairPosition + 1 != index) {
                            layoutParams.setMargins(0, 0, 0, 10);
                        } else if (index == 0) {
                            layoutParams.setMargins(0, 0, 5, 0);
                        } else if (index == 3) {
                            layoutParams.setMargins(5, 0, 0, 0);
                        } else {
                            layoutParams.setMargins(5, 0, 5, 0);
                        }

                        boolean cropByAspect = true;

                        image.setAdjustViewBounds(true);
                        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        layoutParams.gravity = Gravity.CENTER_VERTICAL;

                        if (pairPosition == index || pairPosition + 1 == index) {
                            view.addView(image, layoutParams);
                        } else {
                            viewGroup.addView(image, layoutParams);
                        }
                        if (pairPosition == index) {
                            viewGroup.addView(view);
                        }

                        setImageWithCrop(entry, image, cropByAspect);

                        setClickListener(index, image);
                        index++;
                    }

                    return true;
                }

                private boolean layoutFourThumbnailsInSquare() {
                    if (mBitmaps.size() <= 3) {
                        return false;
                    }

                    for (Result entry : mBitmaps) {
                        if (!entry.isLandscape()) {
                            return false;
                        }
                    }

                    int maxHeight = mHeight;
                    // 4枚タイルレイアウト
                    LinearLayout views[] = new LinearLayout[] { new LinearLayout(context), new LinearLayout(context) };
                    boolean isLeft = true;
                    for (LinearLayout view : views) {
                        view.setOrientation(LinearLayout.VERTICAL);
                        LinearLayout.LayoutParams layoutParams =
                                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, maxHeight, 1.0f);
                        if (isLeft) {
                            isLeft = false;
                            layoutParams.setMargins(0, 0, 10, 0);
                        }
                        viewGroup.addView(view, layoutParams);
                    }

                    int index = 0;
                    int height = (maxHeight - 10) / 2;
                    for (final Result entry : mBitmaps) {
                        RoundedImageView image = getRoundedImageView();
                        LinearLayout.LayoutParams layoutParams =
                                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);

                        if (index == 0 || index == 1) {
                            layoutParams.setMargins(0, 0, 0, 10);
                        }

                        boolean cropByAspect = true;

                        // layoutParams.ight = ViewGroup.LayoutParams.WRAP_CONTENT;
                        // image.setMaxHeight(height);
                        image.setAdjustViewBounds(true);
                        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        layoutParams.gravity = Gravity.CENTER_VERTICAL;

                        int viewIndex = (index % 2) == 0 ? 0 : 1;
                        views[viewIndex].addView(image, layoutParams);

                        setImageWithCrop(entry, image, cropByAspect);

                        setClickListener(index, image);
                        index++;
                    }


                    return true;
                }

                private void setClickListener(final int index, RoundedImageView image) {
                    if (videoUrl.isEmpty()) {
                        // 画像タップで拡大表示（ピンチイン・ピンチアウトいつかちゃんとやる）
                        image.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent(v.getContext(), ScaleImageActivity.class);
                                intent.putExtra("status", status);
                                intent.putExtra("index", index);
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
                }

                @NonNull
                private RoundedImageView getRoundedImageView() {
                    RoundedImageView image = new RoundedImageView(context);
                    image.setMinimumHeight(100); // 極端に小さい画像対応
                    image.setCornerRadius(25.0f);
                    image.setBorderWidth(2.0f);
                    image.setBorderColor(Color.DKGRAY);
                    return image;
                }
            }.execute();


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


    private static class FaceInfo {

    }
}
