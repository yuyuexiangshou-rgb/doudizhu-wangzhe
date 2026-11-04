package cn.local.tilecounter;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Exactly the visible strip is a touch window; no hidden full-screen container. */
final class CompactOverlay extends View {
    private final Paint p=new Paint(3);
    int[] counts=new int[15];
    String status="等待发牌",center="中：—",right="右：—";
    boolean ready,paused,tiny;
    CompactOverlay(Context context){super(context);setContentDescription("斗地主实时记牌条。拖动移动，轻点打开操作菜单。");}
    @Override protected void onDraw(Canvas c) {
        float w=getWidth(),h=getHeight();
        p.setColor(0xff42454b);c.drawRoundRect(0,0,w,h,h*.12f,h*.12f,p);
        p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);
        if(tiny) {p.setColor(paused?0xffffc46e:ready?0xff78ff62:0xffe1e4e8);p.setTextSize(h*.47f);c.drawText("记",w/2,h*.67f,p);return;}
        float end=w*.63f,pad=h*.06f,cell=(end-pad*2)/15;
        String[] labels={"大","小","2","A","K","Q","J","10","9","8","7","6","5","4","3"};
        for(int i=0;i<15;i++) {
            float x=pad+cell*(i+.5f);int rank=14-i;
            p.setColor(0xffd9dce0);p.setTextSize(Math.min(h*.36f,cell*.88f));c.drawText(labels[i],x,h*.40f,p);
            p.setColor(!ready?0xff92969f:counts[rank]==0?0xff9498a0:counts[rank]<PokerSession.capacity(rank)?0xff79ff64:0xfff5f5f5);
            p.setTextSize(Math.min(h*.43f,cell*.91f));c.drawText(ready?Integer.toString(counts[rank]):"·",x,h*.88f,p);
            p.setColor(0xff64676d);p.setStrokeWidth(1);c.drawLine(pad+cell*i,h*.08f,pad+cell*i,h*.93f,p);
        }
        p.setColor(0xff70737a);c.drawLine(end,h*.08f,end,h*.92f,p);c.drawLine(pad,h*.48f,end,h*.48f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(h*.255f);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));
        float left=end+h*.10f,available=w-left-h*.06f;
        line(c,paused?"已暂停":status,left,h*.28f,available,paused?0xffffc46e:ready?0xff78ff62:0xffffd084);
        line(c,center,left,h*.60f,available,0xffeeeeee);line(c,right,left,h*.91f,available,0xffeeeeee);
    }
    private void line(Canvas c,String text,float x,float y,float width,int color) {
        p.setColor(color);int n=p.breakText(text,true,width,null);c.drawText(n<text.length()?text.substring(0,Math.max(0,n-1))+"…":text,x,y,p);
    }
}
