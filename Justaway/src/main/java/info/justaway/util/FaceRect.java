package info.justaway.util;

import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.LinkedList;

public class FaceRect {
    Rect mRect = null;
    public FaceRect(Rect r) {
        mRect = r;
    }

    public FaceRect(android.graphics.Rect r) {
        mRect = new Rect();
        mRect.x = r.left;
        mRect.y = r.top;
        mRect.width = r.width();
        mRect.height = r.height();
    }

    public void scale(double scale) {
        mRect.x /= scale;
        mRect.y /= scale;
        mRect.width /= scale;
        mRect.height /= scale;
    }


    public static LinkedList<FaceRect> create(Rect[] rects) {
        LinkedList<FaceRect> result = new LinkedList<>();
        for (Rect r : rects) {
            result.push(new FaceRect(r));
        }
        return result;
    }

    public Point tl() {
        return mRect.tl();
    }

    public Point br() {
        return mRect.br();
    }

    public int x() {
        return mRect.x;
    }

    public int y() {
        return mRect.y;
    }

    public int width() {
        return mRect.width;
    }

    public int height() {
        return mRect.height;
    }

    public void setTl(Point tl) {
        mRect.x = (int) tl.x;
        mRect.y = (int) tl.y;
    }

    public void setX(int x) {
        mRect.x = x;
    }

    public void setY(int y) {
        mRect.y = y;
    }

    public void setHeight(int height) {
        mRect.height = height;
    }

    public void setWidth(int width) {
        mRect.width = width;
    }
}
