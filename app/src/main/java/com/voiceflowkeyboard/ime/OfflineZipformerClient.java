package com.voiceflowkeyboard.ime;

import android.content.Context;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;
import java.io.*;
import java.util.*;

/** Verified prebundled English ASR; no model network request. */
final class OfflineZipformerClient {
    static final int SAMPLE_RATE = 16000;
    static final String MODEL_ID = "sherpa-onnx-zipformer-gigaspeech-2023-12-12";
    private static final String ASSETS = "models/zipformer-en/";
    private static final String[] FILES = {"encoder-epoch-30-avg-1.int8.onnx",
            "decoder-epoch-30-avg-1.onnx", "joiner-epoch-30-avg-1.int8.onnx", "tokens.txt"};
    private static final long[] SIZES = {72850738,2093081,259417,5020};
    private static final String[] HASHES = {
            "62be226a7b28a38e82cc522184ee01c46751df06bdd070dc9e1572a836ebbe78",
            "78038bec0ef6a53c498a2d1e5b8402e10b3ca1166f344146b9e20f9b7be710a4",
            "80160e45cca71dd52f6b0a6d3d12be18126f5308b2d4ba03f001300fea377c64",
            "0ef7d736bf4de3ef947292e4b119ef13f6808cd5f3aec225a843a7135ac1c2ce"};
    private static File validated;
    private OfflineZipformerClient() {}

    private static File root(Context context) { return new File(context.getFilesDir(), "zipformer-en"); }
    private static File active(Context context) { return new File(root(context), "c9e18578"); }
    static synchronized boolean isModelReady(Context context) {
        File dir = active(context);
        if (!dir.equals(validated)) return false;
        for (int i=0;i<FILES.length;i++) if (new File(dir,FILES[i]).length()!=SIZES[i]) return false;
        return true;
    }

    private static boolean verify(File dir) throws IOException {
        for (int i=0;i<FILES.length;i++) {
            if (!ModelFiles.verified(new File(dir, FILES[i]), SIZES[i], HASHES[i])) return false;
        }
        return true;
    }

    static synchronized void ensureModel(Context context) throws IOException {
        File target = active(context);
        if (isModelReady(context)) return;
        if (verify(target)) { validated=target; return; }
        File parent = root(context);
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not prepare local model storage");
        File stage = new File(parent, "staging-" + UUID.randomUUID());
        if (!stage.mkdir()) throw new IOException("Could not stage local model");
        for (int i=0;i<FILES.length;i++) {
            try (InputStream in=context.getAssets().open(ASSETS+FILES[i])) {
                ModelFiles.copy(in,new File(stage,FILES[i]),SIZES[i]);
            }
        }
        if (!verify(stage)) throw new IOException("Bundled model integrity check failed");
        // Preserve an invalid previous installation for diagnosis, rather than overwrite it.
        if (target.exists() && !target.renameTo(new File(parent,"invalid-"+UUID.randomUUID()))) {
            throw new IOException("Could not preserve previous model");
        }
        if (!stage.renameTo(target)) throw new IOException("Could not activate verified model");
        validated=target;
    }

    static List<String> defaultTranscriptionModels() { return Collections.singletonList(MODEL_ID); }

    static String transcribePcm(Context context, File pcm) throws Exception {
        ensureModel(context);
        if (pcm.length()>CaptureLimits.MAX_PCM_BYTES) throw new IOException("Recording is too long");
        FeatureConfig feature = new FeatureConfig();
        feature.setSampleRate(SAMPLE_RATE); feature.setFeatureDim(80); feature.setDither(0.0f);
        File dir=active(context);
        OfflineTransducerModelConfig transducer = new OfflineTransducerModelConfig();
        transducer.setEncoder(new File(dir,FILES[0]).getAbsolutePath());
        transducer.setDecoder(new File(dir,FILES[1]).getAbsolutePath());
        transducer.setJoiner(new File(dir,FILES[2]).getAbsolutePath());
        OfflineModelConfig model=new OfflineModelConfig();
        model.setTransducer(transducer); model.setTokens(new File(dir,FILES[3]).getAbsolutePath());
        model.setModelType(""); model.setNumThreads(2); model.setDebug(false);
        OfflineRecognizerConfig config=new OfflineRecognizerConfig();
        config.setFeatConfig(feature); config.setModelConfig(model); config.setDecodingMethod("greedy_search");
        OfflineRecognizer recognizer=null;
        OfflineStream stream=null;
        try {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Transcription canceled");
            recognizer=new OfflineRecognizer(null,config);
            stream=recognizer.createStream();
            OfflineParakeetClient.acceptPcm(stream,pcm);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Transcription canceled");
            recognizer.decode(stream);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Transcription canceled");
            String text=recognizer.getResult(stream).getText().trim();
            if (text.isEmpty()) throw new IOException("No speech recognized");
            return text;
        } finally {
            if(stream!=null) stream.release();
            if(recognizer!=null) recognizer.release();
        }
    }
}
