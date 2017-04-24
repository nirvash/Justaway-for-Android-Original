package info.justaway.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import info.justaway.JustawayApplication;
import info.justaway.R;

@SuppressLint("AppCompatCustomView")
public class FontelloTextView extends TextView {

    public FontelloTextView(Context context) {
        super(context);
        init(null);
    }

    public FontelloTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) {
            return;
        }
        init(attrs);
    }

    public FontelloTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (isInEditMode()) {
            return;
        }
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.FontelloTextView);

        String fontName = ta.getString(R.styleable.FontelloTextView_font);
        if (TextUtils.isEmpty(fontName)) {
            setTypeface(JustawayApplication.getFontello());
        } else {
            setTypeface(JustawayApplication.getFontelloEx());
        }
    }

    public void enableExFont(boolean enable) {
        if (enable) {
            setTypeface(JustawayApplication.getFontelloEx());
        } else {
            setTypeface(JustawayApplication.getFontello());
        }
    }

}
