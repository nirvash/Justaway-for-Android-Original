package info.justaway.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    private List<Rect> mRects = new ArrayList<>();
    private int mColor = Color.MAGENTA;
    private boolean mIsFace = false;

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

    private static Bitmap drawFaceRegions(List<Rect> rects, Bitmap image, int color) {
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
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return image;
        }
    }


    public Bitmap drawRegion(Bitmap bitmap) {
        if (mIsSuccess) {
            if (BasicSettings.isDebug()) {
//                bitmap = drawFaceRegion(mRect, bitmap, Color.GREEN);
                bitmap = drawFaceRegions(mRects, bitmap, mColor);
                return bitmap;
            }
        }
        return bitmap;
    }

    private class DetectorConf {
        public DetectorConf(CascadeClassifier detector, double angle, double scale, int neighbor, Size size, int color, boolean flip, String tag) {
            this.detector = detector;
            this.angle = angle;
            this.scale = scale;
            this.neighbor = neighbor;
            this.size = size;
            this.color = color;
            this.flip = flip;
            this.tag = tag;
        }
        public CascadeClassifier detector;
        public double angle;
        public double scale;
        public int neighbor;
        public Size size;
        public int color;
        public boolean flip;
        public String tag;
    }


    public Rect getFaceRect(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() * bitmap.getHeight() == 0) {
            return mRect;
        }

        if (!mIsFirst) {
/*
            if (mColor == Color.MAGENTA || mColor == Color.GREEN) {
                mColor = Color.GREEN;
            } else {
                mColor = Color.CYAN;
            }
*/
            return mRect;
        }

        if (sFaceDetectorAnimeFace == null) {
            return mRect;
        }

        mIsFirst = false;
        DetectorConf[] confs = new DetectorConf[] {
                new DetectorConf(sFaceDetectorAnimeFace,   0, 1.09f, 3, new Size(40, 40), Color.MAGENTA, false, "A"),
                new DetectorConf(sFaceDetectorAnimeFace,  10, 1.09f, 3, new Size(40, 40), Color.MAGENTA, false, "A10"),
                new DetectorConf(sFaceDetectorAnimeFace, -10, 1.09f, 3,  new Size(40, 40),Color.MAGENTA, false, "A-10"),
                new DetectorConf(sFaceDetectorAnimeProfileFace, 0, 1.1f, 2,  new Size(40, 40),Color.BLACK, false, "AP"),
                new DetectorConf(sFaceDetectorAnimeProfileFace, 0, 1.1f, 2,  new Size(40, 40),Color.BLACK, true, "APF"),
                new DetectorConf(sFaceDetectorFace,   0, 1.09f, 3,  new Size(40, 40),Color.BLUE, false, "F"),
                new DetectorConf(sFaceDetector_Cat,   0, 1.09f, 3, new Size(40, 40), Color.BLUE, false, "C")
        };

        try {
            Mat imageMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
            Utils.bitmapToMat(bitmap, imageMat);

            // グレースケール化 (色情報はいらない)
            Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_RGB2GRAY);
            // ヒストグラム均一化
            Imgproc.equalizeHist(imageMat, imageMat);

            double scale = mWidth * mHeight > 500 * 500 ? 0.5f : 1.0f;
            if (scale < 1.0f) {
                Imgproc.resize(imageMat, imageMat, new Size(mWidth * scale, mHeight * scale));
            }

            for (DetectorConf conf : confs) {
                mColor = conf.color;
                Rect ret = getFaceRectImpl(imageMat, conf, scale);
                if (ret != null) {
                    return ret;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public Rect getFaceRectImpl(Mat imageMat, DetectorConf conf, double scale) {
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

            if (facesArray.length > 0) {
                Rect r = getLargestFace(facesArray);
                Log.d(TAG, String.format("face area: (%d, %d, %d, %d) : angle %s", r.x, r.y, r.width, r.height, conf.angle));
                mRect = r;
                mRects.clear();
                Collections.addAll(mRects, facesArray);
                mIsFace = true;
                mIsSuccess = true;
                if (conf.angle != 0) {
                    mColor = Color.rgb(255, 153, 0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mRect;
    }

    private Point rotatePoint(Point point, Point center, double angle) {
        double rad = angle * Math.PI / 180.0;
        Point inPoint = new Point(point.x - center.x, point.y - center.y);
        Point outPoint = new Point();
        outPoint.x = Math.cos(rad) * inPoint.x - Math.sin(rad) * inPoint.y + center.x;
        outPoint.y = Math.sin(rad) * inPoint.x + Math.cos(rad) * inPoint.y + center.y;
        return outPoint;
    }

    private Rect getGravityCenter(Bitmap bitmap) {
        MatOfRect faces = new MatOfRect();
        Mat imageMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
        Mat mask = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
        Mat hadairo = Mat.zeros((int) mHeight, (int) mWidth, CvType.CV_8U);

        Utils.bitmapToMat(bitmap, imageMat);

        Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_RGB2HSV);
        Imgproc.medianBlur(imageMat, imageMat, 3);

        Bitmap dst = Bitmap.createBitmap(imageMat.width(), imageMat.height(), Bitmap.Config.ARGB_8888);

        // 肌色抽出
        Core.inRange(imageMat, new Scalar(0, 20, 88), new Scalar(25, 80, 255), mask);

        // 重心取得
        // Utils.matToBitmap(mask, dst);
        Utils.bitmapToMat(bitmap, imageMat);
        Imgproc.cvtColor(hadairo, hadairo, Imgproc.COLOR_RGB2GRAY);
        // Utils.matToBitmap(hadairo, dst);

        Moments mu = Imgproc.moments(hadairo, false);

        Rect r = new Rect();
        r.x = (int) (mu.m10 / mu.m00);
        r.y = (int) (mu.m01 / mu.m00);

        r.x -= 50;
        r.width = 100;
        r.y -= 50;
        r.height = 100;

        mRect = r;
        mRects.clear();
        mRects.add(r);
        mIsSuccess = true;
        mColor = Color.BLUE;
        return mRect;
    }

    // 領域分割
    private Rect getFeatureArea(Bitmap bitmap) {
        MatOfRect faces = new MatOfRect();
        Mat srcMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));

        Utils.bitmapToMat(bitmap, srcMat);

        Mat imageMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
        Imgproc.cvtColor(srcMat, imageMat, Imgproc.COLOR_RGB2HSV);
        Imgproc.medianBlur(imageMat, imageMat, 3);

        Bitmap dst = Bitmap.createBitmap(imageMat.width(), imageMat.height(), Bitmap.Config.ARGB_8888);
        Mat tmp =  new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));

        // 肌色抽出
        Mat bw =  new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));

        Core.inRange(imageMat, new Scalar(0, 20, 88), new Scalar(25, 80, 255), bw);
        Utils.matToBitmap(bw, dst);

        Imgproc.threshold(bw, bw, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        Utils.matToBitmap(bw, dst);

        // ノイズ除去 (膨張 and 収縮)
        Mat kernel = Mat.ones(3, 3, CvType.CV_8U);
        Imgproc.morphologyEx(bw, bw, Imgproc.MORPH_OPEN, kernel, new Point(-1, -1), 2);
//        bw.convertTo(tmp, CvType.CV_8U);
        Imgproc.cvtColor(bw, tmp, Imgproc.COLOR_GRAY2RGB, 4);
        Utils.matToBitmap(tmp, dst);

        // 背景領域
        Mat sureBg = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
        Imgproc.dilate(bw, sureBg, kernel, new Point(-1, -1),  3);
//        sureBg.convertTo(tmp, CvType.CV_8U);
        Imgproc.cvtColor(sureBg, tmp, Imgproc.COLOR_GRAY2RGB, 4);
        Utils.matToBitmap(tmp, dst);

        // 輪郭の内側に行くほど白が残るマスク
        Mat sureFg = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
        Imgproc.distanceTransform(bw, sureFg, Imgproc.DIST_L2, 3); // 3.5 or 0 CV_8C1 -> CV_32FC1

        sureFg.convertTo(tmp, CvType.CV_8U);
        Imgproc.equalizeHist(tmp, tmp);
        Utils.matToBitmap(tmp, dst);

        Core.normalize(sureFg, sureFg, 0.0f, 255.0f, Core.NORM_MINMAX);

        sureFg.convertTo(tmp, CvType.CV_8U);
        Imgproc.equalizeHist(tmp, tmp);
        Utils.matToBitmap(tmp, dst);

        // 中心部分だけ抽出
        Imgproc.threshold(sureFg, sureFg, 40, 255, Imgproc.THRESH_BINARY);
        Imgproc.dilate(sureFg, sureFg, kernel,  new Point(-1, -1), 3); // CV_32FC1 -> CV_32FC1

        sureFg.convertTo(tmp, CvType.CV_8U);
        Imgproc.equalizeHist(tmp, tmp);
        Utils.matToBitmap(tmp, dst);

        // 不明領域
        Mat sureFgUC1 = sureFg.clone();
        sureFg.convertTo(sureFgUC1, CvType.CV_8UC1);
        Mat unknown = sureFg.clone();
        Core.subtract(sureBg, sureFgUC1, unknown);

        unknown.convertTo(tmp, CvType.CV_8U);
        Imgproc.equalizeHist(tmp, tmp);
        Utils.matToBitmap(tmp, dst);

        Mat markers = Mat.zeros(sureFg.size(), CvType.CV_32SC1);

        sureFg.convertTo(sureFg, CvType.CV_8U);
        int nLabels = Imgproc.connectedComponents(sureFg, markers, 8, CvType.CV_32SC1);
        if (nLabels < 2) {
            return mRect;
        }

        Core.add(markers, new Scalar(1), markers);
        for (int i=0; i<markers.rows(); i++) {
            for (int j=0; j<markers.cols(); j++) {
                double[] data = unknown.get(i, j);
                if (data[0] == 255) {
                    int[] val = new int[] { 255 };
                    markers.put(i, j, val);
                }
            }
        }

        Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2RGB);
        Imgproc.watershed(srcMat, markers);

        markers.convertTo(tmp, CvType.CV_8U);
        Imgproc.equalizeHist(tmp, tmp);
        Utils.matToBitmap(tmp, dst);

        mRect = null;
        mRects.clear();
        return mRect;
    }

    private Rect getContourRect(Bitmap bitmap) {
        if (bitmap == null) {
            return mRect;
        }

        MatOfRect faces = new MatOfRect();
        Mat imageMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));

        Utils.bitmapToMat(bitmap, imageMat);

        Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_RGB2HSV);
        Imgproc.medianBlur(imageMat, imageMat, 5);

       // Bitmap dst = Bitmap.createBitmap(imageMat.width(), imageMat.height(), Bitmap.Config.ARGB_8888);

        // 肌色抽出
        Mat bw =  new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
        Core.inRange(imageMat, new Scalar(0, 20, 88), new Scalar(25, 80, 255), bw);

       // Imgproc.threshold(bw, bw, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        // ノイズ除去 (膨張 and 収縮)
        Mat kernel = Mat.ones(3, 3, CvType.CV_8U);
        Imgproc.morphologyEx(bw, bw, Imgproc.MORPH_CLOSE, kernel, new Point(-1, -1), 2);

        // 輪郭の内側のみ抽出
        Imgproc.distanceTransform(bw, bw, Imgproc.DIST_L2, 3); // 3.5 or 0 CV_8C1 -> CV_32FC1
        Core.normalize(bw, bw, 0.0f, 255.0f, Core.NORM_MINMAX);
        Imgproc.threshold(bw, bw, 30, 255, Imgproc.THRESH_BINARY);
        Imgproc.dilate(bw, bw, kernel,  new Point(-1, -1), 3); // CV_32FC1 -> CV_32FC1
        bw.convertTo(bw, CvType.CV_8U); // CV_32FC1 -> CV_8UC1

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = Mat.zeros(new Size(5,5), CvType.CV_8UC1);
        Imgproc.findContours(bw, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_TC89_L1);

        mRect = null;
        mRects.clear();
        double maxArea = 0;
        for (MatOfPoint contour : contours) {
            Rect r = Imgproc.boundingRect(contour);
            mRects.add(r);

            double area = Imgproc.contourArea(contour);
            if (maxArea < area) {
                maxArea = area;
                mRect = r;
            }
        }

        if (contours.size() > 0) {
            mColor = Color.BLUE;
            mIsSuccess = true;
        }

        return mRect;
    }

    public Bitmap cropFace(Bitmap bitmap, float aspect) {
        if (!mIsSuccess) {
            return bitmap;
        }

        try {

            float w = bitmap.getWidth();
            float h = bitmap.getHeight();
            float bitmapAspect = h / w;

            if (BasicSettings.isDebug()) {
                //            bitmap = drawFaceRegion(mRect, bitmap, mColor);
                bitmap = drawFaceRegions(mRects, bitmap, mColor);
            }

            Rect r = new Rect(mRect.x, mRect.y, mRect.width, mRect.height);
            if (bitmapAspect > aspect) {
                r = addVPadding(r, bitmap, (int) (w * aspect));
                Bitmap resized = Bitmap.createBitmap(bitmap, 0, r.y, (int) w, r.height);
                return resized;
            } else {
                r = addHPadding(r, bitmap, (int) (h / aspect));
                Bitmap resized = Bitmap.createBitmap(bitmap, r.x, 0, r.width, (int) h);
                return resized;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    public Bitmap invoke(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() * bitmap.getHeight() == 0) {
            return bitmap;
        }

        mColor = mIsFirst ? Color.MAGENTA : Color.GREEN;
        if (mIsFirst) {
            mIsFirst = false;
            if (sFaceDetectorAnimeFace != null) {
                MatOfRect faces = new MatOfRect();
                Mat imageMat = new Mat((int) mHeight, (int) mWidth, CvType.CV_8U, new Scalar(4));
                Utils.bitmapToMat(bitmap, imageMat);
                Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_RGB2GRAY);
                Imgproc.equalizeHist(imageMat, imageMat);

                sFaceDetectorAnimeFace.detectMultiScale(imageMat, faces, 1.1f, 3, 0, new Size(300 / 5, 300 / 5), new Size());
                Rect[] facesArray = faces.toArray();
                if (facesArray.length > 0) {
                    Rect r = getLargestFace(facesArray);
                    Log.d(TAG, String.format("image: (%s, %s)", mWidth, mHeight));
                    Log.d(TAG, String.format("face area: (%d, %d, %d, %d)", r.x, r.y, r.width, r.height));
                    mRect = r;
                    mRects.clear();
                    Collections.addAll(mRects, facesArray);
                    mIsSuccess = true;
                }
            }
        }

        if (mIsSuccess) {
            if (BasicSettings.isDebug()) {
                bitmap = drawFaceRegion(mRect, bitmap, mColor);
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

    public static FaceCrop get(String imageUri, int maxHeight, float w, float h) {
        FaceCrop faceCrop = sFaceInfoMap.get(imageUri);
        if (faceCrop == null) {
            faceCrop = new FaceCrop(maxHeight, w, h);
            sFaceInfoMap.put(imageUri, faceCrop);
        }
        return faceCrop;
    }

    public boolean isSuccess() {
        return mIsSuccess;
    }
}
