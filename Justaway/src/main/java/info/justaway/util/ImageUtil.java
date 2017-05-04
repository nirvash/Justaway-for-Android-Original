package info.justaway.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
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
import com.nostra13.universalimageloader.core.assist.SimpleImageLoadingListener;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import info.justaway.JustawayApplication;
import info.justaway.R;
import info.justaway.ScaleImageActivity;
import info.justaway.VideoActivity;
import info.justaway.display.FadeInRoundedBitmapDisplayer;
import info.justaway.settings.BasicSettings;
import twitter4j.Status;

public class ImageUtil {
    private static final String TAG = ImageUtil.class.getSimpleName();
    private static DisplayImageOptions sRoundedDisplayImageOptions;
    private static CascadeClassifier sFaceDetector = null;
    private static Map<String, FaceCrop> sFaceInfoMap = new LinkedHashMap<String, FaceCrop>(100, 0.75f, true) {
        private static final int MAX_ENTRIES = 100;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FaceCrop> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

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
    }

    private static File setupCascadeFile(Context context) {
        File cascadeDir = context.getFilesDir();
        File cascadeFile = null;
        InputStream is = null;
        FileOutputStream os = null;
        try {
            cascadeFile = new File(cascadeDir, "lbpcascade_animeface.xml");
            if (!cascadeFile.exists()) {
                is = context.getResources().openRawResource(R.raw.lbpcascade_animeface);
                os = new FileOutputStream(cascadeFile);
                byte[] buffer = new byte[4096];
                int readLen = 0;
                while ((readLen = is.read(buffer)) != -1) {
                    os.write(buffer, 0, readLen);
                }
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // NOP
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // NOP
                }
            }
        }
        return cascadeFile;
    }

    private static CascadeClassifier setupFaceDetector(Context context) {
        File cascadeFile = setupCascadeFile(context);
        if (cascadeFile == null) {
            return null;
        }

        CascadeClassifier detector = new CascadeClassifier(cascadeFile.getAbsolutePath());
        detector.load(cascadeFile.getAbsolutePath());
if (detector.empty()) {
            return null;
        }
        return detector;
    }

    public static void initFaceDetector(Context context) {
        if (sFaceDetector == null) {
            sFaceDetector = setupFaceDetector(context);
        }
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
                int maxHeight = 400;
                ImageView imageView = (ImageView) view;
                if (imageView != null) {
                    float w = image.getWidth();
                    float h = image.getHeight();
                    FaceCrop faceCrop = sFaceInfoMap.get(imageUri);
                    if (faceCrop == null) {
                        faceCrop = new FaceCrop(maxHeight, w, h);
                        sFaceInfoMap.put(imageUri, faceCrop);
                    }
                    Bitmap cropImage = faceCrop.drawRegion(image);
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
                int maxHeight = 400;
                ImageView imageView = (ImageView) view;
                if (imageView != null) {
                    // CROP 指定で一定比率のときは画像の下を切り詰めてフォーカス位置を上にずらす
                    float w = image.getWidth();
                    float h = image.getHeight();
                    if (w > 0 && h / w > 300.0f / 600.0f) {
                        try {
                            FaceCrop faceCrop = sFaceInfoMap.get(imageUri);
                            if (faceCrop == null) {
                                faceCrop = new FaceCrop(maxHeight, w, h);
                                sFaceInfoMap.put(imageUri, faceCrop);
                            }
                            Bitmap cropImage = faceCrop.invoke(image);

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
        // そもそも PostResume 時に Page がフラグメントを全部作り直してたのが悪かっただけ？
        // 無駄な再生成をやめたらチラつかなくなったので元の処理に戻している
        return false;
    }

    private static boolean displayImageOnCacheImpl(String url, ImageView view, ImageLoadingListener listener) {
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
                    LinearLayout.LayoutParams dummyParams =
                            new LinearLayout.LayoutParams(20, imageHeight, 0.15f);
                    viewGroup.addView(image, layoutParams);
                    viewGroup.addView(space, dummyParams);
                } else {
                    viewGroup.addView(image, layoutParams);
                }
                if (image.getScaleType() == ImageView.ScaleType.CENTER_CROP) {
                    displayImageForCrop(url, image);
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


    private static class FaceCrop {
        private boolean mIsFirst = true;
        private boolean mIsSuccess;
        private int mMaxHeight;
        private float mWidth;
        private float mHeight;
        private Rect mRect;

        public FaceCrop(int maxHeight, float w, float h) {
            this.mMaxHeight = maxHeight;
            this.mWidth = w;
            this.mHeight = h;
        }

        private static Bitmap drawFaceRegion(Rect rect, Bitmap image, int color) {
            try {
                Bitmap result = image.copy(Bitmap.Config.ARGB_8888, true);
                Paint paint = new Paint();
                paint.setColor(color);
                paint.setStrokeWidth(4);
                paint.setStyle(Paint.Style.STROKE);

                Canvas canvas = new Canvas(result);
                if (rect != null) {
                    canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, paint);
                }
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return image;
            }
        }

        public Bitmap drawRegion(Bitmap bitmap) {
            if (mIsSuccess) {
                if (BasicSettings.isDebug()) {
                    bitmap = drawFaceRegion(mRect, bitmap, Color.GREEN);
                    return bitmap;
                }
            }
            return null;
        }


        public Bitmap invoke(Bitmap bitmap) {
            int c = mIsFirst ? Color.MAGENTA : Color.GREEN;
            if (mIsFirst) {
                mIsFirst = false;
                if (sFaceDetector != null) {
                    MatOfRect faces = new MatOfRect();
                    Mat imageMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
                    Utils.bitmapToMat(bitmap, imageMat);
                    Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_RGB2GRAY);
                    Imgproc.equalizeHist(imageMat, imageMat);

                    sFaceDetector.detectMultiScale(imageMat, faces, 1.1, 2, 0, new Size(300 / 5, 300 / 5), new Size());
                    Rect[] facesArray = faces.toArray();
                    if (facesArray.length > 0) {
                        Rect r = getLargestFace(facesArray);
                        Log.d(TAG, String.format("image: (%s, %s)", mWidth, mHeight));
                        Log.d(TAG, String.format("face area: (%d, %d, %d, %d)", r.x, r.y, r.width, r.height));
                        mRect = r;
                        mIsSuccess = true;
                    }
                }
            }

            if (mIsSuccess) {
                if (BasicSettings.isDebug()) {
                    bitmap = drawFaceRegion(mRect, bitmap, c);
                }
                Rect r = new Rect(mRect.x, mRect.y, mRect.width, mRect.height);
                r = addVPadding(r, bitmap);
                Bitmap resized = Bitmap.createBitmap(bitmap, 0, r.y, (int) mWidth, r.height);
                return resized;
            } else {
                return null;
            }
        }

        private Rect addVPadding(Rect r, Bitmap bitmap) {
            // 大きいときに周囲にパディングいれても横幅からの比で影響しないことが多いが一応
            int padding = r.height < mMaxHeight ? mMaxHeight - r.height : (int)(r.height * 0.2f);
            r.y -= padding / 2;
            r.height += padding;
            if (r.y < 0) {
                r.y = 0;
            }
            if (r.y + r.height > bitmap.getHeight()) {
                r.height = bitmap.getHeight() - r.y;
            }
            return r;
        }

        private Rect getLargestFace(Rect[] facesArray) {
            Rect ret = null;
            int maxSize = -1;
            for (Rect r : facesArray) {
                int size = r.width * r.height;
                if (size > maxSize) {
                    ret = r;
                    maxSize = size;
                }
            }
            return ret;
        }
    }

    private static class FaceInfo {

    }
}
