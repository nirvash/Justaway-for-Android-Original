package info.justaway.task;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.deque.LIFOLinkedBlockingDeque;

import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import info.justaway.settings.BasicSettings;
import info.justaway.util.FaceCrop;
import info.justaway.util.ImageUtil;

public abstract class LoadImageTask implements Runnable {
    boolean mHasCache = false;
    private static Executor sLoadImageExecutor;
    private static Handler sHandler;

    abstract protected void onPostExecute();

    public void execute() {
        mHasCache = true;
        for (String url : mUrls) {
            if (!ImageUtil.hasCache(url)) {
                mHasCache = false;
                break;
            }
        }

        if (mHasCache) {
            sLoadImageExecutor.execute(this);
//            run();
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
        public Rect rect;
        public int maxHeight;

        public Result(String url, Bitmap bitmap, Rect rect, int maxHeight) {
            this.url = url;
            this.bitmap = bitmap;
            this.rect = rect;
            this.maxHeight = maxHeight;
        }

        public boolean isLandscape() {
            if (bitmap.getWidth() > bitmap.getHeight()) {
                return true;
            }
            return false;
        }
    }

    protected boolean mEnableCrop = false;
    protected List<String> mUrls;
    protected int mHeight;

    protected ArrayList<Result> mBitmaps = new ArrayList<>();

    public LoadImageTask(List<String> urls, boolean enableCrop, int height) {
        mUrls = urls;
        mEnableCrop = enableCrop;
        mHeight = height;
    }

    protected void doInBackground() {
        for (String url : mUrls) {
            Bitmap bitmap = ImageLoader.getInstance().loadImageSync(url);
            if (!mHasCache && false) {
                try {
                    Thread.sleep(300, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (bitmap != null) {
                int maxHeight = 300;
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                Rect r = null;

                try {
                    if (!mEnableCrop || w == 0 || h == 0) {
                        return;
                    }

                    Bitmap cropImage = null;
                    if (BasicSettings.enableFaceDetection()) {
                        FaceCrop faceCrop = FaceCrop.get(url, maxHeight, w, h);
                        r = faceCrop != null ? faceCrop.getFaceRect(bitmap) : null;
                    }
                } catch (Exception e) {

                } finally {
                    mBitmaps.add(new Result(url, bitmap, r, maxHeight));
                }

            }
        }
    }
}

