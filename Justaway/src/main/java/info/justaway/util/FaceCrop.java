package info.justaway.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.v4.text.TextUtilsCompat;
import android.text.TextUtils;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import info.justaway.R;
import info.justaway.settings.BasicSettings;

public class FaceCrop {
    private static final String TAG = FaceCrop.class.getSimpleName();

    private boolean mIsFirst = true;
    private boolean mIsSuccess;
    private float mWidth;
    private float mHeight;
    private Rect mRect;
    private Mat mSkin;
    private String mTag = "";
    private long mProcessTime = 0;
    private Paint mPaintText = new Paint();
    private List<Rect> mRectsOrig = new ArrayList<>();
    private List<Rect> mRects = new ArrayList<>();
    private int mColor = Color.MAGENTA;

    private static CascadeClassifier sFaceDetectorAnimeFace = null;
    private static CascadeClassifier sFaceDetectorFace = null;
    private static CascadeClassifier sFaceDetectorAnimeProfileFace = null;
    private static CascadeClassifier sFaceDetector_Cat = null;

    private static Map<String, FaceCrop> sFaceInfoMap = new LinkedHashMap<String, FaceCrop>(100, 0.75f, true) {
        private static final int MAX_ENTRIES = 100;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FaceCrop> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public static void initFaceDetector(Context context) {
        if (sFaceDetectorAnimeFace == null) {
            sFaceDetectorAnimeFace = setupFaceDetector(context, "lbpcascade_animeface.xml", R.raw.lbpcascade_animeface);
        }
        if (sFaceDetectorAnimeProfileFace == null) {
            sFaceDetectorAnimeProfileFace = setupFaceDetector(context, "lbpcascade_animeprofileface.xml", R.raw.lbpcascade_animeprofileface);
        }
        if (sFaceDetectorFace == null) {
            sFaceDetectorFace = setupFaceDetector(context, "lbpcascade_frontalface_improved.xml", R.raw.lbpcascade_frontalface_improved);
        }
        if (sFaceDetector_Cat == null) {
            sFaceDetector_Cat = setupFaceDetector(context, "lbpcascade_cat.xml", R.raw.lbpcascade_cat);
        }
    }

    private static File setupCascadeFile(Context context, String fileName, int xml) {
        File cascadeDir = context.getFilesDir();
        File cascadeFile = null;
        InputStream is = null;
        FileOutputStream os = null;
        try {
            cascadeFile = new File(cascadeDir, fileName);
            if (!cascadeFile.exists()) {
                is = context.getResources().openRawResource(xml);
                os = new FileOutputStream(cascadeFile);
                byte[] buffer = new byte[4096];
                int readLen;
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

    private static CascadeClassifier setupFaceDetector(Context context, String fileName, int xml) {
        File cascadeFile = setupCascadeFile(context, fileName, xml);
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

    private FaceCrop(float w, float h) {
        this.mWidth = w;
        this.mHeight = h;
        mPaintText.setColor(Color.BLACK);
        mPaintText.setTextSize(30);
    }

    private BitmapWrapper drawFaceRegion(Rect rect, BitmapWrapper image, int color) {
        List<Rect> list = new ArrayList<>();
        list.add(rect);
        return drawFaceRegions(list, image, color);
    }

    private BitmapWrapper drawFaceRegions(List<Rect> rects, BitmapWrapper image, int color) {
        try {
            BitmapWrapper result = new BitmapWrapper(image.getBitmap().copy(Bitmap.Config.ARGB_8888, true), true);
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(4);
            paint.setStyle(Paint.Style.STROKE);

            Canvas canvas = new Canvas(result.getBitmap());
            for (Rect rect : rects) {
                canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, paint);
            }
            drawTag(canvas);
            image.recycle();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return image;
        }
    }

    private void drawTag(Canvas canvas) {
        if (mRect != null) {
            String text = String.format("%s (%dms)", mTag, mProcessTime);

            int x = mRect.x;
            int y = mRect.y + mRect.height;

            mPaintText.setStrokeWidth(10);
            mPaintText.setStyle(Paint.Style.STROKE);
            mPaintText.setColor(Color.WHITE);
            canvas.drawText(text, x, y, mPaintText);

            mPaintText.setStrokeWidth(0);
            mPaintText.setStyle(Paint.Style.FILL);
            mPaintText.setColor(Color.BLACK);
            canvas.drawText(text, x, y, mPaintText);

        }
    }

    private static BitmapWrapper drawSkinArea(BitmapWrapper image, Mat skin) {
        if (skin == null) {
            return image;
        }
        try {
            Mat imageMat = image.createMat();
            Mat outMat = Mat.zeros(new Size((int)image.getWidth(), (int)image.getHeight()), CvType.CV_8UC3);
            //Core.bitwise_and(imageMat, imageMat, outMat, skin);
//            imageMat.copyTo(outMat, skin);
            Imgproc.cvtColor(skin, outMat, Imgproc.COLOR_GRAY2RGBA);
            image.setMat(outMat);
            return image;
        } catch (Exception e) {
            e.printStackTrace();
            return image;
        }
    }

    @SuppressWarnings("unused")
    private Bitmap drawFaceRegions(List<Rect> rects, Bitmap image, int color) {
        try {
            Bitmap result = image.copy(Bitmap.Config.ARGB_8888, true);
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(4);
            paint.setStyle(Paint.Style.STROKE);

            Canvas canvas = new Canvas(result);
            for (Rect rect : rects) {
                canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, paint);
            }
            drawTag(canvas);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return image;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public BitmapWrapper drawRegion(BitmapWrapper bitmap) {
        if (mIsSuccess) {
            if (BasicSettings.isDebug()) {
                // bitmap = drawSkinArea(bitmap, mSkin);
                bitmap = drawFaceRegions(mRectsOrig, bitmap, Color.YELLOW);
                bitmap = drawFaceRegions(mRects, bitmap, mColor);
                return bitmap;
            }
        }
        return bitmap;
    }

    private class DetectorConf {
        DetectorConf(CascadeClassifier detector, double angle, double scale, int neighbor, Size size, int color, boolean flip, boolean multi, String tag) {
            this.detector = detector;
            this.angle = angle;
            this.scale = scale;
            this.neighbor = neighbor;
            this.size = size;
            this.color = color;
            this.flip = flip;
            this.multi = multi;
            this.tag = tag;
        }
        CascadeClassifier detector;
        double angle;
        double scale;
        int neighbor;
        public Size size;
        public int color;
        boolean flip;
        boolean multi;
        String tag;
    }

    @SuppressWarnings("WeakerAccess")
    public Rect getFaceRect() {
        if (mRect == null) {
            return new Rect();
        } else {
            return new Rect(mRect.x, mRect.y, mRect.width, mRect.height);
        }
    }

    public Rect getFaceRect(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() * bitmap.getHeight() == 0) {
            return getFaceRect();
        }

        if (!mIsFirst) {
            return getFaceRect();
        }

        if (sFaceDetectorAnimeFace == null) {
            return getFaceRect();
        }

        long startTime = System.currentTimeMillis();
        mIsFirst = false;
        mRect = null;
        mRects.clear();
        mRectsOrig.clear();
        mIsSuccess = false;

        DetectorConf[] confs = new DetectorConf[] {
                new DetectorConf(sFaceDetectorAnimeFace,   0, 1.09f, 3, new Size(40, 40), Color.MAGENTA, false, true, "Anime"),
                new DetectorConf(sFaceDetectorAnimeFace,  10, 1.09f, 3, new Size(40, 40), Color.MAGENTA, false, true, "Anime+10"),
                new DetectorConf(sFaceDetectorAnimeFace, -10, 1.09f, 3,  new Size(40, 40),Color.MAGENTA, false, true, "Anime-10"),
                new DetectorConf(sFaceDetectorAnimeProfileFace, 0, 1.1f, 2,  new Size(40, 40),Color.BLACK, false, true, "AnimeP"),
                new DetectorConf(sFaceDetectorAnimeProfileFace, 0, 1.1f, 2,  new Size(40, 40),Color.BLACK, true, true, "AnimeP(R)"),
                new DetectorConf(sFaceDetectorFace,   0, 1.09f, 3,  new Size(40, 40),Color.BLUE, false, false, "NormalFace"),
                new DetectorConf(sFaceDetector_Cat,   0, 1.09f, 3, new Size(40, 40), Color.BLUE, false, false, "Cat")
        };

        try {
            Mat imageMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
            Mat gray = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(1));
            Utils.bitmapToMat(bitmap, imageMat);

            // グレースケール化 (色情報はいらない)
            Imgproc.cvtColor(imageMat, gray, Imgproc.COLOR_RGB2GRAY);
            // ヒストグラム均一化
            Imgproc.equalizeHist(gray, gray);

            double scale = mWidth * mHeight > 500 * 500 ? 0.5f : 1.0f;
            if (scale < 1.0f) {
                Imgproc.resize(gray, gray, new Size(mWidth * scale, mHeight * scale));
            }

            for (DetectorConf conf : confs) {
                if (!conf.multi && mIsSuccess) {
                    break;
                }
                mColor = conf.color;
                Rect ret = getFaceRectImpl(gray, conf, scale);
                // 1回の検出に 200ms ぐらいかかるので複数の結果をマージすると常に遅くなる
                if (mIsSuccess) {
                    return getFaceRect();
                }
            }
            if (mIsSuccess) {
                return getFaceRect();
            }

            //return getSkinRect(imageMat);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            long endTime = System.currentTimeMillis();
            mProcessTime = endTime - startTime;
        }

        return null;
    }

    private Rect getFaceRectImpl(Mat imageMat, DetectorConf conf, double scale) {
        if (conf.detector == null) {
            return null;
        }

        try {
            MatOfRect faces = new MatOfRect();
            Mat rotMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));

            // 回転 (今のところ回転と左右反転は同時につかえない)
            if (conf.angle != 0) {
                Mat rot = Imgproc.getRotationMatrix2D(new Point(mWidth * scale / 2, mHeight * scale / 2), conf.angle, 1.0f);
                Imgproc.warpAffine(imageMat, rotMat, rot, imageMat.size());
            } else if (conf.flip) {
                Core.flip(imageMat, rotMat, 1);
            } else {
                rotMat = imageMat.clone();
            }


            Log.d(TAG, String.format("image: (%s, %s)", mWidth, mHeight));
            Log.d(TAG, String.format("rotMat: (%d, %d) : angle %s", rotMat.cols(), rotMat.rows() , conf.angle));
            conf.detector.detectMultiScale(rotMat, faces, conf.scale, conf.neighbor, 0, conf.size, new Size());
            Rect[] facesArray = faces.toArray();

            if (conf.angle != 0 || scale != 1.0f || conf.flip) {
                // 回転復元
                for (Rect r : facesArray) {
                    if (scale != 1.0f) {
                        r.x /= scale;
                        r.y /= scale;
                        r.width /= scale;
                        r.height /= scale;
                    }
                    if (conf.angle != 0) {
                        Point inPoint = r.tl();
                        inPoint.x += r.width / 2;
                        inPoint.y += r.height / 2;
                        Log.d(TAG, String.format("face area org: (%d, %d, %d, %d) : angle %s", r.x, r.y, r.width, r.height, conf.angle));
                        Point outPoint = rotatePoint(inPoint, new Point(mWidth/2, mHeight/2), conf.angle);
                        outPoint.x -= r.width / 2;
                        outPoint.y -= r.height / 2;
                        r.x = (int) outPoint.x;
                        r.y = (int) outPoint.y;
                    }
                    // 左右反転復元
                    if (conf.flip) {
                        r.x = (int)mWidth - r.x - r.width;
                    }
                }
            }

            Collections.addAll(mRectsOrig, facesArray);
            facesArray = filterFaceRects(facesArray, mWidth, mHeight);
            facesArray = mergeFaceRects(facesArray);

            if (facesArray.length > 0) {
                Rect r = getLargestFace(facesArray);
                Log.d(TAG, String.format("face area: (%d, %d, %d, %d) : angle %s", r.x, r.y, r.width, r.height, conf.angle));
                if (mRect == null) {
                    mRect = r;
                }
                Collections.addAll(mRects, facesArray);
                mIsSuccess = true;
                if (TextUtils.isEmpty(mTag)) {
                    mTag = conf.tag;
                }
                if (conf.angle != 0) {
                    mColor = Color.rgb(255, 153, 0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mRect;
    }

    private Rect getSkinRect(Mat src) {
        mSkin = null;
        Mat hsv = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(3));
        Imgproc.cvtColor(src, hsv, Imgproc.COLOR_RGB2HSV);
        Core.inRange(hsv, new Scalar(0, 10, 60), new Scalar(40, 155, 255), hsv);
        int area = Core.countNonZero(hsv);
        if (area < 60 * 60) {
            return getFaceRect();
        }

        float ratio = (float)area / (float)src.size().width / (float)src.size().height;
        if (ratio > 0.55f) {
            return getFaceRect();
        }

        Moments mu = Imgproc.moments(hsv);
        int cx = (int)(mu.get_m10() / mu.get_m00());
        int cy = (int)(mu.get_m01() / mu.get_m00());
        int w = (int)(Math.sqrt(area) / 2);

        Rect ret = new Rect();
        ret.x = (int)(Math.max(0, cx - w) );
        ret.y = (int)(Math.max(0, cy - w) );
        ret.width = (int)(w * 2 );
        ret.height = (int)(w * 2 );

        if (ret.br().x > mWidth) {
            ret.width = (int)mWidth - ret.x;
        }
        if (ret.br().y > mHeight) {
            ret.height = (int)mHeight - ret.y;
        }

        if (mRect != null) {
            mRect = ret;
        }
        mRects.add(ret);
        mColor = Color.rgb(0, 0, 0);
        mIsSuccess = true;

        mSkin = hsv;
        return getFaceRect();
    }

    // 重なっている領域を合成する
    private Rect[] mergeFaceRects(Rect[] facesArray) {
        return mergeFaceRects(facesArray, 1.0f, 0.5f);
    }
        // 左右マージン付きで領域合成
    private Rect[] mergeFaceRects(Rect[] facesArray, float marginRateX, float marginRateY) {
        ArrayList<Rect> result = new ArrayList<>();
        HashMap<Integer, Integer> map = new HashMap<>(); // 領域ごとにつけたラベル

        for (int i=0; i<facesArray.length; i++) {
            map.put(i, i);
        }

        for (int i=0; i<facesArray.length; i++) {
            Rect r1 = facesArray[i];
            android.graphics.Rect gr1 = new android.graphics.Rect((int)r1.tl().x, (int)r1.tl().y, (int)r1.br().x, (int)r1.br().y);

            for (int j=i+1; j<facesArray.length; j++) {
                Rect r2 = facesArray[j];
                android.graphics.Rect gr2 = new android.graphics.Rect((int)r2.tl().x, (int)r2.tl().y, (int)r2.br().x, (int)r2.br().y);
                if (horizontalIntersects(gr1, gr2, marginRateX) || verticalIntersects(gr1, gr2, marginRateY)) {
                    int labelMin = Math.min(map.get(i), map.get(j));
                    int labelMax = Math.max(map.get(i), map.get(j));
                    // labelMax をすべて labelMin に書き換える
                    for (int k=0; k<facesArray.length; k++) {
                        if (map.get(k) == labelMax) {
                            map.put(k, labelMin);
                        }
                    }
                }
            }
        }

        // 同じラベルがついた領域をマージする
        for (int i=0; i<facesArray.length; i++) {
            android.graphics.Rect gr1 = null;
            for (int j=0; j<facesArray.length; j++) {
                int label = map.get(j);
                if (label == i) {
                    Rect r2 = facesArray[j];
                    android.graphics.Rect gr2 = new android.graphics.Rect((int)r2.tl().x, (int)r2.tl().y, (int)r2.br().x, (int)r2.br().y);
                    if (gr1 == null) {
                        gr1 = gr2;
                    } else {
                        gr1.union(gr2);
                    }
                }
            }
            if (gr1 != null) {
                Rect r = createRect(gr1);
                result.add(r);
            }
        }

        return result.toArray(new Rect[0]);
    }

    @NonNull
    private Rect createRect(android.graphics.Rect rect) {
        Rect tmp = new Rect();
        tmp.x = rect.left;
        tmp.y = rect.top;
        tmp.width = rect.width();
        tmp.height = rect.height();
        return tmp;
    }

    private boolean horizontalIntersects(android.graphics.Rect a, android.graphics.Rect b, float marginRate) {
        float ma = a.width() * marginRate;
        float mb = b.width() * marginRate;
        return a.left - ma < b.right + mb && b.left - mb< a.right + ma && a.top  < b.bottom && b.top < a.bottom;
    }

    private boolean verticalIntersects(android.graphics.Rect a, android.graphics.Rect b, float marginRate) {
        float ma = a.height() * marginRate;
        float mb = b.height() * marginRate;
        return a.top - ma < b.bottom + mb && b.top - mb< a.bottom + ma && a.left  < b.right && b.left < a.right;
    }

        // 顔領域として不適切な位置にある領域をフィルタする
    @SuppressWarnings("UnusedParameters")
    private Rect[] filterFaceRects(Rect[] facesArray, float width, float height) {
        ArrayList<Rect> result = new ArrayList<>();
        int bottomArea = (int)(height * 0.6f);
        for (Rect r : facesArray) {
            // 画面の下部にある領域は無視
            if (r.y > bottomArea) {
                continue;
            }
            result.add(r);
        }
        return result.toArray(new Rect[0]);
    }

    private Point rotatePoint(Point point, Point center, double angle) {
        double rad = angle * Math.PI / 180.0;
        Point inPoint = new Point(point.x - center.x, point.y - center.y);
        Point outPoint = new Point();
        outPoint.x = Math.cos(rad) * inPoint.x - Math.sin(rad) * inPoint.y + center.x;
        outPoint.y = Math.sin(rad) * inPoint.x + Math.cos(rad) * inPoint.y + center.y;
        return outPoint;
    }

    // 指定したサイズで顔領域をクロップした画像を返す
    @SuppressWarnings("WeakerAccess")
    public BitmapWrapper cropFace2(BitmapWrapper image, int viewWidth, int viewHeight) {
        Rect r = getFaceRect();
        if (BasicSettings.isDebug()) {
            image = drawFaceRegion(r, image, Color.YELLOW);
        }
        enlargeRect(r, (int)image.getWidth(), (int)image.getHeight());
        adjustRect(r, viewWidth, viewHeight, (int)image.getWidth(), (int)image.getHeight());
        if (BasicSettings.isDebug()) {
            image = drawFaceRegion(r, image, mColor);
        }
        Bitmap cropped = Bitmap.createBitmap(image.getBitmap(), r.x, r.y, r.width, r.height);
        image.setBitmap(cropped, true);
        return image;
    }



    // 描画領域のアスペクト比に合わせてクロップ領域を調整
    private static void adjustRect(Rect rect, int viewWidth, int viewHeight, int bitmapWidth, int bitmapHeight) {
        float widgetAspect = (float)viewWidth / (float) viewHeight;
        float rectAspect = (float)rect.width / (float)rect.height;
        if (widgetAspect > rectAspect) {
            int w = (int)(rect.height * widgetAspect);
            w = Math.min(bitmapWidth, w);
            int diff = w - rect.width;
            if (rect.x < diff / 2) {
                rect.x = 0;
            } else {
                rect.x -= diff / 2;
            }
            rect.width = w;
            if (bitmapWidth < rect.x + rect.width) {
                rect.width = bitmapWidth - rect.x;
            }
        } else {
            int h = (int)(rect.width / widgetAspect);
            h = Math.min(bitmapHeight, h);
            int diff = h - rect.height;
            if (rect.y < diff / 2) {
                rect.y = 0;
            } else {
                rect.y -= diff / 2;
            }
            rect.height = h;
            if (bitmapHeight < rect.y + rect.height) {
                rect.height = bitmapHeight - rect.y;
            }
        }
    }

    // 顔領域をクロップした画像を返す
    @SuppressWarnings("WeakerAccess")
    public BitmapWrapper cropFace(BitmapWrapper bitmap, float aspect, int viewWidth, int viewHeight) {
        if (!mIsSuccess) {
            return bitmap;
        }
        try {
            int bitmapWidth = (int)bitmap.getWidth();
            int bitmapHeight = (int)bitmap.getHeight();
            float bitmapAspect = bitmapHeight / (float)bitmapWidth;

            if (BasicSettings.isDebug()) {
                bitmap = drawFaceRegions(mRects, bitmap, mColor);
            }

            Rect r = getFaceRect();
            if (bitmapAspect > aspect) {
                //r = addVPadding(r, bitmap.getBitmap(), (int) (w * aspect));
                enlargeRect(r, bitmapWidth, bitmapHeight);
                adjustRect(r, viewWidth, viewHeight, bitmapWidth, bitmapHeight);
//                Bitmap cropped = Bitmap.createBitmap(bitmap.getBitmap(), 0, r.y, (int) w, r.height);
                Bitmap cropped = Bitmap.createBitmap(bitmap.getBitmap(), r.x, r.y, r.width, r.height);
                bitmap.setBitmap(cropped, true);
                return bitmap;
            } else {
//                r = addHPadding(r, bitmap.getBitmap(), (int) (bitmapHeight / aspect));
                enlargeRect(r, bitmapWidth, bitmapHeight);
                adjustRect(r, viewWidth, viewHeight, bitmapWidth, bitmapHeight);
//                Bitmap resized = Bitmap.createBitmap(bitmap.getBitmap(), r.x, 0, r.width, (int) h);
                Bitmap cropped = Bitmap.createBitmap(bitmap.getBitmap(), r.x, r.y, r.width, r.height);
                bitmap.setBitmap(cropped, true);
                return bitmap;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    private void enlargeRect(Rect r, int bitmapWidth, int bitmapHeight) {
        float xRate = r.width / (float)bitmapWidth;
        float yRate = r.height / (float)bitmapHeight;
        float SCALE1 = 0.3f;
        float SCALE2 = 0.2f;
        float SCALE3 = 0.1f;
        float xScale = xRate < 0.2 ? SCALE1 : (xRate  < 0.4 ? SCALE2 : SCALE3);
        float xPadding = bitmapWidth * xScale;
        float yScale = yRate < 0.2 ? SCALE1 : (yRate  < 0.4 ? SCALE2 : SCALE3);
        float yPadding = bitmapHeight * yScale;
        r.y -= yPadding / 2;
        r.height += yPadding;
        if (r.y < 0) {
            r.height += r.y;
            r.y = 0;
        }
        int bottomExcess =r.y + r.height - bitmapHeight;

        if (bottomExcess > 0) {
            r.y -= bottomExcess;
            if (r.y < 0) {
                r.y = 0;
            }
            r.height = bitmapHeight - r.y;
        }

        r.x -= xPadding / 2;
        r.width += xPadding;
        if (r.x < 0) {
            r.width += r.x;
            r.x = 0;
        }
        int rightExcess =r.x + r.width - bitmapWidth;

        if (rightExcess > 0) {
            r.x -= rightExcess;
            if (r.x < 0) {
                r.x = 0;
            }
            r.width = bitmapWidth - r.x;
        }
    }

    static private Rect addVPadding(Rect r, Bitmap bitmap, int maxHeight) {
        int padding = r.height < maxHeight ? maxHeight - r.height : (int)(r.height * 0.2f);
        r.y -= padding / 2;
        r.height += padding;
        if (r.y < 0) {
            r.y = 0;
        }
        int bottomExcess =r.y + r.height - bitmap.getHeight();

        if (bottomExcess > 0) {
            r.y -= bottomExcess;
            if (r.y < 0) {
                r.y = 0;
            }
            r.height = bitmap.getHeight() - r.y;
        }

        return r;
    }

    static private Rect addHPadding(Rect r, Bitmap bitmap, int maxWidth) {
        int padding = r.width < maxWidth ? maxWidth - r.width : (int)(r.width * 0.2f);
        r.x -= padding / 2;
        r.width += padding;
        if (r.x < 0) {
            r.x = 0;
        }
        int rightExcess =r.x + r.width - bitmap.getWidth();

        if (rightExcess > 0) {
            r.x -= rightExcess;
            if (r.x < 0) {
                r.x = 0;
            }
            r.width = bitmap.getWidth() - r.x;
        }
        return r;
    }

    private Rect getLargestFace(Rect[] facesArray) {
        Rect ret = null;
        int maxSize = -1;
        for (Rect r : facesArray) {
            double yweight = 1.0f + ((mHeight - r.y) / mHeight * 5.0f); // 上にある領域を優先
            int size = (int)(r.width * r.height * yweight * yweight);
            if (size > maxSize) {
                ret = r;
                maxSize = size;
            }
        }
        return ret;
    }

    public static FaceCrop get(String imageUri, float w, float h) {
        FaceCrop faceCrop = sFaceInfoMap.get(imageUri);
        if (faceCrop == null) {
            faceCrop = new FaceCrop(w, h);
            sFaceInfoMap.put(imageUri, faceCrop);
        }
        return faceCrop;
    }

    public boolean isSuccess() {
        return mIsSuccess;
    }
}
