package io.github.cjybyjk.dpmapkinstaller;

import androidx.appcompat.app.AppCompatActivity;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    Uri gUri;
    TextView tvAPK;
    TextView tvLog;
    Button btnInstall;
    Button btnSelect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvAPK = findViewById(R.id.txtAPK);
        tvLog = findViewById(R.id.tvLog);
        btnInstall = findViewById(R.id.btnInstall);
        btnSelect = findViewById(R.id.btnSelect);

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("application/vnd.android.package-archive");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent,1);
            }
        });
        btnInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startInstall();
            }
        });

        DPMSetting();

        Intent intent = getIntent();
        String action = intent.getAction();
        if(intent.ACTION_VIEW.equals(action))
        {
            tvAPK.setText(intent.getData().toString());
            gUri = intent.getData();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                gUri = data.getData();
                if (gUri != null) {
                    tvAPK.setText(gUri.toString());
                }
            }
        }
    }

    void startInstall() {
        tvLog.append("开始安装 " + gUri.toString() + "\n");
        new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    final boolean tRes = PackageInstallerUtils.installPackage(getApplicationContext(), gUri);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvLog.append("安装结果: " + tRes + "\n");
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    void DPMSetting() {
        DevicePolicyManager mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName mComponentName = new ComponentName(this, DPMReceiver.class);
        // 判断该组件是否已经是DeviceAdmin
        if (!mDevicePolicyManager.isAdminActive(mComponentName)) {
            Intent intent = new Intent(
                    DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    mComponentName);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "DPM APK installer need this permission");
            startActivityForResult(intent, 1);
        }
    }
}
