package cn.local.tilecounter;

import java.util.*;

/** Counts unseen tiles. An item is entered once when it first becomes known. */
public final class Counter {
    public static final String[] MODES = {"麻将 · 万条筒 108 张", "麻将 · 含字牌 136 张", "斗地主 · 54 张"};
    private String[] labels;
    private int[] capacity, known;
    private final Deque<int[]> history = new ArrayDeque<>();
    public Counter(int mode) { reset(mode); }
    public void reset(int mode) {
        List<String> names = new ArrayList<>();
        if (mode == 2) Collections.addAll(names, "3","4","5","6","7","8","9","10","J","Q","K","A","2","小王","大王");
        else {
            String[] numbers = {"一","二","三","四","五","六","七","八","九"};
            for (String suit : new String[]{"万","条","筒"}) for (String n : numbers) names.add(n + suit);
            if (mode == 1) Collections.addAll(names, "东","南","西","北","中","发","白");
        }
        labels = names.toArray(new String[0]); capacity = new int[labels.length]; known = new int[labels.length];
        Arrays.fill(capacity, 4);
        if (mode == 2) capacity[13] = capacity[14] = 1;
        history.clear();
    }
    public String[] labels() { return labels.clone(); }
    public int size() { return labels.length; }
    public int[] snapshot() { return known.clone(); }
    public void load(int[] values) {
        if(values.length!=size()) throw new IllegalArgumentException("存档牌种不匹配");
        for(int i=0;i<size();i++) if(values[i]<0 || values[i]>capacity[i]) throw new IllegalArgumentException("存档数量无效");
        known=values.clone();history.clear();
    }
    public int remaining(int i) { return capacity[i] - known[i]; }
    public int unseen() { int n = 0; for(int i=0;i<size();i++) n += remaining(i); return n; }
    public void add(int[] indices) {
        if(indices.length == 0) throw new IllegalArgumentException("没有可记入的牌");
        int[] delta = new int[size()];
        for(int i : indices) { if(i < 0 || i >= size()) throw new IllegalArgumentException("牌种无效"); delta[i]++; }
        for(int i=0;i<size();i++) if(delta[i] > remaining(i)) throw new IllegalArgumentException(labels[i] + "超出整副牌数量，请先修正或撤销");
        for(int i=0;i<size();i++) known[i] += delta[i];
        history.push(delta);
    }
    public boolean undo() {
        if(history.isEmpty()) return false;
        int[] delta = history.pop(); for(int i=0;i<size();i++) known[i] -= delta[i]; return true;
    }
    public void restoreOne(int i) {
        if(known[i] == 0) throw new IllegalArgumentException("这张牌尚未记入");
        int[] delta = new int[size()]; delta[i] = -1; known[i]--; history.push(delta);
    }
    public String describe(int[] indices) {
        StringBuilder s = new StringBuilder();
        for(int i : indices) { if(s.length()>0) s.append(" "); s.append(labels[i]); } return s.toString();
    }
}
