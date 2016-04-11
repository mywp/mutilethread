package com.example.scorpio.mutilethread;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /* 线程的数量 */
    private int threadCount = 3;

    /* 每个下载区块的大小 */
    private long blocksize;

    /* 线程的数量 */
    private int runningThreadCount;
    private EditText et_path;
    private EditText et_count;
    
    //存放进度条的布局
    private LinearLayout ll_countainer;

    //进度条的集合
    private List<ProgressBar> pbs;
  
    private static final int DOWNLOAD_ERROR=1;
    private static final int THREAD_ERROR=2;
    private static final int DOWNLOAD_FINASH=3;
    
    /*android下的消息处理器，在主线程中创建才可以更新url*/
    private Handler handler = new Handler(){
      public void handleMessage(Message msg){
          switch (msg.what){
              case DOWNLOAD_ERROR:
                  Toast.makeText(getApplicationContext(),"下载失败",Toast.LENGTH_SHORT).show();
                  break;
              case THREAD_ERROR:
                  Toast.makeText(getApplicationContext(),"下载失败,请重试",Toast.LENGTH_SHORT).show();
                  break;
              case DOWNLOAD_FINASH:
                  Toast.makeText(getApplicationContext(),"下载完成",Toast.LENGTH_SHORT).show();
                  break;
          }
      }  
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        et_path = (EditText) findViewById(R.id.et_path);
        et_count = (EditText) findViewById(R.id.et_count);
        ll_countainer = (LinearLayout) findViewById(R.id.ll_container);
    }

    /*下载按钮的点击事件*/
    public void downLoad(View view) {
        // 下载文件的路径
        final String path = et_path.getText().toString().trim();
        if (TextUtils.isEmpty(path)) {
            Toast.makeText(this, "对不起下载路径不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        String count = et_count.getText().toString().trim();
        if (TextUtils.isEmpty(count)) {
            Toast.makeText(this, "对不起线程数量不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        threadCount = Integer.parseInt(count);
        //清空旧的进度条
        ll_countainer.removeAllViews();
        //在界面里面添加count个进度条
        pbs = new ArrayList<ProgressBar>();
        for (int j = 0; j < threadCount; j++) {
            ProgressBar pb = (ProgressBar) View.inflate(this, R.layout.pb, null);
            ll_countainer.addView(pb);
            pbs.add(pb);
        }
        Toast.makeText(this, "开始下载",Toast.LENGTH_SHORT).show();
        new Thread() {
            public void run() {
                try {
                    URL url = new URL(path);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        long size = conn.getContentLength();// 得到服务器端返回的文件的大小
                        System.out.println("服务器文件的大小：" + size);
                        blocksize = size / threadCount;
                        // 1.首先在本地创建一个大小根服务器一模一样的空白文件。
                        File file = new File(Environment.getExternalStorageDirectory(),getFileName(path));
                        RandomAccessFile raf = new RandomAccessFile(file, "rw");
                        raf.setLength(size);
                        raf.close();
                        // 2.开启若干个子线程分别去下载对应的资源
                        runningThreadCount = threadCount;
                        for (int i = 1; i <= threadCount; i++) {
                            long startIndex = (i - 1) * blocksize;
                            long endIndex = i * blocksize;
                            if (i == threadCount) {
                                // 最后一个线程
                                endIndex = size - 1;

                            }
                            System.out.println("开启线程：" + i + "下载的位置" + startIndex + "~" + endIndex);
                            int threadSize = (int) (endIndex - startIndex);
                            pbs.get(i-1).setMax(threadSize);
                            new DownloadThread(path, i, startIndex, endIndex).start();
                        }

                    }
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg= Message.obtain();
                    msg.what = DOWNLOAD_ERROR;
                    handler.sendMessage(msg);
                }
            }
        }.start();
    }

    private class DownloadThread extends Thread {
        private int threadId;
        private long startIndex;
        private long endIndex;
        private String path;

        public DownloadThread(String path, int threadId, long startIndex, long endIndex) {
            this.path = path;
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;

        }

        @Override
        public void run() {
            try {
                // 当前线程下载的总大小
                int total = 0;
                File positionFile = new File(Environment.getExternalStorageDirectory(), getFileName(path)+threadId + ".txt");
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                // 接着从上一次的位置继续下载数据
                if (positionFile.exists() && positionFile.length() > 0) {// 判断是否有记录
                    FileInputStream fis = new FileInputStream(positionFile);
                    BufferedReader br = new BufferedReader(new InputStreamReader(fis));
                    // 获取当前线程上次下载的总大小是多少
                    String lasttotalstr = br.readLine();
                    int lastTotal = Integer.valueOf(lasttotalstr);
                    System.out.println("上次线程" + threadId + "下载的总大小：" + lastTotal);
                    startIndex += lastTotal;
                    total += lastTotal;// 加上上次下载的总大小
                    fis.close();
                    //存数据库
                    //_id path threadid total

                }
                conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);
                conn.setConnectTimeout(5000);
                int code = conn.getResponseCode();
                System.out.println("code =" + code);
                InputStream is = conn.getInputStream();
                File file = new File(Environment.getExternalStorageDirectory(), getFileName(path));
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                // 指定文件开始的位置
                raf.seek(startIndex);
                System.out.println("第" + threadId + "个线程：写文件的开始位置" + String.valueOf(startIndex));
                int len = 0;
                byte[] buffer = new byte[1024 * 1024];
                while ((len = is.read(buffer)) != -1) {
                    RandomAccessFile rf = new RandomAccessFile(positionFile, "rwd");
                    raf.write(buffer, 0, len);
                    total += len;
                    rf.write(String.valueOf(total).getBytes());
                    rf.close();
                    pbs.get(threadId-1).setProgress(total);
                   
                }
                is.close();
                raf.close();
            } catch (Exception e) {
                e.printStackTrace();
                Message msg =Message.obtain();
                msg.what = THREAD_ERROR;
                handler.sendMessage(msg);
            } finally {
                // 只有所有的县城都下载完毕后 才可以删除记录文件
                synchronized (MainActivity.class) {
                    System.out.println("线程" + threadId + "下载完毕了");
                    runningThreadCount--;
                    if (runningThreadCount < 1) {
                        System.out.println("所有的线程都工作完毕了。删除临时记录的文件");
                        for (int i = 1; i <= threadCount; i++) {
                            File f = new File(Environment.getExternalStorageDirectory(), getFileName(path)+i+ ".txt");
                            System.out.println(f.delete());
                        }
                        Message msg = Message.obtain();
                        msg.what = DOWNLOAD_FINASH;
                        handler.sendMessage(msg);
                    }
                }
            }
        }
        
    }
    //http://10.0.2.2:8080/qq.apk
    private String getFileName(String path){
        int start =path.lastIndexOf("/")+1;
        return path.substring(start);
    }
}
