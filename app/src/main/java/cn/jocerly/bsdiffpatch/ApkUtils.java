package cn.jocerly.bsdiffpatch;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;

import java.io.File;

/**
 * Created by Jocerly on 2018/3/23.
 */

class ApkUtils {
    public static void installApk(Context context, String newApkFilePath) {
        File file = new File(newApkFilePath);
        if (file.exists()) {
            Uri apkUri = FileProvider.getUriForFile(context, "cn.jocerly.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            context.startActivity(intent);
        }
    }
}
