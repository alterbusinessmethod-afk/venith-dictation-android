package com.voiceflowkeyboard.ime;

import android.content.Context;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class OfflineVoskClient {
    static final int SAMPLE_RATE = 16000;
    private static final String MODEL_ID = "vosk-model-small-en-us-0.15";
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/" + MODEL_ID + ".zip";

    private OfflineVoskClient() {
    }

    static boolean isModelReady(Context context) {
        File dir = modelDir(context);
        return OptionalModelIntegrity.ready(dir) && new File(dir, "conf").isDirectory()
                && new File(dir, "am").isDirectory()
                && new File(dir, "graph").isDirectory();
    }

    static void ensureModel(Context context) throws IOException {
        if (isModelReady(context) && OptionalModelIntegrity.verify(modelDir(context))) return;
        File root = modelRoot(context);
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Could not create offline model directory.");
        }
        File zip = new File(context.getCacheDir(), MODEL_ID + ".zip");
        download(MODEL_URL, zip);
        File stage=new File(root,"staging-"+java.util.UUID.randomUUID());
        if(!stage.mkdir()) throw new IOException("Could not stage optional model");
        unzip(zip, stage);
        File staged=new File(stage,MODEL_ID);
        if(!new File(staged,"am/final.mdl").isFile() || !new File(staged,"conf/model.conf").isFile()) throw new IOException("Incomplete optional model");
        OptionalModelIntegrity.record(staged);
        File target=modelDir(context);
        if(target.exists() && !target.renameTo(new File(root,"invalid-"+java.util.UUID.randomUUID()))) throw new IOException("Could not preserve model");
        if(!staged.renameTo(target)) throw new IOException("Could not activate optional model");
        if (!zip.delete()) {
            zip.deleteOnExit();
        }
        if (!isModelReady(context)) {
            throw new IOException("Offline model download did not finish correctly.");
        }
    }

    static String transcribePcm(Context context, File pcmFile) throws Exception {
        if(pcmFile.length()>CaptureLimits.MAX_PCM_BYTES) throw new IOException("Recording too long");
        ensureModel(context);
        Model model = null;
        Recognizer recognizer = null;
        try {
            model = new Model(modelDir(context).getAbsolutePath());
            recognizer = new Recognizer(model, SAMPLE_RATE);
            VoskSegments segments = new VoskSegments();
            try (InputStream in = new BufferedInputStream(new FileInputStream(pcmFile))) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if(Thread.currentThread().isInterrupted()) throw new IOException("Transcription canceled");
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        segments.append(new JSONObject(recognizer.getResult()).optString("text", ""));
                    }
                }
            }
            segments.append(new JSONObject(recognizer.getFinalResult()).optString("text", ""));
            String text = segments.text();
            if (!text.isEmpty()) {
                return text;
            }
            throw new IOException("Offline transcription did not include text.");
        } finally {
            if (recognizer != null) {
                recognizer.close();
            }
            if (model != null) {
                model.close();
            }
        }
    }

    static List<String> defaultTranscriptionModels() {
        List<String> models = new ArrayList<>();
        models.add(MODEL_ID);
        return models;
    }

    private static File modelRoot(Context context) {
        return new File(context.getFilesDir(), "vosk");
    }

    private static File modelDir(Context context) {
        return new File(modelRoot(context), MODEL_ID);
    }

    private static void download(String url, File destination) throws IOException {
        ModelDownload.download(url,destination,80L*1024*1024);
    }

    private static void unzip(File zip, File destinationRoot) throws IOException {
        String rootPath = destinationRoot.getCanonicalPath() + File.separator;
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            int entries=0; long expanded=0;
            byte[] buffer = new byte[8192];
            while ((entry = in.getNextEntry()) != null) {
                if(++entries>256) throw new IOException("Model entry limit exceeded");
                File outFile = ModelFiles.contained(destinationRoot, entry.getName());
                String outPath = outFile.getCanonicalPath();
                if (!outPath.startsWith(rootPath)) {
                    throw new IOException("Blocked unsafe model zip path.");
                }
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Could not create model directory.");
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create model directory.");
                    }
                    try (FileOutputStream fileOut = new FileOutputStream(outFile);
                         BufferedOutputStream out = new BufferedOutputStream(fileOut)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            if(Thread.currentThread().isInterrupted()) throw new IOException("Model setup canceled");
                            expanded+=read;
                            if(expanded>150L*1024*1024) throw new IOException("Expanded model limit exceeded");
                            out.write(buffer, 0, read);
                        }
                    }
                }
                in.closeEntry();
            }
        }
    }
}
