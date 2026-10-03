package com.voiceflowkeyboard.ime;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local download receipt, NOT a trusted upstream hash or provenance attestation. */
final class OptionalModelIntegrity {
    private OptionalModelIntegrity() {}
    static boolean ready(File dir) {
        return new File(dir,"local-integrity.tsv").isFile();
    }
    static void record(File dir) throws IOException {
        List<File> files=new ArrayList<>();
        collect(dir,files,0);
        if(files.isEmpty()) throw new IOException("Empty optional model");
        StringBuilder receipt=new StringBuilder("# Local integrity only; source archive is not independently pinned\n");
        String root=dir.getCanonicalPath()+File.separator;
        for(File file:files) {
            String name=file.getCanonicalPath().substring(root.length()).replace(File.separatorChar,'/');
            if(name.contains("\t") || name.contains("\n")) throw new IOException("Invalid model filename");
            receipt.append(name).append('\t').append(file.length()).append('\t').append(ModelFiles.sha256(file)).append('\n');
        }
        try(FileOutputStream out=new FileOutputStream(new File(dir,"local-integrity.tsv"))) {
            out.write(receipt.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
        }
    }
    private static void collect(File dir,List<File> files,int depth) throws IOException {
        if(depth>8) throw new IOException("Model nesting limit exceeded");
        File[] children=dir.listFiles();
        if(children==null) throw new IOException("Model directory unreadable");
        for(File child:children) {
            if(child.isDirectory()) collect(child,files,depth+1);
            else if(!child.getName().equals("local-integrity.tsv")) files.add(child);
            if(files.size()>256) throw new IOException("Model entry limit exceeded");
        }
    }
    static boolean verify(File dir) throws IOException {
        File receipt=new File(dir,"local-integrity.tsv");
        if(!receipt.isFile() || receipt.length()>65536) return false;
        int count=0;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(receipt),StandardCharsets.UTF_8))) {
            for(String line;(line=in.readLine())!=null;) {
                if(line.startsWith("#")) continue;
                String[] fields=line.split("\t",-1);
                if(fields.length!=3 || ++count>256) return false;
                long size;
                try { size=Long.parseLong(fields[1]); } catch(NumberFormatException e) { return false; }
                if(!ModelFiles.verified(ModelFiles.contained(dir,fields[0]),size,fields[2])) return false;
            }
        }
        return count>0;
    }
}
