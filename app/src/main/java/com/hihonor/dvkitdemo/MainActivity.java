/*
 * Copyright (c) Honor Device Co., Ltd. 2022-2024. All rights reserved.
 */

package com.hihonor.dvkitdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.hihonor.mcs.connect.devicevirtualization.DVVersionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo初始化操作界面
 *
 * @since 2022-06-30
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DVKit-MainActivity";

    private static final int PERMISSIONS_REQUEST = 101;

    // Requested permission
    String[] mRequestPermissions =
        new String[] {Manifest.permission.CAMERA, "com.hihonor.permission.DISTRIBUTED_VIRTUALDEVICE"};

    List<String> mPermissionList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button dvKitDemoBtn = findViewById(R.id.dvkitDemo);
        if (!DVVersionUtils.isSupportDVService()) {
            dvKitDemoBtn.setText("NOT SUPPORT");
            dvKitDemoBtn.setEnabled(false);
        } else {
            dvKitDemoBtn.setOnClickListener(view -> {
                // Determine whether you have obtained the required mRequestPermissions
                mPermissionList.clear();
                for (String permission : mRequestPermissions) {
                    Log.i(TAG, "request permission mRequestPermissions[i]: " + permission);
                    if (ContextCompat.checkSelfPermission(MainActivity.this, permission) !=
                        PackageManager.PERMISSION_GRANTED) {
                        mPermissionList.add(permission);
                    }
                }
                if (mPermissionList.isEmpty()) {
                    Log.i(TAG, "permission granted");
                    // Unauthorized mRequestPermissions are empty, meaning they are all granted
                    Intent intent = new Intent(MainActivity.this, DvkitDemoActivity.class);
                    startActivity(intent);
                } else {
                    // Request permission method
                    Log.i(TAG, "request mRequestPermissions");
                    String[] mPermissions = mPermissionList.toArray(new String[0]);
                    requestPermissions(mPermissions, PERMISSIONS_REQUEST);
                }
            });
        }
    }

    // After obtaining mRequestPermissions, open DvKitDemo Activity
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult, request code: " + requestCode);
        if (requestCode == PERMISSIONS_REQUEST) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "mRequestPermissions[i]: " + permissions[i]);
                    mPermissionList.remove(permissions[i]);
                }
            }
            if (mPermissionList.isEmpty()) {
                Log.i(TAG, "start activity");
                Intent intent = new Intent(MainActivity.this, DvkitDemoActivity.class);
                startActivity(intent);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}