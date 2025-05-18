package com.mshomeguardian.logger.transcription;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class to download Whisper models at runtime
 */
public class WhisperModelDownloader {
    private static final String TAG = "WhisperModelDownloader";

    public interface DownloadCallback {
        void onProgress(int progress);
        void onComplete(boolean success, String modelPath);
        void onError(String error);
    }

    // Map of model names to download URLs
    private static final Map<String, String> MODEL_URLS = new HashMap<>();
    static {
        MODEL_URLS.put("tiny", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin");
        MODEL_URLS.put("base", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin");
        MODEL_URLS.put("small", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin");
        // Add more models as needed
    }

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public WhisperModelDownloader(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Download a model
     */
    public void downloadModel(String modelName, DownloadCallback callback) {
        if (!MODEL_URLS.containsKey(modelName)) {
            mainHandler.post(() -> callback.onError("Unknown model: " + modelName));
            return;
        }

        String modelUrl = MODEL_URLS.get(modelName);
        File modelFile = getModelFile(modelName);

        // Create directory if it doesn't exist
        File modelsDir = modelFile.getParentFile();
        if (modelsDir != null && !modelsDir.exists()) {
            if (!modelsDir.mkdirs()) {
                mainHandler.post(() -> callback.onError("Failed to create models directory"));
                return;
            }
        }

        executor.execute(() -> {
            try {
                URL url = new URL(modelUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    mainHandler.post(() -> callback.onError("Server returned HTTP " + responseCode));
                    return;
                }

                int fileLength = connection.getContentLength();
                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(modelFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);

                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> callback.onProgress(progress));
                    }
                }

                output.close();
                input.close();

                mainHandler.post(() -> callback.onComplete(true, modelFile.getAbsolutePath()));

            } catch (IOException e) {
                Log.e(TAG, "Error downloading model", e);
                mainHandler.post(() -> callback.onError("Error downloading model: " + e.getMessage()));

                // Delete partial file if it exists
                if (modelFile.exists()) {
                    modelFile.delete();
                }
            }
        });
    }

    /**
     * Get the File object for a model
     */
    public File getModelFile(String modelName) {
        File modelsDir = new File(context.getFilesDir(), "whisper_models");
        return new File(modelsDir, "ggml-" + modelName + ".bin");
    }

    /**
     * Check if a model exists
     */
    public boolean modelExists(String modelName) {
        File modelFile = getModelFile(modelName);
        return modelFile.exists() && modelFile.length() > 0;
    }

    /**
     * Get model path if it exists
     */
    public String getModelPath(String modelName) {
        if (modelExists(modelName)) {
            return getModelFile(modelName).getAbsolutePath();
        }
        return null;
    }



    /**
     * Cleanup the executor
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Extract model from assets (if it exists there)
     */
    public boolean extractModelFromAssets(String modelName) {
        try {
            String assetFileName = "whisper_models/ggml-" + modelName + ".bin";
            File modelFile = getModelFile(modelName);
            File modelsDir = modelFile.getParentFile();

            // Create directory if it doesn't exist
            if (modelsDir != null && !modelsDir.exists()) {
                if (!modelsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create models directory");
                    return false;
                }
            }

            // Check if asset exists
            String[] assets = context.getAssets().list("whisper_models");
            boolean assetExists = false;
            if (assets != null) {
                for (String asset : assets) {
                    if (asset.equals("ggml-" + modelName + ".bin")) {
                        assetExists = true;
                        break;
                    }
                }
            }

            if (!assetExists) {
                Log.e(TAG, "Model not found in assets: " + assetFileName);
                return false;
            }

            // Extract the model
            InputStream inputStream = context.getAssets().open(assetFileName);
            FileOutputStream outputStream = new FileOutputStream(modelFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();

            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error extracting model from assets", e);
            return false;
        }
    }
}