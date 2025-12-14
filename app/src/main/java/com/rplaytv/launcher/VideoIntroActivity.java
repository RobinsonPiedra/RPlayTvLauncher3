package com.rplaytv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class VideoIntroActivity extends Activity {
    private VideoView videoView;
    private FrameLayout layout;
    private static final String API_URL = "https://4k.rpcine.com/api/video_intro.php?action=get_intro";
    private static final String RPLAYTV_PACKAGE = "iptvsmart.rpvipv";
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean canSkip = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
        
        layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);
        
        videoView = new VideoView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        layout.addView(videoView, params);
        setContentView(layout);
        
        fetchVideoConfig();
    }
    
    private void fetchVideoConfig() {
        new Thread(() -> {
            String videoUrl = "";
            boolean showIntro = true;
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject json = new JSONObject(response.toString());
                    if (json.optBoolean("success", false)) {
                        showIntro = json.optBoolean("show_intro", true);
                        videoUrl = json.optString("video_url", "");
                        canSkip = json.optBoolean("can_skip", false);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            final String finalVideoUrl = videoUrl;
            final boolean finalShowIntro = showIntro;
            handler.post(() -> {
                if (finalShowIntro && !finalVideoUrl.isEmpty()) {
                    playVideo(finalVideoUrl);
                } else {
                    openRPlayTv();
                }
            });
        }).start();
    }
    
    private void playVideo(String videoUrl) {
        try {
            videoView.setVideoURI(Uri.parse(videoUrl));
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                videoView.start();
            });
            videoView.setOnCompletionListener(mp -> openRPlayTv());
            videoView.setOnErrorListener((mp, what, extra) -> { 
                openRPlayTv(); 
                return true; 
            });
        } catch (Exception e) { 
            openRPlayTv(); 
        }
    }
    
    private void openRPlayTv() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(RPLAYTV_PACKAGE);
            if (intent != null) { 
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent); 
            } else { 
                Toast.makeText(this, "RPlayTv no instalado", Toast.LENGTH_LONG).show(); 
            }
        } catch (Exception e) { }
        finish();
    }
    
    @Override
    public void onBackPressed() { 
        if (canSkip) {
            openRPlayTv(); 
        }
    }
}
