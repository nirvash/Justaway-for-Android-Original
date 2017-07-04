package info.justaway.util;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

public class BitmapWrapper {
    private Bitmap mBitmap = null;
    private boolean mIsTemporary = false;

    public BitmapWrapper(Bitmap bitmap, boolean isTemporary) {
        this.mBitmap = bitmap;
        this.mIsTemporary = isTemporary;
    }

    public void setBitmap(Bitmap bitmap, boolean isTemporary) {
        recycle();
        this.mBitmap = bitmap;
        this.mIsTemporary = isTemporary;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public Mat createMat() {
        Mat mat =  new Mat(mBitmap.getWidth(), mBitmap.getHeight(),  CvType.CV_8U, new Scalar(4));
        Utils.bitmapToMat(mBitmap, mat);
        return mat;
    }

    public void setMat(Mat mat) {
        Utils.matToBitmap(mat, mBitmap);
    }

    public boolean isTemporary() {
        return mIsTemporary;
    }

    public void recycle() {
        if (mIsTemporary && mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }

    public float getWidth() {
        return mBitmap.getWidth();
    }

    public float getHeight() {
        return mBitmap.getHeight();
    }

}
