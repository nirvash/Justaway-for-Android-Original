package info.justaway;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;

import com.squareup.picasso.Picasso;

import info.justaway.view.ScaleImageView;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

/**
 * 画像の拡大表示用のActivity、かぶせて使う
 * 
 * @author aska
 */
public class ScaleImageActivity extends Activity {

    private ScaleImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        imageView = new ScaleImageView(this);
        imageView.setActivity(this);
        String url = getIntent().getExtras().getString("url");
        Picasso.with(this).load(url).into(imageView);
        setContentView(imageView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scale_image, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.save) {
            imageView.setDrawingCacheEnabled(false);
            imageView.setDrawingCacheEnabled(true);
            Bitmap bitmap = imageView.getDrawingCache(false);
            File root = new File(Environment.getExternalStorageDirectory(), "/Download/");
            try {
                File file = new File(root, new Date().getTime() + ".jpg");
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(CompressFormat.JPEG, 100, fos);
                fos.close();
                // ギャラリーに登録
                String[] paths = {file.getPath()};
                String[] types = {"image/jpeg"};
                MediaScannerConnection.scanFile(getApplicationContext(), paths, types, null);
                JustawayApplication.showToast("保存しました！！！！１１１１");
                finish();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }
}
