package info.justaway.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

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
import java.util.LinkedHashMap;
import java.util.Map;

import info.justaway.R;
import info.justaway.settings.BasicSettings;

public class FaceCrop {
    private static final String TAG = FaceCrop.class.getSimpleName();

    private boolean mIsFirst = true;
    private boolean mIsSuccess;
    private int mMaxHeight;
    private float mWidth;
    private float mHeight;
    private Rect mRect;

    private static CascadeClassifier sFaceDetector = null;
    private static Map<String, FaceCrop> sFaceInfoMap = new LinkedHashMap<String, FaceCrop>(100, 0.75f, true) {
        private static final int MAX_ENTRIES = 100;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FaceCrop> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public static void initFaceDetector(Context context) {
        if (sFaceDetector == null) {
            sFaceDetector = setupFaceDetector(context);
        }
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

    public Rect getFaceRect(Bitmap bitmap) {
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

        return mRect;
    }

    static public Bitmap cropFace(Bitmap bitmap, Rect rect, int maxHeight) {
        if (rect == null) {
            return bitmap;
        }

        int c = Color.MAGENTA;
        int w = bitmap.getWidth();

        if (BasicSettings.isDebug()) {
            bitmap = drawFaceRegion(rect, bitmap, c);
        }
        Rect r = new Rect(rect.x, rect.y, rect.width, rect.height);
        r = addVPadding(r, bitmap, maxHeight);
        Bitmap resized = Bitmap.createBitmap(bitmap, 0, r.y, w, r.height);
        return resized;

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
            r = addVPadding(r, bitmap, mMaxHeight);
            Bitmap resized = Bitmap.createBitmap(bitmap, 0, r.y, (int) mWidth, r.height);
            return resized;
        } else {
            return null;
        }
    }

    static private Rect addVPadding(Rect r, Bitmap bitmap, int maxHeight) {
        // 大きいときに周囲にパディングいれても横幅からの比で影響しないことが多いが一応
        int padding = r.height < maxHeight ? maxHeight - r.height : (int)(r.height * 0.2f);
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

    public static FaceCrop get(String imageUri, int maxHeight, float w, float h) {
        FaceCrop faceCrop = sFaceInfoMap.get(imageUri);
        if (faceCrop == null) {
            faceCrop = new FaceCrop(maxHeight, w, h);
            sFaceInfoMap.put(imageUri, faceCrop);
        }
        return faceCrop;
    }
}
