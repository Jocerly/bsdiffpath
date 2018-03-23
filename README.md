# bsdiffpath
Android 增量更新方案：插件式更新

一、什么是增量更新呢？

增量更新可以帮助我们减少用户更新apk所耗费的流量。

具体的做法是，在老版本apk和新版本apk中，差分出这两个apk文件之间，不同的部分，得到一个patch（补丁）文件。
比如我们之前的apk是20M，新的apk是30M，一般情况下，差分出来的补丁文件的大小在10M左右。
因此，用户在更新apk时，就只需要下载这10M的patch文件，通过app将patch文件和旧apk合成，就可以得到新的apk。

二、差分工具的使用
【服务器端】
    我们需要通过【diff(差分)】操作，得到patch文件。
【app端】
    我们需要从服务器端下载patch文件，并将其与旧apk文件合并，得到新版本的apk文件，然后提示用户安装。
    
三、app端实现旧版本与patch合并需要进行native开发，环境搭建我就不做介绍了，Cmake的语法使用也不做介绍哈。
直接来：
1、创建工程是选上c/c++开发z支持。
2、删除Android Studio为我们生成的native-lib.cpp文件，然后去下载bzip2：http://www.bzip.org/1.0.6/bzip2-1.0.6.tar.gz
解压，将刚刚解压出来的那些.c/.h文件复制到cpp目录下，
将AS自动生成的那个CMakeLists.txt文件拷贝到cpp目录下，并且在bzip2目录下再创建一个CMakeLists.txt
3、bzip2目录下CMakeLists.txt修改：

          #bzip2PROJECT(bzip2)
          
 cpp目录下的CMakeLists.txt：

        cmake_minimum_required(VERSION 3.4.1)
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11 -Wall -DGLM_FORCE_SIZE_T_LENGTH")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DGLM_FORCE_RADIANS")
        set(bzip2_src_DIR ${CMAKE_SOURCE_DIR})
        add_subdirectory(${bzip2_src_DIR}/bzip2)
        add_library(bspatch
                     SHARED
                     bspatch.c )
        find_library(log-lib
                      log )
        target_link_libraries( bspatch
                              ${log-lib} )

4、然后，我们需要修改一下MainActivit.java与bspatch.c的内容。

在MainActivit.java中：

        public class MainActivity extends AppCompatActivity {
            ...    

            static {
                System.loadLibrary("bspatch");
            }    /**
             * Native方法 合并更新文件
             * @param oldAPKPath 原APK路径
             * @param newAPKPath 要生成的新APK路径
             * @param patchPath 增量更新补丁包路径
             * @return 成功返回 0
             */
            public native int patch(String oldAPKPath, String newAPKPath, String patchPath);
        }
        
然后回到bspatch.c文件中，添加jni方法：

        JNIEXPORT jint JNICALL Java_cn_jocerly_bsdiffpatch_MainActivity_patch(JNIEnv *env, jobject instance,
                                                                     jstring oldAPKPath_,
                                                                     jstring newAPKPath_,
                                                                     jstring patchPath_) {
            const char *oldAPKPath = (*env)->GetStringUTFChars(env, oldAPKPath_, 0);
            const char *newAPKPath = (*env)->GetStringUTFChars(env, newAPKPath_, 0);
            const char *patchPath = (*env)->GetStringUTFChars(env, patchPath_, 0);
            int argc = 4;
            char *argv[4];
            argv[0] = "bspatch";
            argv[1] = oldAPKPath;
            argv[2] = newAPKPath;
            argv[3] = patchPath;
            int ret = bspatch_main(argc, argv);
            (*env)->ReleaseStringUTFChars(env, oldAPKPath_, oldAPKPath);
            (*env)->ReleaseStringUTFChars(env, newAPKPath_, newAPKPath);
            (*env)->ReleaseStringUTFChars(env, patchPath_, patchPath);
        }

