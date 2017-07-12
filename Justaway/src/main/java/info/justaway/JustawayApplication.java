package info.justaway;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.StrictMode;
import android.util.Log;

import com.deploygate.sdk.DeployGate;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import info.justaway.model.Relationship;
import info.justaway.model.UserIconManager;
import info.justaway.settings.BasicSettings;
import info.justaway.settings.MuteSettings;
import info.justaway.util.FaceCrop;
import info.justaway.util.ImageUtil;

@ReportsCrashes(
        mode = ReportingInteractionMode.DIALOG,
        mailTo = "nirvash@gmail.com",
        resToastText = R.string.crash_toast_text,
        resDialogText = R.string.crash_dialog_text,
        resDialogIcon = android.R.drawable.ic_dialog_info,
        resDialogTitle = R.string.crash_dialog_title,
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt
)

public class JustawayApplication extends Application {

    private static final String TAG = JustawayApplication.class.getSimpleName();

    private BaseLoaderCallback mOpenCVLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            super.onManagerConnected(status);
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV initialized");
                    FaceCrop.initFaceDetector(getApplicationContext());
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    private static JustawayApplication sApplication;
    private static Typeface sFontello;

    @Override
    public void onCreate() {
        super.onCreate();
        DeployGate.install(this);

        sApplication = this;
        ACRA.init(this);

        // Twitter4J の user stream の shutdown() で NetworkOnMainThreadException が発生してしまうことに対する暫定対応
        if (!BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
        }

        /**
         * 画像のキャッシュや角丸の設定を行う
         */
        ImageUtil.init(getApplicationContext());

        /**
         * 設定ファイル読み込み
         */
        MuteSettings.init();

        BasicSettings.init();

        UserIconManager.warmUpUserIconMap();

        Relationship.init();

        sFontello = Typeface.createFromAsset(getAssets(), "fontello.ttf");
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mOpenCVLoaderCallback);

        // 例外発生時の処理を指定（スタックトレースを保存）
        if (BuildConfig.DEBUG) {
            Thread.setDefaultUncaughtExceptionHandler(new MyUncaughtExceptionHandler(sApplication));
        }
    }

    /**
     * 終了時
     * 
     * @see android.app.Application#onTerminate()
     */
    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    /**
     * 空きメモリ逼迫時
     * 
     * @see android.app.Application#onLowMemory()
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    /**
     * 実行時において変更できるデバイスの設定時 ( 画面のオリエンテーション、キーボードの使用状態、および言語など )
     * https://sites.google.com/a/techdoctranslator.com/jp/android/guide/resources/runtime-changes
     *
     * @see android.app.Application#onConfigurationChanged(android.content.res.Configuration)
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public static JustawayApplication getApplication() {
        return sApplication;
    }

    public static Typeface getFontello() {
        return sFontello;
    }
}
