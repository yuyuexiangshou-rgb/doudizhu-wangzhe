package cn.local.tilecounter;

public class CoreCheck {
    static void check(boolean ok, String message) { if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        Counter c = new Counter(0); check(c.unseen()==108,"108 tiles");
        c.add(new int[]{0,0,0}); check(c.remaining(0)==1,"duplicates in batch");
        try { c.add(new int[]{0,0,1}); throw new AssertionError("overflow accepted"); } catch(IllegalArgumentException expected) {}
        check(c.remaining(0)==1 && c.remaining(1)==4,"batch must be atomic");
        c.restoreOne(0);check(c.remaining(0)==2,"manual correction");c.undo();check(c.remaining(0)==1,"undo correction");
        c.undo();check(c.unseen()==108,"undo batch");
        c.add(new int[]{2,2,4});int[] saved=c.snapshot();Counter loaded=new Counter(0);loaded.load(saved);
        check(loaded.remaining(2)==2 && loaded.remaining(4)==3,"restore persisted counts");
        saved[0]=99;try {loaded.load(saved);throw new AssertionError("invalid save accepted");}catch(IllegalArgumentException expected){}
        check(loaded.remaining(0)==4 && loaded.remaining(2)==2,"invalid restore atomic");
        c.reset(1);check(c.unseen()==136 && c.size()==34,"honors");
        c.reset(2);check(c.unseen()==54 && c.remaining(14)==1,"jokers");
        Recognition.Gate g=new Recognition.Gate();int[] a={1,2};
        check(g.observe(a)==null && g.observe(a)==null && g.observe(a)!=null,"three-frame stability");
        g.commit(a);check(g.observe(a)==null,"no repeat debit");
        g.observe(null);check(g.observe(a)==null && g.observe(a)==null && g.observe(a)==null,"animation gap must not rearm");
        g.rearm();g.observe(a);g.observe(a);check(g.observe(a)!=null,"identical play explicitly rearmed");
        float[] x={0,1,0,1,1,0}, y={1,0,1,0,0,1};
        check(Recognition.best(x,new float[][]{x,y},.94f,.06f)==0,"exact recognition");
        check(Recognition.best(x,new float[][]{x,x},.94f,.06f)==-1,"ambiguous rejected");
        check(Recognition.best(new float[]{2,2,2,2,2,2},new float[][]{x,y},.94f,.06f)==-1,"blank rejected");
        check(Recognition.best(x,new float[][]{x,null},.94f,.06f)==-1,"one-template unsafe matching");
        System.out.println("PASS: counts, atomic validation, correction, undo, modes, debounce, matching ambiguity");
    }
}
