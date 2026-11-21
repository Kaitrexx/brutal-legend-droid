package com.brutallegend.droid;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.Callback;

import org.json.JSONObject;

import java.io.File;

/**
 * Brutal Legend Droid — fərdi, tək oyunlu istifadəçi interfeysi.
 * Winlator-ın X server / Wine / Box64 mühərriki backend olaraq istifadə olunur,
 * lakin Winlator-ın menyuları və Settings interfeysi istifadəçiyə göstərilmir.
 */
public class LauncherActivity extends Activity {

    private static final String CONTAINER_NAME = "BrutalLegend";
    private static final String DEFAULT_GAME_DIR = "/sdcard/Download/BrutalLegend";

    private ContainerManager containerManager;
    private Container container;
    private EditText pathEdit;
    private TextView statusText;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        containerManager = new ContainerManager(this);
        setContentView(buildUi());
        requestStorageAccess();
        prepareContainer();
    }

    private ViewGroup buildUi() {
        int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0B10"));
        root.setPadding(pad, dp(48), pad, dp(32));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("BRUTAL LEGEND");
        title.setTextSize(30);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#FF6A1E"));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("D R O I D   ·   Mali optimized");
        sub.setTextSize(13);
        sub.setTextColor(Color.parseColor("#9AA0AA"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(24));
        root.addView(sub);

        TextView hint = new TextView(this);
        hint.setText("Oyun qovluğu (içində BrutalLegend.exe)");
        hint.setTextColor(Color.parseColor("#C8CDD4"));
        hint.setTextSize(13);
        root.addView(hint);

        pathEdit = new EditText(this);
        pathEdit.setText(DEFAULT_GAME_DIR);
        pathEdit.setTextColor(Color.WHITE);
        pathEdit.setHintTextColor(Color.parseColor("#666B75"));
        pathEdit.setSingleLine(true);
        pathEdit.setBackgroundColor(Color.parseColor("#1C1E26"));
        pathEdit.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editParams.topMargin = dp(8);
        pathEdit.setLayoutParams(editParams);
        root.addView(pathEdit);

        startButton = new Button(this);
        startButton.setText("OYUNU BAŞLAT");
        startButton.setTextColor(Color.WHITE);
        startButton.setBackgroundColor(Color.parseColor("#FF6A1E"));
        startButton.setEnabled(false);
        startButton.setAllCaps(false);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dp(20);
        startButton.setLayoutParams(btnParams);
        startButton.setOnClickListener(v -> startGame());
        root.addView(startButton);

        statusText = new TextView(this);
        statusText.setText("Hazırlanır…");
        statusText.setTextColor(Color.parseColor("#8BE08B"));
        statusText.setTextSize(13);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(20), 0, 0);
        root.addView(statusText);

        TextView info = new TextView(this);
        info.setText("640×360 · Vortek · Box64 PERFORMANCE · FPS HUD");
        info.setTextColor(Color.parseColor("#5E646E"));
        info.setTextSize(11);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(8), 0, 0);
        root.addView(info);

        return root;
    }

    private void prepareContainer() {
        for (Container existing : containerManager.getContainers()) {
            if (CONTAINER_NAME.equals(existing.getName())) {
                container = existing;
                setStatus("Konteyner hazırdır", true);
                return;
            }
        }

        setStatus("Oyun üçün konteyner yaradılır…", false);
        JSONObject data = new JSONObject();
        try {
            data.put("name", CONTAINER_NAME);
            data.put("screenSize", "640x360");
            data.put("envVars", Container.DEFAULT_ENV_VARS);
            data.put("cpuList", "");
            data.put("cpuListWoW64", "");
            data.put("graphicsDriver", "vortek,gladio");
            data.put("dxwrapper", "dxvk");
            data.put("dxwrapperConfig", "");
            data.put("graphicsDriverConfig", "");
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("audioDriverConfig", "");
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES);
            data.put("hudMode", 1);
            data.put("startupSelection", 1);
            data.put("box64Preset", "PERFORMANCE");
            data.put("desktopTheme", "");
        } catch (Exception ignored) {
        }

        containerManager.createContainerAsync(data, new Callback<Container>() {
            @Override
            public void call(Container result) {
                container = result;
                runOnUiThread(() -> {
                    if (result != null) {
                        setStatus("Konteyner hazırdır — başlaya bilərsiniz", true);
                    } else {
                        setStatus("Konteyner yaradılamadı", false);
                    }
                });
            }
        });
    }

    private void startGame() {
        if (container == null) {
            setStatus("Konteyner hələ hazır deyil", false);
            return;
        }
        String dir = pathEdit.getText().toString().trim();
        File exe = findGame(new File(dir), 0);
        if (exe == null) {
            setStatus("BrutalLegend.exe tapılmadı: " + dir, false);
            return;
        }
        setStatus("Başladılır: " + exe.getName(), true);
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", exe.getAbsolutePath());
        startActivity(intent);
    }

    private File findGame(File dir, int depth) {
        if (dir == null || !dir.exists() || depth > 3) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().equalsIgnoreCase("BrutalLegend.exe")) return f;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findGame(f, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void setStatus(String text, boolean ready) {
        statusText.setText(text);
        statusText.setTextColor(Color.parseColor(ready ? "#8BE08B" : "#FFB061"));
        startButton.setEnabled(ready && container != null);
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                Toast.makeText(this, "Fayl icazəsi verin", Toast.LENGTH_LONG).show();
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
