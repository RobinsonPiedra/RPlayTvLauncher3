package com.rplaytv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class VideoIntroActivity extends Activity implements TextureView.SurfaceTextureListener {
    private TextureView textureView;
    private MediaPlayer mediaPlayer;
    private FrameLayout layout;
    private static final String API_URL = "https://4k.rpcine.com/api/video_intro.php?action=get_intro";
    private static final String RPLAYTV_PACKAGE = "iptvsmart.rpvipv";
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean canSkip = false;
    private String videoUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        
        layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);
        
        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        layout.addView(textureView, params);
        setContentView(layout);
        
        fetchVideoConfig();
    }
    
    private void fetchVideoConfig() {
        new Thread(() -> {
            String fetchedUrl = "";
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
                        fetchedUrl = json.optString("video_url", "");
                        canSkip = json.optBoolean("can_skip", false);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            final String finalVideoUrl = fetchedUrl;
            final boolean finalShowIntro = showIntro;
            handler.post(() -> {
                if (finalShowIntro && !finalVideoUrl.isEmpty()) {
                    videoUrl = finalVideoUrl;
                    if (textureView.isAvailable()) {
                        playVideo(textureView.getSurfaceTexture());
                    }
                } else {
                    openRPlayTv();
                }
            });
        }).start();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (!videoUrl.isEmpty()) {
            playVideo(surface);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        adjustAspectRatio();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    
    private void playVideo(SurfaceTexture surfaceTexture) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            Surface surface = new Surface(surfaceTexture);
            mediaPlayer.setSurface(surface);
            mediaPlayer.setDataSource(this, Uri.parse(videoUrl));
            mediaPlayer.setOnPreparedListener(mp -> {
                adjustAspectRatio();
                mp.start();
            });
            mediaPlayer.setOnCompletionListener(mp -> openRPlayTv());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> { 
                openRPlayTv(); 
                return true; 
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) { 
            openRPlayTv(); 
        }
    }
    
    private void adjustAspectRatio() {
        if (mediaPlayer == null) return;
        int videoWidth = mediaPlayer.getVideoWidth();
        int videoHeight = mediaPlayer.getVideoHeight();
        if (videoWidth == 0 || videoHeight == 0) return;
        
        int screenWidth = layout.getWidth();
        int screenHeight = layout.getHeight();
        
        float videoRatio = (float) videoWidth / videoHeight;
        float screenRatio = (float) screenWidth / screenHeight;
        
        int newWidth, newHeight;
        if (videoRatio > screenRatio) {
            newWidth = screenWidth;
            newHeight = (int) (screenWidth / videoRatio);
        } else {
            newHeight = screenHeight;
            newWidth = (int) (screenHeight * videoRatio);
        }
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(newWidth, newHeight);
        params.gravity = Gravity.CENTER;
        textureView.setLayoutParams(params);
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
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        finish();
    }
    
    @Override
    public void onBackPressed() { 
        if (canSkip) {
            openRPlayTv(); 
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
