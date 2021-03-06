package info.justaway.task;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;

import com.makeramen.roundedimageview.RoundedImageView;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.deque.LIFOLinkedBlockingDeque;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.justaway.ScaleImageActivity;
import info.justaway.VideoActivity;
import info.justaway.settings.BasicSettings;
import info.justaway.util.FaceCrop;
import info.justaway.util.ImageUtil;
import info.justaway.util.StatusUtil;
import twitter4j.Status;

public class LoadImageTask implements Runnable {
    private static final String TAG = LoadImageTask.class.getSimpleName();

    protected Status mStatus;
    protected List<String> mUrls;
    protected boolean mEnableCrop = false;
    protected int mHeight;
    private ViewGroup mViewGroup;
    private View mWrapperViewGroup;
    private Context mContext;

    boolean mHasCache = false;
    protected ArrayList<Result> mBitmaps = new ArrayList<>();

    private static Executor sLoadImageExecutor;
    private static Handler sHandler;

    public LoadImageTask(Status status, List<String> urls, boolean enableCrop, int height, ViewGroup viewGroup, View wrapper, Context context) {
        mStatus = status;
        mUrls = urls;
        mEnableCrop = enableCrop;
        mHeight = height;
        mViewGroup = viewGroup;
        mWrapperViewGroup = wrapper;
        mContext = context;
    }

    public void execute() {
        mHasCache = true;
        for (String url : mUrls) {
            if (!ImageUtil.hasCache(url)) {
                mHasCache = false;
                break;
            }
        }

        Log.d(TAG, "execute() : mHasCache: " + mHasCache + ", " + mStatus.getId());

        if (mHasCache) {
            doInBackground();
            onPostExecute();
        } else {
            sLoadImageExecutor.execute(this);
        }
    }

