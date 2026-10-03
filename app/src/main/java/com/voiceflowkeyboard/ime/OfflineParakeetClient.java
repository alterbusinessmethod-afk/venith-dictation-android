package com.voiceflowkeyboard.ime;

import android.content.Context;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

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

final class OfflineParakeetClient {
    static final int SAMPLE_RATE = 16000;
    static final String MODEL_ID = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8";
    private static final String MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
            + MODEL_ID + ".tar.bz2";

    private OfflineParakeetClient() {
    }

    static boolean isModelReady(Context context) {
        File dir = modelDir(context);
        return OptionalModelIntegrity.ready(dir) && new File(dir, "encoder.int8.onnx").isFile()
                && new File(dir, "decoder.int8.onnx").isFile()
                && new File(dir, "joiner.int8.onnx").isFile()
                && new File(dir, "tokens.txt").isFile();
    }

    static void ensureModel(Context context) throws IOException {
        if (isModelReady(context) && OptionalModelIntegrity.verify(modelDir(context))) return;
        File root = modelRoot(context);
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Could not create Parakeet model directory.");
        }
        File archive = new File(context.getCacheDir(), MODEL_ID + ".tar.bz2");
        download(MODEL_URL, archive);
        File stage=new File(root,"staging-"+java.util.UUID.randomUUID());
        if(!stage.mkdir()) throw new IOException("Could not stage optional model");
        extractTarBz2(archive, stage);
        File staged=new File(stage,MODEL_ID);
        for(String name:new String[]{"encoder.int8.onnx","decoder.int8.onnx","joiner.int8.onnx","tokens.txt"})
            if(!new File(staged,name).isFile() || new File(staged,name).length()==0) throw new IOException("Incomplete optional model");
        OptionalModelIntegrity.record(staged);
        File target=modelDir(context);
        if(target.exists() && !target.renameTo(new File(root,"invalid-"+java.util.UUID.randomUUID()))) throw new IOException("Could not preserve model");
        if(!staged.renameTo(target)) throw new IOException("Could not activate optional model");
        if (!archive.delete()) {
            archive.deleteOnExit();
        }
        if (!isModelReady(context)) {
            throw new IOException("Parakeet model download did not finish correctly.");
        }
    }

    static String transcribePcm(Context context, File pcmFile) throws Exception {
        if(pcmFile.length()>CaptureLimits.MAX_PCM_BYTES) throw new IOException("Recording too long");
        ensureModel(context);
        OfflineRecognizer recognizer = null;
        OfflineStream stream = null;
        try {
            recognizer = new OfflineRecognizer(null, recognizerConfig(context));
            stream = recognizer.createStream();
            acceptPcm(stream, pcmFile);
            recognizer.decode(stream);
            String text = recognizer.getResult(stream).getText().trim();
            if (!text.isEmpty()) {
                return text;
            }
            throw new IOException("Parakeet transcription did not include text.");
        } finally {
            if (stream != null) {
                stream.release();
            }
            if (recognizer != null) {
                recognizer.release();
            }
        }
    }

    static List<String> defaultTranscriptionModels() {
        List<String> models = new ArrayList<>();
        models.add(MODEL_ID);
        return models;
    }

    private static OfflineRecognizerConfig recognizerConfig(Context context) {
        File dir = modelDir(context);

        FeatureConfig featureConfig = new FeatureConfig();
        featureConfig.setSampleRate(SAMPLE_RATE);
        featureConfig.setFeatureDim(80);
        featureConfig.setDither(0.0f);

        OfflineTransducerModelConfig transducer = new OfflineTransducerModelConfig();
        transducer.setEncoder(new File(dir, "encoder.int8.onnx").getAbsolutePath());
        transducer.setDecoder(new File(dir, "decoder.int8.onnx").getAbsolutePath());
        transducer.setJoiner(new File(dir, "joiner.int8.onnx").getAbsolutePath());

        OfflineModelConfig modelConfig = new OfflineModelConfig();
        modelConfig.setTransducer(transducer);
        modelConfig.setTokens(new File(dir, "tokens.txt").getAbsolutePath());
        modelConfig.setModelType("nemo_transducer");
        modelConfig.setNumThreads(2);
        modelConfig.setDebug(false);

        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(featureConfig);
        config.setModelConfig(modelConfig);
        config.setDecodingMethod("greedy_search");
        return config;
    }

    static void acceptPcm(OfflineStream stream, File pcmFile) throws IOException {
        byte[] buffer = new byte[8192];
        int carry = -1;
        try (InputStream in = new BufferedInputStream(new FileInputStream(pcmFile))) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if(Thread.currentThread().isInterrupted()) throw new IOException("Transcription canceled");
                int offset = 0;
                int sampleCount = read / 2;
                if (carry >= 0 && read > 0) {
                    sampleCount = (read + 1) / 2;
                }
                float[] samples = new float[sampleCount];
                int index = 0;
                if (carry >= 0 && read > 0) {
                    int value = (short) ((buffer[0] & 0xff) << 8 | carry);
                    samples[index++] = value / 32768.0f;
                    offset = 1;
                    carry = -1;
                }
                while (offset + 1 < read) {
                    int low = buffer[offset] & 0xff;
                    int high = buffer[offset + 1] & 0xff;
                    int value = (short) ((high << 8) | low);
                    samples[index++] = value / 32768.0f;
                    offset += 2;
                }
                if (offset < read) {
                    carry = buffer[offset] & 0xff;
                }
                if (index > 0) {
                    if (index != samples.length) {
                        float[] trimmed = new float[index];
                        System.arraycopy(samples, 0, trimmed, 0, index);
                        samples = trimmed;
                    }
                    stream.acceptWaveform(samples, SAMPLE_RATE);
                }
            }
        }
    }

    private static File modelRoot(Context context) {
        return new File(context.getFilesDir(), "sherpa");
    }

    private static File modelDir(Context context) {
        return new File(modelRoot(context), MODEL_ID);
    }

    private static void download(String url, File destination) throws IOException {
        ModelDownload.download(url,destination,800L*1024*1024);
    }

    private static void extractTarBz2(File archive, File destinationRoot) throws IOException {
        String rootPath = destinationRoot.getCanonicalPath() + File.separator;
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(archive));
             BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(fileIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(bzIn)) {
            TarArchiveEntry entry;
            int entries=0; long expanded=0;
            byte[] buffer = new byte[1024 * 64];
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if(++entries>256) throw new IOException("Model entry limit exceeded");
                if(!entry.isDirectory() && !entry.isFile() || entry.isSymbolicLink() || entry.isLink()) throw new IOException("Unsupported model entry");
                File outFile = ModelFiles.contained(destinationRoot, entry.getName());
                String outPath = outFile.getCanonicalPath();
                if (!outPath.startsWith(rootPath)) {
                    throw new IOException("Blocked unsafe Parakeet archive path.");
                }
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Could not create Parakeet model directory.");
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create Parakeet model directory.");
                    }
                    try (FileOutputStream fileOut = new FileOutputStream(outFile);
                         BufferedOutputStream out = new BufferedOutputStream(fileOut)) {
                        int read;
                        while ((read = tarIn.read(buffer)) != -1) {
                            if(Thread.currentThread().isInterrupted()) throw new IOException("Model setup canceled");
                            expanded+=read;
                            if(expanded>900L*1024*1024) throw new IOException("Expanded model limit exceeded");
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
        }
    }
}
