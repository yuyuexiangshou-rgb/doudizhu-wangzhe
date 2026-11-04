package cn.local.tilecounter;

import java.util.*;

/** Tracks own starting cards separately from opponents' new plays. All time comes from the caller. */
public final class PokerSession {
    public static final String[] LABELS={"3","4","5","6","7","8","9","10","J","Q","K","A","2","小王","大王"};
    public static final class Play {
        public final int seat;public final int[] cards;final int previousBadge;
        Play(int s,int[] c,int badge){seat=s;cards=c.clone();previousBadge=badge;}
    }
    private static final class Seat {
        int[] observed,lastCommitted;int stable,blankFrames;boolean cleared;
        int remaining=-1,observedBadge=-1,badgeStable;
        int[] pending;long pendingAt;
        long observedAt;
        void reset(){observed=null;lastCommitted=null;stable=0;blankFrames=0;cleared=false;remaining=-1;observedBadge=-1;badgeStable=0;}
    }
    final Seat[] seats={new Seat(),new Seat()};
    private int[] own=new int[15],current=new int[15],played=new int[15],adjustment=new int[15],lastHand;
    private int handStable;
    private long absentSince=-1;
    private boolean initialized,incomplete;
    private boolean finished;
    private long ownMovedAt=-10000,now;
    private int landlord=-1;
    private int[] bottom=new int[0];
    private long emptySince=-1;
    public int landlord(){return landlord;}
    public int[] bottomCards(){return bottom.clone();}
    public String status="等待发牌 · 自动识别";
    public final List<Play> history=new ArrayList<>();
    public boolean ready(){return initialized;}
    public boolean finished(){return finished;}
    public boolean incomplete(){return incomplete;}
    public int remaining(int rank){return capacity(rank)-own[rank]-played[rank]-adjustment[rank];}
    public int total(){int sum=0;for(int i=0;i<15;i++)sum+=remaining(i);return sum;}
    public int ownCount(){return sum(current);}
    public int badge(int seat){Seat s=seats[seat];return s.badgeStable>=2&&s.observedBadge>=0?s.observedBadge:s.remaining;}
    public static int capacity(int rank){return rank>=13?1:4;}
    public static int[] histogram(int[] cards){int[] out=new int[15];for(int c:cards){if(c<0||c>=15)throw new IllegalArgumentException("无效牌点");out[c]++;}return out;}
    private static int sum(int[] a){int n=0;for(int i:a)n+=i;return n;}
    private static boolean subset(int[] a,int[] b){for(int i=0;i<15;i++)if(a[i]>b[i])return false;return true;}
    private static boolean validCounts(int[] a){for(int i=0;i<15;i++)if(a[i]<0||a[i]>capacity(i))return false;return true;}
    public void reset(){own=new int[15];current=new int[15];played=new int[15];adjustment=new int[15];initialized=false;incomplete=false;finished=false;landlord=-1;bottom=new int[0];emptySince=-1;ownMovedAt=-10000;history.clear();lastHand=null;handStable=0;absentSince=-1;for(Seat s:seats){s.reset();s.pending=null;}status="等待17/20张完整起手牌";}
    public void update(PokerVision.Frame frame,long now) {
        this.now=now;
        if(finished){status="本局已结束 · 点重新开始";return;}
        if(frame.bottom.length==3)bottom=frame.bottom.clone();
        if(frame.hand.length==20)landlord=0;else if(frame.centerRemaining==20)landlord=1;else if(frame.rightRemaining==20)landlord=2;
        if(initialized && frame.hand.length==0 && frame.centerRemaining<0 && frame.rightRemaining<0){
            if(emptySince<0)emptySince=now;
            if(now-emptySince>=2500 && (sum(current)<=4 || seats[0].remaining<=4 && seats[0].remaining>=0 || seats[1].remaining<=4 && seats[1].remaining>=0)){finished=true;status="本局已结束 · 点重新开始";return;}
        }else emptySince=-1;
        if(frame.hand.length==0){if(absentSince<0)absentSince=now;lastHand=null;handStable=0;}
        else {
            if(Arrays.equals(lastHand,frame.hand))handStable++;else{lastHand=frame.hand.clone();handStable=1;}
            if(handStable>=3) {
                int[] hand=histogram(frame.hand);
                if(validCounts(hand)) {
                    boolean full=frame.hand.length==17 || frame.hand.length==20;
                    if(initialized && full && !subset(hand,own) && !(frame.hand.length==20 && sum(own)==17 && history.isEmpty() && subset(own,hand))){finished=true;status="请点重新开始记牌";return;}
                    if(!initialized && full) {own=hand.clone();current=hand.clone();initialized=true;for(int i=0;i<2;i++){int badge=i==0?frame.centerRemaining:frame.rightRemaining;seats[i].remaining=badge>=0?badge:frame.hand.length==20?17:-1;}status="自动记牌中";}
                    else if(initialized) {
                        if(frame.hand.length==20 && sum(own)==17 && history.isEmpty() && subset(own,hand))own=hand.clone();
                        if(subset(hand,own)){if(!Arrays.equals(current,hand))ownMovedAt=now;current=hand;}
                    }
                }
                absentSince=-1;
            }
        }
        if(!initialized) {status=frame.hand.length>0?"手牌未完整 · 等待17/20张":"等待发牌 · 自动识别";return;}
        if(landlord==0 && sum(own)==17 && bottom.length==3 && history.isEmpty()){
            int[] full=own.clone();for(int c:bottom)full[c]++;if(validCounts(full))own=full;
        }
        if(frame.bidding){
            if(!history.isEmpty()){finished=true;status="上一局结束 · 点重新开始";}
            else status="叫地主 · 3张底牌待分配";
            return;
        }
        status=incomplete?"有漏帧 · 牌数可能偏多":"自动记牌中";
        observe(0,frame.center,frame.centerRemaining);observe(1,frame.right,frame.rightRemaining);
        if(frame.self.length>0 && frame.hand.length==0 && sum(current)==frame.self.length && subset(histogram(frame.self),own)){finished=true;status="本局已结束 · 点重新开始";}
    }
    private void observe(int seat,int[] cards,int badge) {
        Seat s=seats[seat];
        if(badge>=0 && badge==s.observedBadge)s.badgeStable++;else{s.observedBadge=badge;s.badgeStable=badge>=0?1:0;}
        if(s.pending!=null && now-s.pendingAt>800)s.pending=null;
        if(cards.length>0 && legal(cards) && (!Arrays.equals(s.lastCommitted,cards)||s.cleared)) {s.pending=cards.clone();s.pendingAt=now;}
        if(cards.length==0 && s.pending!=null && s.badgeStable>=2 && s.remaining-badge==s.pending.length && badge>=0){
            int[] candidate=s.pending;s.pending=null;commit(seat,candidate,badge);return;
        }
        if(cards.length==0) {s.observed=null;s.stable=0;if(++s.blankFrames>=2)s.cleared=true;if(s.remaining<0 && badge>=0 || history.isEmpty() && s.remaining==17 && badge==20)s.remaining=badge;return;}
        s.blankFrames=0;
        if(Arrays.equals(s.observed,cards))s.stable++;else{s.observed=cards.clone();s.stable=1;s.observedAt=now;}
        if(s.stable<2 || !legal(cards))return;
        if(history.isEmpty() && sum(own)==17 && s.remaining==17 && badge>17)s.remaining=20;
        boolean corroborated=s.remaining>=0 && badge>=0 && s.badgeStable>=2;
        if(!corroborated && (s.stable<2 || now-s.observedAt<250 || now-ownMovedAt<300 || handStable<2)){status="正在跟踪出牌画面";return;}
        boolean same=Arrays.equals(s.lastCommitted,cards);
        int drop=s.remaining>=0 && badge>=0?s.remaining-badge:-1;
        if(s.lastCommitted!=null && !s.cleared && subset(histogram(cards),histogram(s.lastCommitted)) && (!corroborated||drop<=0))return;
        if(same && (!s.cleared || corroborated && drop<=0))return;
        if(same && corroborated && drop!=cards.length){status="同点数出牌 · 等待换牌画面";return;}
        if(corroborated && drop==0)return;
        if(drop>0 && drop<cards.length){status="等待牌面与剩余张数同步";return;}
        if(drop>cards.length && now-s.observedAt<1200){status="等待完整出牌画面";return;}
        if(badge>s.remaining && s.remaining>=0){status="检测到重新发牌 · 等待起手牌";return;}
        if(drop>cards.length){incomplete=true;status="缺少出牌证据 · 未推测牌点";}
        commit(seat,cards,badge);
    }
    private void commit(int seat,int[] cards,int badge){
        Seat s=seats[seat];int drop=s.remaining>=0&&badge>=0?s.remaining-badge:-1;
        int[] delta=histogram(cards);
        for(int i=0;i<15;i++)if(delta[i]>remaining(i)){status="识别冲突 · 未扣牌";return;}
        int prior=s.remaining;
        for(int i=0;i<15;i++)played[i]+=delta[i];
        if(drop>cards.length){incomplete=true;status="有漏帧 · 牌数可能偏多";}
        history.add(new Play(seat,cards,prior));s.lastCommitted=cards.clone();s.pending=null;s.cleared=false;s.remaining=badge>=0?badge:s.remaining>=cards.length?s.remaining-cards.length:-1;
        if(s.remaining==0){finished=true;status="本局已结束 · 点重新开始";}
    }
    public boolean undo() {
        if(history.isEmpty())return false;
        Play last=history.remove(history.size()-1);for(int c:last.cards)played[c]--;
        Seat s=seats[last.seat];
        // Retain the rejected signature and current badge so the same visible mistake is not auto-added again.
        status="已撤销 · 当前画面不会重复入账";return true;
    }
    public void adjust(int rank,int amount) {
        int next=remaining(rank)-amount;
        if(next<0||next>capacity(rank))throw new IllegalArgumentException("超出整副牌数量");
        adjustment[rank]+=amount;
    }
    public static String describe(int[] cards){StringBuilder s=new StringBuilder();for(int c:cards){if(s.length()>0)s.append(' ');s.append(LABELS[c]);}return s.toString();}
    public static boolean legal(int[] cards) {
        if(cards.length==0||cards.length>20)return false;
        int[] n=histogram(cards);if(!validCounts(n))return false;
        int pairs=0,triples=0,fours=0,distinct=0;for(int k:n){if(k>0)distinct++;if(k==2)pairs++;if(k==3)triples++;if(k==4)fours++;}
        int size=cards.length;
        if(size==1 || size==2 && (pairs==1 || n[13]+n[14]==2) || size==3 && triples==1 || size==4 && (fours==1||triples==1) || size==5 && triples==1&&pairs==1)return true;
        if(size==6&&fours==1 || size==8&&fours==1&&pairs==2)return true;
        for(int repeat=1;repeat<=3;repeat++) {
            int needed=repeat==1?5:repeat==2?3:2;
            for(int start=0;start<=11;start++)for(int end=start+needed-1;end<=11;end++) {
                boolean ok=true;int body=end-start+1;
                for(int i=start;i<=end;i++)if(n[i]!=repeat)ok=false;
                if(!ok)continue;
                int base=body*repeat;if(base==size)return true;
                if(repeat==3 && size==base+body)return true;
                if(repeat==3 && size==base+body*2 && pairs==body)return true;
            }
        }
        return false;
    }
}
