package cn.local.tilecounter;

import java.util.Arrays;

/** Platform-independent matching and debounce, shared by the Android scanner and checks. */
public final class Recognition {
    public static float correlation(float[] a, float[] b) {
        if(a.length != b.length || a.length == 0) return -1;
        double am=0,bm=0; for(int i=0;i<a.length;i++) { am+=a[i]; bm+=b[i]; }
        am/=a.length; bm/=b.length;
        double dot=0,aa=0,bb=0;
        for(int i=0;i<a.length;i++) { double x=a[i]-am,y=b[i]-bm; dot+=x*y;aa+=x*x;bb+=y*y; }
        if(aa < 1e-6 || bb < 1e-6) return -1;
        return (float)(dot / Math.sqrt(aa*bb));
    }
    public static int best(float[] sample, float[][] templates, float threshold, float margin) {
        float best=-1, second=-1; int index=-1;
        int count=0;
        for(int i=0;i<templates.length;i++) if(templates[i]!=null) {
            count++; float score=correlation(sample,templates[i]);
            if(score>best) { second=best;best=score;index=i; } else if(score>second) second=score;
        }
        // shortcut: a partial template library is conservative; build all game tile templates before relying on it.
        return count >= 2 && best >= threshold && best-second >= margin ? index : -1;
    }
    public static final class Gate {
        private int[] last, committed;
        private int stable;
        public int[] observe(int[] value) {
            if(value==null || value.length==0) { last=null; stable=0; return null; }
            if(Arrays.equals(last,value)) stable++; else { last=value.clone();stable=1; }
            return stable>=3 && !Arrays.equals(committed,value) ? value.clone() : null;
        }
        public void commit(int[] value) { committed=value.clone(); }
        /** Explicitly re-arm for a later identical play; disappearance alone is not a new turn. */
        public void rearm() { committed=null;last=null;stable=0; }
    }
}
