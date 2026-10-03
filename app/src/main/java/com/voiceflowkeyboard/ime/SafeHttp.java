package com.voiceflowkeyboard.ime;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixed HTTPS requests, bounded bodies, and capture-owned cancellation. */
final class SafeHttp {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private SafeHttp() {}

    static final class Scope {
        private final Set<HttpURLConnection> connections = new HashSet<>();
        private boolean canceled;
        synchronized void track(HttpURLConnection connection) throws IOException {
            if(canceled) { connection.disconnect(); throw new IOException("Request canceled"); }
            connections.add(connection);
        }
        synchronized void cancel() {
            canceled=true;
            for(HttpURLConnection connection:connections) connection.disconnect();
            connections.clear();
        }
    }
    static void enter(Scope scope) { CURRENT.set(scope); }
    static void leave() { CURRENT.remove(); }

    static HttpURLConnection open(String address) throws IOException {
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol())) throw new IOException("HTTPS required");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setInstanceFollowRedirects(false);
        Scope scope=CURRENT.get();
        if(scope!=null) scope.track(connection);
        return connection;
    }

    static String response(HttpURLConnection connection, String label) throws IOException {
        try {
            int status=connection.getResponseCode();
            if(status<200 || status>=300) throw new IOException(label+" failed (HTTP "+status+")");
            if(connection.getContentLengthLong()>MAX_RESPONSE_BYTES) throw new IOException("Response too large");
            InputStream stream=connection.getInputStream();
            if(stream==null) return "";
            try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];
                int total=0;
                for(int n;(n=in.read(buffer))!=-1;) {
                    if(Thread.currentThread().isInterrupted()) throw new IOException("Request canceled");
                    total+=n;
                    if(total>MAX_RESPONSE_BYTES) throw new IOException("Response too large");
                    out.write(buffer,0,n);
                }
                return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