注意，这里用到了一个小技巧，要把main()函数修改成patch_main()，才可以调用。

OK，native方法写好了，接下来我们就来编写Java代码，实现增量合并更新。                      
5、                      
工具类，MD5检测工具类：

        public class SignUtils {    
            /**
             * 判断文件的MD5值是否为指定值
             * @param file1
             * @param md5
             * @return
             */
            public static boolean checkMd5(File file1, String md5) {        
                if(TextUtils.isEmpty(md5)) {            
                    throw new RuntimeException("md5 cannot be empty");
                }        
                if(file1 != null && file1.exists()) {
                    String file1Md5 = getMd5ByFile(file1);            
                    return file1Md5.equals(md5);
                }        
                return false;
            }    
           /**
             * 获取文件的MD5值
             * @param file
             * @return
             */
            public static String getMd5ByFile(File file) {
                String value = null;
                FileInputStream in = null;        
               try {            
                    in = new FileInputStream(file);
                    MessageDigest digester = MessageDigest.getInstance("MD5");            
                   byte[] bytes = new byte[8192];            
                   int byteCount;           
                   while ((byteCount = in.read(bytes)) > 0) {
                         digester.update(bytes, 0, byteCount);
                    }            
                   value = bytes2Hex(digester.digest());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {           
                   if (null != in) {                
                       try {                    
                           in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }        
                 return value;
            }    

            private static String bytes2Hex(byte[] src) {        
            char[] res = new char[src.length * 2];
                final char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};        
                for (int i = 0, j = 0; i < src.length; i++) {
                    res[j++] = hexDigits[src[i] >>> 4 & 0x0f];
                    res[j++] = hexDigits[src[i] & 0x0f];
                }        return new String(res);
            }
        }
        
        
Apk安装工具类

注意，在Android 7.0以上，为了提高私有文件的安全性，面向 Android 7.0 或更高版本的应用私有目录被限制访问　(0700)。此设置可防止私有文件的元数据泄漏，如它们的大小或存在性。 因此，在Android 7以上，在应用之间共享文件受到了很大的限制。

如果使用如下的代码：

        public void installApk(File file) {     
            Intent intent = new Intent();     
            intent.setAction(Intent.ACTION_VIEW);     
            intent.setDataAndType(Uri.fromFile(file),             
            "application/vnd.android.package-archive");     
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
             context.startActivity(intent);
         }
         
系统将会抛出一个 FileUriExposedException 异常。 解决办法是，使用FileProvider。

在Manifest文件中，<application></application>标签之间，添加:

        <provider
                    android:authorities="cn.jocerly.fileprovider"
                    android:name="android.support.v4.content.FileProvider"
                    android:exported="false"
                    android:grantUriPermissions="true" >
                    <meta-data
                        android:name="android.support.FILE_PROVIDER_PATHS"
                        android:resource="@xml/file_paths" />
        </provider>
        
以启用FileProvider。注意<meta-data>标签，我们还需要提供一个资源文件，用来标识需要共享的文件。

在res文件下新建一个xml目录，并添加file_paths.xml文件。

        <?xml version="1.0" encoding="utf-8"?>
        <paths xmlns:android="http://schemas.android.com/apk/res/android">
            <root-path name="latest.apk" path="" />
        </paths>
        
这里<root_path>并未在官方文档中指出，它指代Environment.getExternalStorageDirectory()目录， 这个目录一般为/storage/emulator/0/，path为添加在其后的路径，name为文件名。 这里，指明我们的新apk文件在/storage/emulator/0/diffpatch/latest.apk路径下。

OK，配置完FileProvider后，我们编写ApkUtils.java:

        public class ApkUtils {    
            public static void installApk(Context context, String apkPath) {        
                File file = new File(apkPath);        
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
        
        
请大家自行与之前的installApk()方法对比，看看使用FileProvider后，获取文件Uri的方式有怎样的变化。

至此，增量更新的代码就编写完成了。                      
