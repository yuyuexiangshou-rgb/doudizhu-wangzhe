package cn.local.tilecounter;

import java.util.*;
public final class SessionCheck {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static final int[] HAND={14,12,12,12,12,9,9,8,8,7,7,7,7,3,2,2,2,2,1,1};
    static PokerVision.Frame frame(int[] hand,int[] middle,int badge){PokerVision.Frame f=new PokerVision.Frame();f.hand=hand;f.center=middle;f.centerRemaining=badge;f.rightRemaining=17;return f;}
    public static void main(String[] args){
        PokerSession s=new PokerSession();PokerVision.Frame f=frame(HAND,new int[0],17);
        s.update(f,0);s.update(f,250);check(!s.ready(),"baseline must stabilize");s.update(f,500);
        check(s.ready()&&s.total()==34&&s.remaining(12)==0&&s.remaining(7)==0,"own hand baseline");
        PokerVision.Frame self=frame(Arrays.copyOfRange(HAND,2,HAND.length),new int[]{8,8},17);s.update(self,550);s.update(self,600);check(s.total()==34,"own table play with unchanged opponent badge is not charged");
        f=frame(Arrays.copyOfRange(HAND,1,HAND.length),new int[]{0},16);s.update(f,750);s.update(f,1000);s.update(f,1250);
        check(s.total()==33&&s.history.size()==1,"automatic opponent debit only");
        for(int i=0;i<20;i++)s.update(f,1500+i*250);check(s.total()==33,"persistent card must not repeat");
        s.update(frame(f.hand,new int[0],16),7000);s.update(frame(f.hand,new int[]{0},16),7500);s.update(frame(f.hand,new int[]{0},16),7750);
        check(s.total()==33,"animation disappearance must not rearm");
        s.update(frame(f.hand,new int[0],16),7800);s.update(frame(f.hand,new int[0],16),7900);
        f=frame(f.hand,new int[]{0},15);s.update(f,8000);s.update(f,8250);check(s.total()==32&&s.history.size()==2,"same rank with decremented badge is a new play");
        s.undo();check(s.total()==33,"undo latest automatic play");s.update(f,8500);check(s.total()==33,"undo must not immediately re-add");
        s.adjust(0,1);check(s.total()==32,"manual correction");s.adjust(0,-1);check(s.total()==33,"manual restore");
        int old=s.total();f=frame(f.hand,new int[]{12},14);s.update(f,9000);s.update(f,9250);check(s.total()==old,"own four twos cannot appear in opponent play");
        PokerSession blocked=new PokerSession();f=frame(new int[]{12,12,9,9,8,8,7,7},new int[0],17);for(int i=0;i<5;i++)blocked.update(f,i*250);check(!blocked.ready(),"partial hand cannot initialize");
        check(PokerSession.legal(new int[]{0,1,2,3,4}),"straight");check(!PokerSession.legal(new int[]{8,9,10,11,12}),"straight cannot contain two");check(PokerSession.legal(new int[]{13,14}),"rocket");
        check(!PokerSession.legal(new int[]{0,1}),"random row rejected");check(PokerSession.legal(new int[]{0,0,0,1,1,1,4,5}),"airplane with singles");
        PokerSession race=new PokerSession();for(int i=0;i<3;i++)race.update(frame(HAND,new int[0],17),i*250);
        for(int i=0;i<3;i++)race.update(frame(HAND,new int[]{0},16),1000+i*250);
        for(int i=0;i<3;i++)race.update(frame(HAND,new int[]{0},14),2000+i*250);
        check(race.total()==33,"new badge arriving before new cards cannot repeat old single");
        for(int i=0;i<3;i++)race.update(frame(HAND,new int[]{4,4},14),3000+i*250);
        check(race.total()==31,"complete new pair accepted after animation");
        PokerSession restarted=new PokerSession();for(int i=0;i<3;i++)restarted.update(frame(HAND,new int[0],17),i*250);
        restarted.update(frame(new int[0],new int[0],17),1000);restarted.update(frame(new int[0],new int[0],17),5000);
        int[] another={13,11,11,10,10,9,9,8,8,7,6,5,4,3,2,1,0};for(int i=0;i<3;i++)restarted.update(frame(another,new int[0],17),5500+i*250);
        check(restarted.total()==34 && restarted.remaining(14)==0,"new deal must never reset without the user's action");
        restarted.reset();for(int i=0;i<3;i++)restarted.update(frame(another,new int[0],17),7000+i*250);
        check(restarted.total()==37 && restarted.remaining(14)==1,"explicit restart creates the next baseline");
        PokerSession missingBadge=new PokerSession();for(int i=0;i<3;i++)missingBadge.update(frame(HAND,new int[0],-1),i*250);
        for(int i=0;i<6;i++)missingBadge.update(frame(HAND,new int[]{0},-1),1000+i*250);
        check(missingBadge.total()==33,"stable opponent cards update automatically even with no badge OCR");
        for(int i=0;i<8;i++)missingBadge.update(frame(HAND,new int[]{0},-1),3000+i*250);
        check(missingBadge.total()==33,"missing badge must not duplicate a persistent play");
        missingBadge.update(frame(HAND,new int[0],-1),5500);missingBadge.update(frame(HAND,new int[0],-1),5750);
        for(int i=0;i<6;i++)missingBadge.update(frame(HAND,new int[]{0},-1),6000+i*250);
        check(missingBadge.total()==32,"same later play after a clear updates without tapping");
        int[] seventeen={13,11,11,11,10,9,9,9,9,7,7,7,6,6,6,0,0};
        PokerSession rules=new PokerSession();PokerVision.Frame bid=frame(seventeen,new int[0],17);bid.bidding=true;
        for(int i=0;i<3;i++)rules.update(bid,i*60);
        check(rules.total()==37&&rules.history.isEmpty(),"17 each plus three unallocated bottom cards");
        PokerVision.Frame assigned=frame(seventeen,new int[0],17);assigned.rightRemaining=20;assigned.bottom=new int[]{1,1,1};
        rules.update(assigned,300);rules.update(assigned,360);
        check(rules.landlord()==2&&rules.total()==37&&rules.bottomCards().length==3,"opponent landlord receives bottom cards without subtracting them twice");
        PokerVision.Frame quick=frame(seventeen,new int[0],17);quick.right=new int[]{1,1,1};quick.rightRemaining=20;quick.bottom=new int[]{1,1,1};rules.update(quick,420);
        assigned.rightRemaining=17;rules.update(assigned,480);rules.update(assigned,540);
        check(rules.total()==34&&rules.history.size()==1,"single-frame legal play retained until delayed badge confirms, no speed-dependent multi-frame gate");
        PokerSession mine=new PokerSession();for(int i=0;i<3;i++)mine.update(bid,i*60);
        int[] twenty=Arrays.copyOf(seventeen,20);twenty[17]=twenty[18]=twenty[19]=1;Arrays.sort(twenty);for(int l=0,r=19;l<r;l++,r--){int t=twenty[l];twenty[l]=twenty[r];twenty[r]=t;}
        PokerVision.Frame myLandlord=frame(twenty,new int[0],17);myLandlord.bottom=new int[]{1,1,1};for(int i=0;i<3;i++)mine.update(myLandlord,300+i*60);
        check(mine.landlord()==0&&mine.ownCount()==20&&mine.total()==34&&mine.remaining(1)==1,"own 17 plus three bottom cards produces exactly 20, no double charge");
        PokerVision.Frame low=frame(new int[]{0},new int[0],17);for(int i=0;i<3;i++)mine.update(low,1000+i*60);
        PokerVision.Frame empty=frame(new int[0],new int[0],-1);empty.rightRemaining=-1;mine.update(empty,1500);mine.update(empty,4200);
        check(mine.finished(),"end scene with no hand or badges freezes after timeout");
        PokerSession bomb=new PokerSession();for(int i=0;i<3;i++)bomb.update(frame(HAND,new int[0],17),i*60);
        for(int i=0;i<3;i++)bomb.update(frame(HAND,new int[]{0},-1),1000+i*60);
        check(bomb.history.isEmpty(),"one visible card in a bomb animation must not commit after 60ms");
        for(int i=0;i<6;i++)bomb.update(frame(HAND,new int[]{0,0,0,0},-1),1200+i*60);
        check(bomb.history.size()==1&&bomb.total()==30,"full bomb commits once after assembly");
        for(int i=0;i<6;i++)bomb.update(frame(HAND,new int[]{0,0},-1),1700+i*60);
        check(bomb.history.size()==1,"fading subset of committed bomb must not become another play");
        System.out.println("PASS: full baseline, own-play exclusion, automatic events, duplicate frames, equal-rank later plays, undo, correction, conflict, partial hand, legal combinations, new deal");
    }
}
