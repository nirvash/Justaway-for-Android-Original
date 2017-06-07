package info.justaway.util;

import android.graphics.Bitmap;

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
