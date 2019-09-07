package io.github.cjybyjk.apkinstallclient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private final String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    private boolean istemp = false;

    ConnectThread tConnect;
    EditText textAPK;
    TextView tvLog;
    Button btnInstall;
    Button btnSelect;

    File tInstallingFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textAPK = findViewById(R.id.txtAPK);
        tvLog = findViewById(R.id.tvLog);
        btnInstall = findViewById(R.id.btnInstall);
        btnSelect = findViewById(R.id.btnSelect);
        tConnect = new ConnectThread();

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("application/vnd.android.package-archive");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                if (istemp) {
                    deleteSingleFile(tInstallingFile);
                    istemp = false;
                }
                startActivityForResult(intent,1);
            }
        });
        btnInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable(){
                    @Override
                    public void run() {
                        try {
                            tInstallingFile = new File(textAPK.getText().toString());
                            tConnect.outputStream.write(textAPK.getText().toString().getBytes());
                        } catch (Exception e) {
                            e.printStackTrace();
                            tvLog.append("数据发送失败！详细信息: " + e.toString() + "\n");
                        }
                    }
                }).start();
            }
        });

        Intent intent = getIntent();
        String action = intent.getAction();
        if(intent.ACTION_VIEW.equals(action))
        {
            textAPK.setText(preInstall(intent.getData()));
        }

        tConnect.start();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null) {
                    String path = FileUtils.getPath(this, uri);
                    if (path != null && new File(path).exists()) {
                        textAPK.setText(path);
                    }
                }
            }
        }
    }

    public class ConnectThread extends Thread{
        Socket socket = null;
        OutputStream outputStream = null;
        InputStream inputStream = null;
        public void run(){
            try {
                socket = new Socket("127.0.0.1", 1226);
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
                while (true)
                {
                    final byte[] buffer = new byte[1024];
                    final int len = inputStream.read(buffer);
                    final String tResult = new String(buffer,0,len);
                    runOnUiThread(new Runnable()
                    {
                        public void run()
                        {
                            tvLog.append(tResult);
                        }
                    });
                    if (tResult.contains("Done")) {
                        if (istemp){
                            deleteSingleFile(tInstallingFile);
                        }
                        istemp = false;
                        tInstallingFile = null;
                    }
                }
            } catch (final Exception e) {
                e.printStackTrace();
                istemp = false;
                tInstallingFile = null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvLog.append("与服务器通信时发生错误！详细信息: " + e.toString() + "\n");
                    }
                });
            }
        }
    }

    private String preInstall(Uri uri) {
        String apkPath = null;
        if (uri != null) {
            String CONTENT = "content://";
            String FILE = "file://";
            if (uri.toString().contains(FILE)) {
                confirmPermission();
                apkPath = uri.getPath();
            } else if (uri.toString().contains(CONTENT)) {
                apkPath = createApkFromUri(this, uri);
            }
            return apkPath;
        } else {
            finish();
            return "";
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, permissions, 0x233);
    }

    private void confirmPermission() {
        int permissionRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        boolean judge = (permissionRead == 0);
        if (!judge) {
            requestPermission();
        }
    }

    private String createApkFromUri(Context context, Uri uri) {
        istemp = true;
        File tempFile = new File(context.getExternalCacheDir(), System.currentTimeMillis() + ".apk");
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is != null) {
                OutputStream fos = new FileOutputStream(tempFile);
                byte[] buf = new byte[4096 * 1024];
                int ret;
                while ((ret = is.read(buf)) != -1) {
                    fos.write(buf, 0, ret);
                    fos.flush();
                }
                fos.close();
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tempFile.getAbsolutePath();
    }

    private void deleteSingleFile(File file) {
        if (file.exists() && file.isFile()) {
            file.delete();
        }
    }


}
