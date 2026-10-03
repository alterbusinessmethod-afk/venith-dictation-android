package com.voiceflowkeyboard.ime;

import java.io.*;
import java.net.*;

final class ModelDownload {
    private ModelDownload() {}
    static void download(String address,File target,long limit) throws IOException {
        for(int redirects=0;redirects<5;redirects++) {
            URL url=new URL(address);
            String host=url.getHost();
            if(!"https".equals(url.getProtocol()) || !(host.equals("alphacephei.com")
                    || host.equals("github.com") || host.equals("release-assets.githubusercontent.com")
                    || host.equals("objects.githubusercontent.com"))) throw new IOException("Unexpected model host");
            HttpURLConnection connection=SafeHttp.open(address);
            connection.setConnectTimeout(30000); connection.setReadTimeout(60000);
            try {
                int code=connection.getResponseCode();
                if(code>=300 && code<400) {
                    String location=connection.getHeaderField("Location");
                    if(location==null) throw new IOException("Invalid model redirect");
                    address=new URL(url,location).toString(); continue;
                }
                if(code<200 || code>=300) throw new IOException("Model download failed (HTTP "+code+")");
                if(connection.getContentLengthLong()>limit) throw new IOException("Model download too large");
                try(InputStream in=connection.getInputStream()) { ModelFiles.copy(in,target,limit); }
                return;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many model redirects");
    }
}
