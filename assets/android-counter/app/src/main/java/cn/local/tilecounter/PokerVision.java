package cn.local.tilecounter;

import java.io.*;
import java.util.*;

/** Card-corner recognition on RGB pixels. No Android APIs, shared with screenshot replay checks. */
public final class PokerVision {
    public static final int ONE=15,ZERO=16,O=17,SPADE=18,HEART=19,CLUB=20,DIAMOND=21;
    static final int GW=20,GH=28;
    static final class Template {
        int token;float aspect;double logAspect;long[] bits=new long[(GW*GH+63)/64];
        Template(String line) {String[] s=line.split(" ");token=Integer.parseInt(s[0]);aspect=Float.parseFloat(s[1]);logAspect=Math.log(aspect);for(int i=0;i<GW*GH;i++)if(s[2].charAt(i)=='1')bits[i/64]|=1L<<(i%64);}
    }
    public static final class Glyph {
        public int l,t,r,b,token=-1; public float score; boolean red;
        int w(){return r-l;}int h(){return b-t;}int cx(){return (l+r)/2;}
        public String toString(){return token+"@"+l+","+t+","+w()+","+h()+":"+String.format(Locale.US,"%.2f",score);}
    }
    public static final class Card {
        public final int rank,l,t,r,b; public final float score;
        Card(int k,Glyph g){rank=k;l=g.l;t=g.t;r=g.r;b=g.b;score=g.score;}
    }
    public static final class Frame {
        public int[] hand=new int[0],center=new int[0],right=new int[0],self=new int[0];
        public final List<Card> cards=new ArrayList<>();
        public final List<Glyph> glyphs=new ArrayList<>();
        public boolean partialHand;
        public int centerRemaining=-1,rightRemaining=-1;
        public int[] bottom=new int[0];
        public boolean bidding;
        public String diagnostic="";
    }
    final List<Template> templates=new ArrayList<>();
    final List<Template> digits=new ArrayList<>();
    private boolean[] workMask,workSeen;
    private int[] workQueue;
    public PokerVision(InputStream input) throws IOException {
        try(BufferedReader r=new BufferedReader(new InputStreamReader(input,"US-ASCII"))) {String s;while((s=r.readLine())!=null)if(!s.trim().isEmpty())templates.add(new Template(s));}
        if(templates.size()<20)throw new IOException("内置识牌资源不完整");
    }
    public PokerVision(InputStream input,InputStream numberInput) throws IOException {
        this(input);
        try(BufferedReader r=new BufferedReader(new InputStreamReader(numberInput,"US-ASCII"))) {String s;while((s=r.readLine())!=null)if(!s.trim().isEmpty())digits.add(new Template(s));}
    }
    private static boolean light(int p) {
        int r=(p>>16)&255,g=(p>>8)&255,b=p&255,lo=Math.min(r,Math.min(g,b)),hi=Math.max(r,Math.max(g,b));
        return lo>155 && hi-lo<48;
    }
    private static boolean red(int p) {int r=(p>>16)&255,g=(p>>8)&255,b=p&255;return r>100 && r>g*1.30 && r>b*1.20 && g<175;}
    private static boolean ink(int p) {
        int r=(p>>16)&255,g=(p>>8)&255,b=p&255,lo=Math.min(r,Math.min(g,b)),hi=Math.max(r,Math.max(g,b));
        return (hi<145 && hi-lo<60)||red(p);
    }
    public Frame read(int[] pixels,int width,int height) {
        if(width<320 || height<160 || pixels.length!=width*height)throw new IllegalArgumentException("画面尺寸无效");
        Frame frame=new Frame();
        if((float)width/height<1.45f) {frame.diagnostic="请横屏并分享整个游戏画面";return frame;}
        if(workMask==null || workMask.length!=pixels.length){workMask=new boolean[pixels.length];workSeen=new boolean[pixels.length];workQueue=new int[pixels.length];}
        boolean[] mask=workMask,seen=workSeen;Arrays.fill(seen,false);
        int y0=(int)(height*.01),y1=(int)(height*.93),x0=(int)(width*.08),x1=(int)(width*.96);
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)mask[y*width+x]=ink(pixels[y*width+x]);
        int[] queue=workQueue;List<Glyph> glyphs=new ArrayList<>();
        int minH=Math.max(9,(int)(height*.014));
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
            int start=y*width+x;if(!mask[start]||seen[start])continue;
            int head=0,tail=1;queue[0]=start;seen[start]=true;
            int l=x,r=x,t=y,b=y,redCount=0;
            while(head<tail) {
                int p=queue[head++],xx=p%width,yy=p/width;l=Math.min(l,xx);r=Math.max(r,xx);t=Math.min(t,yy);b=Math.max(b,yy);if(red(pixels[p]))redCount++;
                if(xx>x0)tail=push(p-1,mask,seen,queue,tail);
                if(xx+1<x1)tail=push(p+1,mask,seen,queue,tail);
                if(yy>y0)tail=push(p-width,mask,seen,queue,tail);
                if(yy+1<y1)tail=push(p+width,mask,seen,queue,tail);
                {
                    if(xx>x0 && yy>y0)tail=push(p-width-1,mask,seen,queue,tail);
                    if(xx+1<x1 && yy>y0)tail=push(p-width+1,mask,seen,queue,tail);
                    if(xx>x0 && yy+1<y1)tail=push(p+width-1,mask,seen,queue,tail);
                    if(xx+1<x1 && yy+1<y1)tail=push(p+width+1,mask,seen,queue,tail);
                }
            }
            int w=r-l+1,h=b-t+1;
            if(tail<14 || h<minH || h>height*.115 || w<3 || w>height*.13)continue;
            Glyph g=new Glyph();g.l=l;g.r=r+1;g.t=t;g.b=b+1;g.red=redCount>tail/2;
            float support=whiteSupport(pixels,width,height,g);
            if(support<.18f)continue;
            classify(g,mask,width);
            if(support<.33f && !(support>=.18f && (g.token==ONE||g.token==ZERO)))continue;
            glyphs.add(g);
        }
        List<Card> found=new ArrayList<>();
        for(Glyph g:glyphs) {
            int rank=g.token;
            if(rank==ONE) {
                Glyph zero=null;
                for(Glyph q:glyphs)if(q.token==ZERO && q.l>=g.r-1 && q.l-g.r<g.h()*.28f && Math.abs(q.t-g.t)<g.h()*.15f && Math.abs(q.h()-g.h())<g.h()*.2f) {zero=q;break;}
                if(zero==null)continue;
                Glyph combined=new Glyph();combined.l=g.l;combined.t=Math.min(g.t,zero.t);combined.r=zero.r;combined.b=Math.max(g.b,zero.b);combined.score=Math.min(g.score,zero.score);combined.red=g.red;g=combined;rank=7;
            } else if(rank<0 || rank>12)continue;
            if(g.score<.78f)continue;
            Glyph below=null;
            for(Glyph q:glyphs)if(q.t>=g.b && q.t-g.b<g.h()*1.1f && Math.abs(q.cx()-g.cx())<g.h()*.40f && q.h()<g.h()*1.3f && q.h()>g.h()*.30f) {
                if(below==null || q.t<below.t)below=q;
            }
            if(below==null)continue;
            if(rank==8 && below.token==O && below.score>=.80f)rank=g.red?14:13;
            else if(below.token<SPADE || below.token>DIAMOND || below.score<.76f)continue;
            found.add(new Card(rank,g));
        }
        // Miniature JOKER text can have disconnected J strokes. Reconstruct its top letter from O + R anchors.
        for(Glyph o:glyphs)if(o.token==O && o.score>=.84f){
            boolean tail=false;for(Glyph r:glyphs)if(r.token==23 && r.t>o.b && r.t-o.b<o.h()*4 && Math.abs(r.cx()-o.cx())<o.w()*.7f)tail=true;
            if(!tail)continue;
            int l=Math.max(x0,o.l-2),r=Math.min(x1,o.r+2),t=Math.max(y0,o.t-(int)(o.h()*1.9f)),b=o.t-1;
            Glyph j=new Glyph();j.l=r;j.r=l;j.t=b;j.b=t;int nr=0,ni=0;
            for(int yy=t;yy<b;yy++)for(int xx=l;xx<r;xx++)if(mask[yy*width+xx]){j.l=Math.min(j.l,xx);j.r=Math.max(j.r,xx+1);j.t=Math.min(j.t,yy);j.b=Math.max(j.b,yy+1);ni++;if(red(pixels[yy*width+xx]))nr++;}
            if(ni<5||j.w()<3||j.h()<6)continue;classify(j,mask,width);
            if(j.token!=8 || j.score<.78)continue;j.red=nr>ni/2;
            boolean exists=false;for(Card card:found)if(Math.abs(card.l-j.l)<o.w()&&Math.abs(card.t-j.t)<o.h())exists=true;
            if(!exists)found.add(new Card(j.red?14:13,j));
        }
        found.sort(Comparator.comparingInt(c->c.l));
        frame.glyphs.addAll(glyphs);
        List<Card> hand=new ArrayList<>(),center=new ArrayList<>(),right=new ArrayList<>(),self=new ArrayList<>(),bottom=new ArrayList<>(),publicCards=new ArrayList<>();
        for(Card c:found) {
            if(c.t<height*.10 && c.l>width*.46 && c.l<width*.54)bottom.add(c);
            else if(c.t>height*.56)hand.add(c);
            else if(c.t>=height*.50 && c.l<width*.65)self.add(c);
            else if(c.t>height*.17 && c.t<height*.50 && c.l>width*.20)publicCards.add(c);
        }
        // Assign whole spatial rows to a player. Splitting at a screen x-coordinate cut long plays in half.
        List<List<Card>> rows=new ArrayList<>();
        for(Card c:publicCards){
            List<Card> group=null;
            for(List<Card> candidate:rows){Card last=candidate.get(candidate.size()-1);float gh=Math.max(c.b-c.t,last.b-last.t);if(Math.abs(c.t-last.t)<gh*.45f && c.l-last.r<gh*2.5f){group=candidate;break;}}
            if(group==null){group=new ArrayList<>();rows.add(group);}group.add(c);
        }
        for(List<Card> group:rows){
            float midpoint=(group.get(0).l+group.get(group.size()-1).r)/2f;
            if(midpoint<width*.55)center.addAll(group);else right.addAll(group);
        }
        frame.hand=row(hand,true);frame.partialHand=hand.size()!=frame.hand.length;
        frame.center=row(center,false);frame.right=row(right,false);
        frame.self=row(self,false);
        int[] bottomRow=row(bottom,false);if(bottomRow.length==3)frame.bottom=bottomRow;
        int blue=0,n=0;
        for(int yy=(int)(height*.02);yy<height*.075;yy+=3)for(int xx=(int)(width*.472);xx<width*.528;xx+=3){int p=pixels[yy*width+xx],r=(p>>16)&255,g=(p>>8)&255,b=p&255;n++;if(b>r*1.15 && b>g*1.05 && r<180)blue++;}
        frame.bidding=frame.bottom.length==0 && n>0 && blue>n*.65;
        frame.centerRemaining=findBadge(pixels,width,height,.47f,.61f,.128f,.197f);
        frame.rightRemaining=findBadge(pixels,width,height,.81f,.91f,.155f,.230f);
        frame.cards.addAll(hand);frame.cards.addAll(center);frame.cards.addAll(right);
        frame.diagnostic="手牌 "+frame.hand.length+" / 中 "+frame.center.length+" / 右 "+frame.right.length;
        return frame;
    }
    private static int push(int p,boolean[] mask,boolean[] seen,int[] q,int tail) {if(mask[p]&&!seen[p]){seen[p]=true;q[tail++]=p;}return tail;}
    private static float whiteSupport(int[] p,int w,int h,Glyph g) {
        int pad=Math.max(2,g.h()/12),n=0,yes=0;
        for(int y=Math.max(0,g.t-pad);y<Math.min(h,g.b+pad);y+=2)for(int x=Math.max(0,g.l-pad);x<Math.min(w,g.r+pad);x+=2){n++;if(light(p[y*w+x]))yes++;}
        return n==0?0:(float)yes/n;
    }
    private void classify(Glyph g,boolean[] mask,int width) {classify(g,mask,width,templates);}
    private void classify(Glyph g,boolean[] mask,int width,List<Template> library) {
        long[] vector=new long[(GW*GH+63)/64];
        for(int y=0;y<GH;y++)for(int x=0;x<GW;x++)if(mask[(g.t+Math.min(g.h()-1,(int)((y+.5)*g.h()/GH)))*width+g.l+Math.min(g.w()-1,(int)((x+.5)*g.w()/GW))]){int bit=y*GW+x;vector[bit/64]|=1L<<(bit%64);}
        float[] scores=new float[24];Arrays.fill(scores,-1);
        float aspect=(float)g.w()/g.h();
        double logAspect=Math.log(aspect);
        for(Template t:library) {
            int mismatch=0;for(int i=0;i<vector.length;i++)mismatch+=Long.bitCount(vector[i]^t.bits[i]);
            float score=1-(float)mismatch/(GW*GH)-(float)(Math.abs(logAspect-t.logAspect)*.12);
            scores[t.token]=Math.max(scores[t.token],score);
        }
        float best=-1,second=-1;int token=-1;
        for(int i=0;i<scores.length;i++)if(scores[i]>best){second=best;best=scores[i];token=i;}else second=Math.max(second,scores[i]);
        g.score=best;g.token=best>=.78 && best-second>=.016?token:-1;
        // Suit identity is irrelevant to rank counting; an ambiguous club/spade still validates the card corner.
        int suit=SPADE;for(int i=SPADE+1;i<=DIAMOND;i++)if(scores[i]>scores[suit])suit=i;
        if(library==templates && g.token<0 && scores[suit]>=.80f && best-scores[suit]<.025f)g.token=suit;
    }
    private int badge(int[] pixels,int width,int height,float l,float t,float r,float b) {
        if(digits.isEmpty())return -1;
        int x0=(int)(l*width),y0=(int)(t*height),w=(int)((r-l)*width),h=(int)((b-t)*height);
        if(w<=0||h<=0)return -1;
        boolean[] mask=new boolean[w*h],seen=new boolean[w*h];int[] q=new int[w*h];List<Glyph> found=new ArrayList<>();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {int p=pixels[(y+y0)*width+x+x0],rr=(p>>16)&255,g=(p>>8)&255,bb=p&255;mask[y*w+x]=Math.min(rr,Math.min(g,bb))>195 && Math.max(rr,Math.max(g,bb))-Math.min(rr,Math.min(g,bb))<55;}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int start=y*w+x;if(!mask[start]||seen[start])continue;seen[start]=true;q[0]=start;int head=0,tail=1;
            Glyph g=new Glyph();g.l=x;g.r=x+1;g.t=y;g.b=y+1;
            while(head<tail) {int p=q[head++],xx=p%w,yy=p/w;g.l=Math.min(g.l,xx);g.r=Math.max(g.r,xx+1);g.t=Math.min(g.t,yy);g.b=Math.max(g.b,yy+1);
                if(xx>0)tail=push(p-1,mask,seen,q,tail);if(xx+1<w)tail=push(p+1,mask,seen,q,tail);if(yy>0)tail=push(p-w,mask,seen,q,tail);if(yy+1<h)tail=push(p+w,mask,seen,q,tail);
            }
            if(g.h()<height*.012 || g.h()>height*.035 || g.w()>width*.015 || tail<5)continue;
            classify(g,mask,w,digits);
            if(g.token<0 || g.score<.78)return -1;
            found.add(g);
        }
        found.sort(Comparator.comparingInt(g->g.l));
        if(found.size()<1 || found.size()>2)return -1;
        int left=found.get(0).l+x0,right=found.get(found.size()-1).r+x0,top=h,bottom=0;
        for(Glyph g:found){top=Math.min(top,g.t);bottom=Math.max(bottom,g.b);}
        int pad=Math.max(3,(int)(height*.020)),borderL=-1,borderR=-1;
        int yy0=y0+top-(bottom-top)/2,yy1=y0+bottom+(bottom-top)/2;
        for(int d=2;d<=pad;d++){
            if(borderL<0 && border(pixels,width,height,left-d,yy0,yy1))borderL=left-d;
            if(borderR<0 && border(pixels,width,height,right+d,yy0,yy1))borderR=right+d;
        }
        if(borderL<0||borderR<0 || borderR-borderL<height*.032 || borderR-borderL>height*.058)return -1;
        int value=0;for(Glyph g:found)value=value*10+g.token;
        return value<=20?value:-1;
    }
    private static boolean border(int[] pixels,int w,int h,int x,int top,int bottom){
        if(x<0||x>=w||top<0||bottom>=h)return false;int yes=0,n=0;
        for(int y=top;y<bottom;y+=2){int p=pixels[y*w+x],r=(p>>16)&255,g=(p>>8)&255,b=p&255;n++;if(r>100&&g>135&&b>180&&b-r>20)yes++;}
        return n>0&&yes>=n*.60;
    }
    private int findBadge(int[] pixels,int w,int h,float left,float right,float top,float bottom){
        int[] votes=new int[21];
        for(float x=left;x<right;x+=.003f){int value=badge(pixels,w,h,x,top,x+.024f,bottom);if(value>=0)votes[value]++;}
        int start=0;for(int i=10;i<=20;i++)if(votes[i]>0){start=10;break;}
        int best=-1,score=0;boolean tie=false;
        for(int i=start;i<=20;i++)if(votes[i]>score){score=votes[i];best=i;tie=false;}else if(votes[i]>0&&votes[i]==score)tie=true;
        return tie?-1:best;
    }
    private static int[] row(List<Card> cards,boolean hand) {
        if(cards.isEmpty()||cards.size()>20)return new int[0];
        // A card row must share its baseline, except lifted cards in the player's hand.
        int minT=Integer.MAX_VALUE,maxT=0,h=0;
        for(Card c:cards){minT=Math.min(minT,c.t);maxT=Math.max(maxT,c.t);h=Math.max(h,c.b-c.t);}
        if(maxT-minT>h*(hand?1.3:.4))return new int[0];
        int[] count=new int[15],out=new int[cards.size()];
        int lastX=-10000;
        int[] gaps=new int[Math.max(0,cards.size()-1)];for(int i=1;i<cards.size();i++)gaps[i-1]=cards.get(i).l-cards.get(i-1).l;
        int[] sorted=gaps.clone();Arrays.sort(sorted);
        if(hand && sorted.length>=4 && sorted[sorted.length-1]>sorted[sorted.length/2]*1.7)return new int[0];
        for(int i=0;i<out.length;i++) {
            Card c=cards.get(i);out[i]=c.rank;
            if(c.l-lastX<h*.25 || ++count[c.rank]>(c.rank>=13?1:4))return new int[0];
            lastX=c.l;
            if(hand && i>0 && out[i]>out[i-1])return new int[0];
        }
        return out;
    }
}
