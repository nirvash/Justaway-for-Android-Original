package info.justaway.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DefaultConfigurationFactory;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;
import com.nostra13.universalimageloader.core.assist.MemoryCacheUtil;
import com.nostra13.universalimageloader.core.assist.SimpleImageLoadingListener;

import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import info.justaway.JustawayApplication;
import info.justaway.display.FadeInRoundedBitmapDisplayer;
import info.justaway.settings.BasicSettings;
import info.justaway.task.LoadImageTask;
import twitter4j.Status;

public class ImageUtil {
    private static final String TAG = ImageUtil.class.getSimpleName();
    private static DisplayImageOptions sRoundedDisplayImageOptions;
    private static Point mDisplaySize = new Point(1080, 1920);

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
                .memoryCacheSizePercentage(20)
                .discCacheSize(1024 * 1024 * 128)
                .build();

        ImageLoader.getInstance().init(config);
        LoadImageTask.initEngine();
    }

    public static Point getDisplaySize(){
        return mDisplaySize;
    }

    public static void setDisplaySize(Point point) {
        mDisplaySize = point;
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

    // ScaleImageView で使っている
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
                BitmapWrapper wrapper = new BitmapWrapper(image, false);
                int maxHeight = 400;
                ImageView imageView = (ImageView) view;
                if (imageView != null) {
                    float w = wrapper.getWidth();
                    float h = wrapper.getHeight();
                    FaceCrop faceCrop = FaceCrop.get(imageUri, w, h);
                    if (faceCrop != null && faceCrop.isSuccess()) {
                        BitmapWrapper cropImage = faceCrop != null ? faceCrop.drawRegion(wrapper) : null;
                        if (cropImage != null) {
                            imageView.setImageBitmap(cropImage.getBitmap());
                        }
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




    // Widget の大きさに合わせてクロップを行う
    private static BitmapWrapper cropImage(BitmapWrapper bitmap, int width, int height) {
        int imageWidth = (int)bitmap.getWidth();
        int imageHeight = (int)bitmap.getHeight();

        if (width == 0 || height == 0) {
            return bitmap;
        }

        Rect srcRect = new Rect(0, 0, imageWidth, imageHeight);
        if ((float)imageWidth  / (float)imageHeight > (float)width / (float)height) {
            int w = imageHeight * width / height;
            if (w < imageWidth) {
                srcRect.width = w;
                int diff = imageWidth - w;
                srcRect.x += diff / 2;
            } else {
                return bitmap;
            }
        } else {
            int h = imageWidth * height / width;
            if (h < imageHeight) {
                srcRect.height = h;
                int diff = imageHeight - h;
                srcRect.y += diff / 2;
            } else {
                return bitmap;
            }
        }

        Bitmap cropped = Bitmap.createBitmap(bitmap.getBitmap(), srcRect.x, srcRect.y, srcRect.width, srcRect.height);
        bitmap.recycle();
        Bitmap scaled = Bitmap.createScaledBitmap(cropped,  width, height, true);
        cropped.recycle();
        return new BitmapWrapper(scaled, true);
    }

    // レイアウトが後用
    // 画像をクロップして ImageView に設定する
    public static void setImageWithCrop(LoadImageTask.Result entry, ImageView imageView, boolean cropByAspect, float viewHeight, float viewWidth, int nImages) {
        BitmapWrapper image = new BitmapWrapper(entry.bitmap, false);
        float viewAspect = viewHeight / viewWidth;

        if (cropByAspect || !entry.isFaceDetected()) {
            if (entry.isFaceDetected()) {
                if (nImages > 1) {
                    image = entry.faceCrop.cropFace2(image, (int) viewWidth, (int) viewHeight);
                } else {
                    image = entry.faceCrop.cropFace(image, viewAspect,  (int) viewWidth, (int) viewHeight);
                };
            }
            float w = image.getWidth();
            float h = image.getHeight();

            if (h < viewHeight && w < viewWidth) {
                // 画像が小さい場合は全体を表示
                if (h / viewHeight < 0.8f) {
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                } else {

                }
                imageView.setImageBitmap(image.getBitmap());
                return;
            } else if (w > 0 && h / w > 300.0f / 600.0f && h > viewHeight) {
                // 縦長のときは上側フォーカスでクロップ

                // ソース画像の高さを縮小してセンターを上に移動させる
                // 渡された Bitmap はメモリキャッシュに乗っているので変更してはダメ
                float rate = h / w > 1.5f ? 0.4f : 0.6f;
                int height = (int)(h * rate);
                if (height  / w > viewHeight / viewWidth) {
                    Bitmap resized = Bitmap.createBitmap(image.getBitmap(), 0, 0, (int) w, height);
                    image.recycle();
                    imageView.setImageBitmap(resized);
                    return;
                }
            }

            // 縦横ともに大きい場合は縮小する
            if (w > viewWidth && h > (viewHeight * 0.8f)) {
                float bitmapAspect = h / w;
                float rate = 1.0f;
                if (viewAspect > bitmapAspect) {
                    // 横が余るとき
                    rate = Math.min(1.2f, viewHeight / h * 1.1f);
                } else {
                    rate = Math.min(1.2f, viewWidth / w * 1.1f);
                }

                if (rate < 1.2f) {
                    Bitmap resized = Bitmap.createScaledBitmap(image.getBitmap(), (int) (w * rate), (int) (h * rate), true);
                    image.recycle();
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imageView.setImageBitmap(resized);
                } else {
                    imageView.setImageBitmap(image.getBitmap());
                }
            } else {
                imageView.setImageBitmap(image.getBitmap());
            }
        } else if (entry.faceCrop != null) {
            if (nImages > 1) {
                image = entry.faceCrop.cropFace2(image, (int) viewWidth, (int) viewHeight);
            } else {
                image = entry.faceCrop.cropFace(image, viewAspect,  (int) viewWidth, (int) viewHeight);
            }
            imageView.setImageBitmap(image.getBitmap());
        } else {
            imageView.setImageBitmap(image.getBitmap());
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
        List<Bitmap> caches = MemoryCacheUtil.findCachedBitmapsForImageUri(url, ImageLoader.getInstance().getMemoryCache());
        return (caches.size() != 0);
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
        play.setVisibility(videoUrl.isEmpty() ? View.GONE : View.VISIBLE);

        // ツイートに含まれる画像のURLをすべて取得
        final ArrayList<String> imageUrls = StatusUtil.getImageUrls(status);

        if (imageUrls.size() > 0) {
            final boolean viaGranblueFantasy = StatusUtil.viaGranblueFantasy(status);
            // 画像の領域はこの時点で決定しておく (読み込み後に高さが変化するとカクツキの原因となる)
            final int imageHeight = viaGranblueFantasy ? 300 : 450;

            // 画像を貼るスペースをクリア
            viewGroup.removeAllViews();
            viewGroup.setVisibility(View.INVISIBLE);
            wrapperViewGroup.setVisibility(View.INVISIBLE);

            // 画像領域の高さを確保
            Space spacer = new Space(context);
            LinearLayout.LayoutParams dummyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, imageHeight);
            viewGroup.addView(spacer, dummyParams);

            new LoadImageTask(status, imageUrls, !viaGranblueFantasy, imageHeight, viewGroup, wrapperViewGroup, context).execute();
        } else {
            hideImageContainer(viewGroup, wrapperViewGroup);
        }
    }

    private static void clearImagesInLayout(ViewGroup viewGroup) {
        for (int i=0; i < viewGroup.getChildCount(); i++) {
            View v = viewGroup.getChildAt(i);
            if (v instanceof ImageView) {
                ImageView image = (ImageView) v;
                image.setImageBitmap(null);
            } else if (v instanceof ViewGroup) {
                ViewGroup childViewGroup = (ViewGroup) v;
                clearImagesInLayout(childViewGroup);
            }
        }
        viewGroup.removeAllViews();
    }

    public static void hideImageContainer(ViewGroup viewGroup, ViewGroup wrapperViewGroup) {
        clearImagesInLayout(viewGroup);
        viewGroup.setVisibility(View.GONE);
        wrapperViewGroup.setVisibility(View.GONE);
    }
}
