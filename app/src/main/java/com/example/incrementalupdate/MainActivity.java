package com.example.incrementalupdate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.InputStream;

import androidx.documentfile.provider.DocumentFile;


import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.IOException;


public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 101;
    // 对于Android 10及以下版本需要的权限
    private static final String[] LEGACY_REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private Button selectFolderButton;
    private Button encryptButton;
    private Button incrementalUpdateButton;
    private EditText keyInput;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView selectedFolderText;

    private Uri selectedFolderUri;
    private String encryptionKey;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private FileEncryptor fileEncryptor;
    private HashCalculator hashCalculator;
    private MetadataCollector metadataCollector;
    private NetworkManager networkManager;
    private FileSplitter fileSplitter;

    private final ActivityResultLauncher<Intent> folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedFolderUri = result.getData().getData();
                    selectedFolderText.setText("已选择文件夹: " + selectedFolderUri.getPath());
                    encryptButton.setEnabled(true);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkPermissions();
        initComponents();
        setupListeners();
    }

    private void initViews() {
        // ... existing code ...
        selectFolderButton = findViewById(R.id.selectFolderButton);
        encryptButton = findViewById(R.id.encryptButton);
        incrementalUpdateButton = findViewById(R.id.incrementalUpdateButton);
        keyInput = findViewById(R.id.keyInput);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        selectedFolderText = findViewById(R.id.selectedFolderText);

        encryptButton.setEnabled(false);
        incrementalUpdateButton.setEnabled(false);
    }

    private void initComponents() {
        // ... existing code ...
        fileEncryptor = new FileEncryptor();
        hashCalculator = new HashCalculator();
        metadataCollector = new MetadataCollector();
        networkManager = new NetworkManager("10.26.68.24", 12000);
        fileSplitter = new FileSplitter(networkManager);
    }

    private void setupListeners() {
        // ... existing code ...
        selectFolderButton.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                openFolderPicker();
            }
        });

        // 其余监听器保持不变
        encryptButton.setOnClickListener(v -> {
            encryptionKey = keyInput.getText().toString();
            if (encryptionKey.isEmpty()) {
                Toast.makeText(this, "请输入加密密钥", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedFolderUri != null) {
                startEncryption();
            } else {
                Toast.makeText(this, "请先选择文件夹", Toast.LENGTH_SHORT).show();
            }
        });

        incrementalUpdateButton.setOnClickListener(v -> {
            // 这里应该显示一个对话框让用户输入文件名和块号
            // 为简化，我们假设使用了固定的文件名和块号
            String fileName = "example.encrypted";
            int blockNumber = 2;

            executorService.submit(() -> {
                try {
                    File encryptedFile = new File(getExternalFilesDir(null), fileName);
                    fileSplitter.splitAndSendFile(encryptedFile, blockNumber);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "增量更新完成", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "增量更新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        });
    }

    /**
     * 检查存储权限
     * 对于Android 11及以上版本，检查并请求MANAGE_EXTERNAL_STORAGE权限
     * 对于低版本，检查传统存储权限
     */
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+需要MANAGE_EXTERNAL_STORAGE权限
            if (!Environment.isExternalStorageManager()) {
                // 请求"所有文件访问权"
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
                Toast.makeText(this, "请授予应用所有文件访问权限", Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        } else {
            // 低版本Android使用传统权限
            return checkLegacyStoragePermissions();
        }
    }

    /**
     * 检查传统存储权限（Android 10及以下）
     */
    private boolean checkLegacyStoragePermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : LEGACY_REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void checkPermissions() {
        // 在onCreate中调用检查权限
        checkStoragePermission();
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderPickerLauncher.launch(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // 已获得权限，可以继续操作
                    Toast.makeText(this, "已获得所有文件访问权限", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "未获得所有文件访问权限", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "需要存储权限才能运行此应用", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startEncryption() {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        progressText.setVisibility(View.VISIBLE);
        encryptButton.setEnabled(false);

        executorService.submit(() -> {
            try {
                List<DocumentFile> files = new ArrayList<>();
                DocumentFile rootDir = DocumentFile.fromTreeUri(this, selectedFolderUri);

                if (rootDir != null && rootDir.isDirectory()) {
                    for (DocumentFile file : rootDir.listFiles()) {
                        if (file.isFile()) {
                            files.add(file);
                        }
                    }
                }

                if (files.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "文件夹中没有文件", Toast.LENGTH_LONG).show());
                    return;
                }

                int totalFiles = files.size();
                int processedFiles = 0;

                for (DocumentFile docFile : files) {
                    try {
                        byte[] iv = fileEncryptor.generateIV();

                        // 创建一个临时文件存储加密结果
                        File tempDir = getExternalFilesDir(null);
                        File encryptedFile = new File(tempDir, docFile.getName() + ".encrypted");

                        // 使用输入流和输出流处理文件
                        InputStream inputStream = getContentResolver().openInputStream(docFile.getUri());
                        File encryptedResult = fileEncryptor.encryptStream(inputStream, encryptedFile, encryptionKey, iv);

                        if (encryptedResult != null) {
                            String fileName = encryptedResult.getName();
                            long creationTime = System.currentTimeMillis();  // 使用当前时间作为创建时间
                            long modificationTime = encryptedResult.lastModified();
                            List<String> blockHashes = hashCalculator.calculateFileBlockHashes(encryptedResult);
                            networkManager.sendMetadataAndHashes(fileName, creationTime, modificationTime, blockHashes);
                        }

                        processedFiles++;
                        int finalProgress = (int) ((processedFiles / (float) totalFiles) * 100);
                        runOnUiThread(() -> {
                            progressBar.setProgress(finalProgress);
                            progressText.setText(finalProgress + "%");
                        });
                    } catch (Exception e) {
                        logError("单个文件加密失败: " + docFile.getName() + ", 错误: " + e.getMessage());
                    }
                }

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "加密完成！", Toast.LENGTH_LONG).show();
                    encryptButton.setEnabled(true);
                    incrementalUpdateButton.setEnabled(true);
                });

            } catch (Exception e) {
                // 在主线程中显示 Toast 消息
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "加密失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    encryptButton.setEnabled(true);
                });

                logError("加密失败: " + e.getMessage());
            }
        });
    }

    private void logError(String errorMessage) {
        try {
            File logDir = new File(Environment.getExternalStorageDirectory(), "Android/2");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, "error.log");

            FileOutputStream fos = new FileOutputStream(logFile, true);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
            writer.write(errorMessage + " - " + new java.util.Date());
            writer.newLine();
            writer.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
