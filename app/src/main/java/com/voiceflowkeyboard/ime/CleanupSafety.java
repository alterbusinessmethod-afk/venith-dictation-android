package com.voiceflowkeyboard.ime;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Deterministic rejection of detectable meaning changes; not a semantic guarantee. */
final class CleanupSafety {
    private static final Pattern PROTECTED=Pattern.compile("https?://[^\\s]+|[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}|\\d+(?:[.,:/-]\\d+)*");
    private static final String[] MEANING={"not","never","no","don't","doesn't","didn't","can't","cannot","won't","isn't","aren't","wasn't","weren't","shouldn't","wouldn't","couldn't","maybe","probably","i think","i guess","kind of","sort of"};
    private static final String[] AFFECTION={"love you","sweetheart","darling","dear","hugs","kisses","thank you"};
    private CleanupSafety() {}

    static String requirePreserved(String source,String output) throws IOException {
        if(output==null || output.trim().isEmpty() || output.length()>CaptureLimits.MAX_TEXT_CHARS) throw new IOException("Cleanup returned incomplete text");
        if(!protectedTokens(source).equals(protectedTokens(output))) throw new IOException("Cleanup changed a protected value; keeping raw text");
        String raw=source.toLowerCase(Locale.ROOT), result=output.toLowerCase(Locale.ROOT);
        for(String phrase:MEANING) if(count(raw,phrase)!=count(result,phrase)) throw new IOException("Cleanup changed uncertainty or negation; keeping raw text");
        for(String phrase:AFFECTION) if(count(result,phrase)>count(raw,phrase)) throw new IOException("Cleanup added sentiment; keeping raw text");
        if(source.contains("`") && !source.equals(output)) throw new IOException("Literal code retained as raw text");
        return output.trim();
    }
    private static List<String> protectedTokens(String text) {
        List<String> tokens=new ArrayList<>();
        Matcher matcher=PROTECTED.matcher(text);
        while(matcher.find()) tokens.add(matcher.group());
        return tokens;
    }
    private static int count(String text,String phrase) {
        Matcher matcher=Pattern.compile("(?<![\\p{L}])"+Pattern.quote(phrase)+"(?![\\p{L}])").matcher(text);
        int count=0;while(matcher.find()) count++;return count;
    }
}