    public static void initEngine() {
        int priority = 2;
        int threadPoolSize = 10;
        BlockingQueue<Runnable> taskQueue =  new LIFOLinkedBlockingDeque<Runnable>();
        sLoadImageExecutor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L, TimeUnit.MILLISECONDS, taskQueue, createThreadFactory(priority));
        sHandler = new Handler();
    }


    private static ThreadFactory createThreadFactory(final int priority) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setPriority(priority);
                return thread;
            }
        };
    }

    @Override
    public void run() {
        doInBackground();

        sHandler.post(new Runnable() {
            @Override
            public void run() {
                onPostExecute();
            }
        });
    }

    public class Result {
        public String url;
        public Bitmap bitmap;
        public FaceCrop faceCrop;
        public int maxHeight;
        public Status status;

        public Result(Status status, String url, Bitmap bitmap, FaceCrop faceCrop, int maxHeight) {
            this.status = status;
            this.url = url;
            this.bitmap = bitmap;
            this.faceCrop = faceCrop;
            this.maxHeight = maxHeight;
        }

        public boolean isLandscape() {
            return bitmap.getWidth() > bitmap.getHeight() * 1.1f;
        }

        // おおよそ正方形かどうか
        public boolean isSquare() {
            float aspect = (float)(bitmap.getWidth()) / bitmap.getHeight();
            return Math.abs(aspect - 1.0f) < 0.2f;
        }

        public boolean isFaceDetected() {
            return faceCrop != null && faceCrop.isSuccess();
        }


    }


    protected void doInBackground() {
        for (String url : mUrls) {
            Bitmap bitmap = ImageLoader.getInstance().loadImageSync(url);
            if (bitmap != null) {
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                FaceCrop faceCrop = null;

                try {
                    if (!mEnableCrop || w == 0 || h == 0) {
                        continue;
                    }

                    if (BasicSettings.enableFaceDetection()) {
                        faceCrop = FaceCrop.get(url, w, h);
                        if (faceCrop != null) {
                            faceCrop.getFaceRect(bitmap);
                        };
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mBitmaps.add(new Result(mStatus, url, bitmap, faceCrop, mHeight));
                }

            }
        }
    }

    protected void onPostExecute() {
        // 元の ListItem が別の項目の表示に使われていないかのチェック
        Long id  = (Long) mViewGroup.getTag();
        Long oldId = mStatus.getId();
        if (id == null || !id.equals(oldId)) {
            // 既に List が他のアイテム用に再利用されている
            Log.w(TAG, String.format("onPostExecute: tag is not match: %d, %d", id, oldId));
            return;
        }

        // 画像を貼るスペースをクリア
        mViewGroup.removeAllViews();

        layoutThumbnails();

        mViewGroup.setVisibility(View.VISIBLE);
        mWrapperViewGroup.setVisibility(View.VISIBLE);
    }

    private void layoutThumbnails() {
        if (layoutFourThumbnailsInSquare()) {
            return;
        }

        if (layoutFourThumbnailsThreeColumns()) {
            return;
        }

        if (layoutFourThumbnailsTwoColumns()) {
            return;
        }

        layoutThumbnailsHorizontal();
    }

    // 水平レイアウト配置 (1枚、2～4枚横並び)
    private boolean layoutThumbnailsHorizontal() {
        Point viewSize = ImageUtil.getDisplaySize();

        int index = 0;
        for (final Result entry : mBitmaps) {
            RoundedImageView image = getRoundedImageView();
            LinearLayout.LayoutParams layoutParams =
                    new LinearLayout.LayoutParams(0, mHeight, 1.0f);

            if (mUrls.size() == 1) {
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
            } else {
                if (index == 0) {
                    layoutParams.setMargins(0, 0, 5, 0);
                } else if (index == mUrls.size() - 1) {
                    layoutParams.setMargins(5, 0, 0, 0);
                } else {
                    layoutParams.setMargins(5, 0, 5, 0);
                }
            }

            boolean cropByAspect = false;

            if (mUrls.size() > 3) {
                image.setMaxHeight(mHeight);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                layoutParams.gravity = Gravity.CENTER_VERTICAL;
                cropByAspect = true;
            } else {
                if (!mEnableCrop) {
                    image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                } else {
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }
            }

            float viewHeight = mHeight;
            float viewWidth = mViewGroup.getWidth() / (float)mUrls.size();
            if (mViewGroup.getWidth() == 0) {
                viewWidth = (viewSize.x - convertDp2Px(60.0f, mContext)) / (float)mUrls.size();
            }

            if (mUrls.size() == 1) {
                Space space = new Space(mContext);
                LinearLayout.LayoutParams dummyParams = new LinearLayout.LayoutParams(0, mHeight, 0.15f);
                mViewGroup.addView(image, layoutParams);
                mViewGroup.addView(space, dummyParams);
                viewWidth /= 1.15f;
            } else {
                mViewGroup.addView(image, layoutParams);
            }

            int nImages = mUrls.size();


            if (image.getScaleType() == ImageView.ScaleType.CENTER_CROP) {
                ImageUtil.setImageWithCrop(entry, image, false, viewHeight, viewWidth, nImages);
            } else {
                ImageUtil.setImageWithCrop(entry, image, cropByAspect, viewHeight, viewWidth, nImages);
            }

            setClickListener(index, image);
            index++;
        }

        return true;
    }

    private float convertDp2Px(float dp, Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return dp * metrics.density;
    }

    // 縦2 を含む4枚レイアウト
    private boolean layoutFourThumbnailsThreeColumns() {
        if (mBitmaps.size() != 4) {
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
        Point viewSize = ImageUtil.getDisplaySize();

        // タイルレイアウト
        LinearLayout view = new LinearLayout(mContext);
        view.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layoutParams2 =
                new LinearLayout.LayoutParams(0, maxHeight, 1.3f);

        if (pairPosition == 0) {
            layoutParams2.setMargins(0, 0, 5, 0);
        } else if (pairPosition == 1) {
            layoutParams2.setMargins(5, 0, 5, 0);
        } else if (pairPosition == 2) {
            layoutParams2.setMargins(5, 0, 0, 0);
        }
        view.setLayoutParams(layoutParams2);
        view.setMinimumHeight(maxHeight);

        int index = 0;
        int height = (maxHeight - 10) / 2;
        for (final Result entry : mBitmaps) {
            RoundedImageView image = getRoundedImageView();
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);

            LinearLayout.LayoutParams layoutParams = null;
            float viewHeight = maxHeight;

            if (pairPosition == index || pairPosition + 1 == index) {
                layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
                image.setMaxHeight(height);
                image.setMinimumHeight(height);
                viewHeight = height;
            } else {
                layoutParams = new LinearLayout.LayoutParams(0, maxHeight, 1.0f);
                image.setMaxHeight(maxHeight);
                image.setMinimumHeight(maxHeight);
            }
            layoutParams.gravity = Gravity.CENTER_VERTICAL;

            if (pairPosition == index) {
                layoutParams.setMargins(0, 0, 0, 10);
            } else if (pairPosition + 1 == index) {
                // NOP
            } else if (index == 0) {
                layoutParams.setMargins(0, 0, 5, 0);
            } else if (index == 3) {
                layoutParams.setMargins(5, 0, 0, 0);
            } else {
                layoutParams.setMargins(5, 0, 5, 0);
            }

            if (pairPosition == index || pairPosition + 1 == index) {
                view.addView(image, layoutParams);
            } else {
                mViewGroup.addView(image, layoutParams);
            }
            if (pairPosition == index) {
                mViewGroup.addView(view);
            }

//            float viewWidth = viewSize.x * 0.8f * 0.33f;
            float viewWidth = mViewGroup.getWidth() * 0.33f;
            int nImages = mUrls.size();

            ImageUtil.setImageWithCrop(entry, image, false, viewHeight, viewWidth, nImages);

            setClickListener(index, image);
            index++;
        }

        return true;
    }


    // 縦2 を含む3枚レイアウト
    // TODO: パラメータ抽出して ThreeColums と統合したい
    private boolean layoutFourThumbnailsTwoColumns() {
        if (mBitmaps.size() != 3) {
            return false;
        }

        float columnWeight = 0.5f;
        int START_INDEX = 0;
        int END_INDEX = 2;

        int pairPosition = -1;
        // TODO: 3枚とも Landscape の場合はどうする?
        if (mBitmaps.get(0).isLandscape() && mBitmaps.get(1).isLandscape()) {
            pairPosition = 0;
        } else if (mBitmaps.get(1).isLandscape() && mBitmaps.get(2).isLandscape()) {
            pairPosition = 1;
        } else {
            return false;
        }

        int maxHeight = mHeight;
        Point viewSize = ImageUtil.getDisplaySize();

        // タイルレイアウト
        LinearLayout view = new LinearLayout(mContext);
        view.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layoutParams2 =
                new LinearLayout.LayoutParams(0, maxHeight, 1.0f);

        if (pairPosition == START_INDEX) {
            layoutParams2.setMargins(0, 0, 5, 0);
        } else if (pairPosition + 1 == END_INDEX) {
            layoutParams2.setMargins(5, 0, 0, 0);
        }
        view.setLayoutParams(layoutParams2);
        view.setMinimumHeight(maxHeight);

        int index = 0;
        int height = (maxHeight - 10) / 2;
        for (final Result entry : mBitmaps) {
            RoundedImageView image = getRoundedImageView();
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);

            LinearLayout.LayoutParams layoutParams = null;
            float viewHeight = maxHeight;

            if (pairPosition == index || pairPosition + 1 == index) {
                layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
                image.setMaxHeight(height);
                image.setMinimumHeight(height);
                viewHeight = height;
            } else {
                layoutParams = new LinearLayout.LayoutParams(0, maxHeight, 1.0f);
                image.setMaxHeight(maxHeight);
                image.setMinimumHeight(maxHeight);
            }
            layoutParams.gravity = Gravity.CENTER_VERTICAL;

            if (pairPosition == index) {
                // 2段組みの上側
                layoutParams.setMargins(0, 0, 0, 10);
            } else if (pairPosition + 1 == index) {
                // 2段組みの下側
                // NOP
            } else if (index == START_INDEX) {
                layoutParams.setMargins(0, 0, 5, 0);
            } else if (index == END_INDEX) {
                layoutParams.setMargins(5, 0, 0, 0);
            } else {
                layoutParams.setMargins(5, 0, 5, 0);
            }

            if (pairPosition == index || pairPosition + 1 == index) {
                // 2段組み
                view.addView(image, layoutParams);
            } else {
                mViewGroup.addView(image, layoutParams);
            }
            if (pairPosition == index) {
                mViewGroup.addView(view);
            }

//            float viewWidth = viewSize.x * 0.8f * columnWeight;
            float viewWidth = mViewGroup.getWidth() * columnWeight;
            int nImages = mUrls.size();

            ImageUtil.setImageWithCrop(entry, image, false, viewHeight, viewWidth, nImages);

            setClickListener(index, image);
            index++;
        }

        return true;
    }

    // 縦2、横2 の4枚レイアウト
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
        Point viewSize = ImageUtil.getDisplaySize();

        // 4枚タイルレイアウト
        LinearLayout views[] = new LinearLayout[] { new LinearLayout(mContext), new LinearLayout(mContext) };
        boolean isLeft = true;
        for (LinearLayout view : views) {
            view.setOrientation(LinearLayout.VERTICAL);
            // LinearLayout の weight は各子要素の幅の合計 - 親の幅の差分を weight で配分なので、純粋に割合で割り当てたいときは幅を 0　にしておけばよい
            LinearLayout.LayoutParams layoutParams =
                    new LinearLayout.LayoutParams(0, maxHeight, 1.0f);
            if (isLeft) {
                isLeft = false;
                layoutParams.setMargins(0, 0, 10, 0);
            }
            mViewGroup.addView(view, layoutParams);
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

            float viewHeight = height;
            float viewWidth = mViewGroup.getWidth() * 0.5f;
 //           float viewWidth = viewSize.x * 0.8f * 0.5f;
            int nImages = mUrls.size();

            ImageUtil.setImageWithCrop(entry, image, cropByAspect, viewHeight, viewWidth, nImages);

            setClickListener(index, image);
            index++;
        }


        return true;
    }

    private void setClickListener(final int index, RoundedImageView image) {
        final String videoUrl = StatusUtil.getVideoUrl(mStatus);

        if (videoUrl.isEmpty()) {
            // 画像タップで拡大表示（ピンチイン・ピンチアウトいつかちゃんとやる）
            image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), ScaleImageActivity.class);
                    intent.putExtra("status", mStatus);
                    intent.putExtra("index", index);
                    mContext.startActivity(intent);
                }
            });
        } else {
            // 画像タップで拡大表示（ピンチイン・ピンチアウトいつかちゃんとやる）
            image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), VideoActivity.class);
                    intent.putExtra("videoUrl", videoUrl);
                    mContext.startActivity(intent);
                }
            });

        }
    }

    @NonNull
    private RoundedImageView getRoundedImageView() {
        RoundedImageView image = new RoundedImageView(mContext);
        image.setMinimumHeight(100); // 極端に小さい画像対応
        image.setCornerRadius(25.0f);
        image.setBorderWidth(2.0f);
        image.setBorderColor(Color.DKGRAY);
        return image;
    }
}

