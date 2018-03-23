package cn.jocerly.bsdiffpatch;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("bspatch");
    }

    @BindView(R.id.text)
    TextView text;
    @BindView(R.id.button)
    Button button;
    @BindView(R.id.checkBox)
    CheckBox checkBox;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.editText)
    EditText editText;
    @BindView(R.id.editText2)
    EditText editText2;
    @BindView(R.id.imageView)
    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        initPath();
    }

    @OnClick(R.id.button)
    public void doButton() {
        Toast.makeText(this, "注解成功", Toast.LENGTH_SHORT).show();
        new IncrementalUpdateTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        LogI(getPatchFilePath());
    }

    /**
     * @param oldAPKPath
     * @param newAPKPath
     * @param patchPath
     * @return
     */
    public native int patch(String oldAPKPath, String newAPKPath, String patchPath);


    public static final String SDCARD_PATH = Environment.getExternalStorageDirectory() + File.separator + "diffpatch";
    public static final String PATCH_FILE = "old-to-new.patch";
    public static final String NEW_APK_FILE = "latest.apk";    // load increment update lib;

    private void initPath() {
        File file = new File(SDCARD_PATH);
        if (!file.exists()) {
            file.mkdir();
        }
    }

    private class IncrementalUpdateTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            String oldApkPath = SDCARD_PATH + "oldapk.apk";
            File oldApkFile = new File(oldApkPath);
            File patchFile = new File(getPatchFilePath());

            if (oldApkFile.exists() && patchFile.exists()) {
                LogI("正在合并增量文件...");
                String newApkPath = getNewApkFilePath();
                patch(oldApkPath, newApkPath, getPatchFilePath());
                LogI("增量文件的MD5值为：" + SignUtils.getMd5ByFile(patchFile));
                LogI("新文件的MD5值为：" + SignUtils.getMd5ByFile(new File(newApkPath)));
                return true;
            }

            LogI("找不到补丁文件");
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                LogI("合并成功，开始安装");
                ApkUtils.installApk(MainActivity.this, getNewApkFilePath());
            } else {
                LogI("合并失败");
            }
        }
    }

    private String getNewApkFilePath() {
        return SDCARD_PATH + NEW_APK_FILE;

    }

    private String getPatchFilePath() {
        return SDCARD_PATH + PATCH_FILE;

    }

    private void LogI(String log) {
        Log.i("bsdiffpatch", log);
    }
}
