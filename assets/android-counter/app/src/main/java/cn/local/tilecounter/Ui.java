package cn.local.tilecounter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int INK=0xff1d342f,GREEN=0xff087f75,PAPER=0xfff3f5ef;
    static int dp(Context c,float n) { return (int)(n*c.getResources().getDisplayMetrics().density+.5f); }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l; }
    static TextView text(Context c,String s,int size,int color) {
        TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(c,5),0,dp(c,5));return t;
    }
    static GradientDrawable background(int color,int stroke) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(20);if(stroke!=0)d.setStroke(1,stroke);return d;
    }
    static Button button(Context c,String s,Runnable action) {
        Button b=new Button(c);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(GREEN);b.setOnClickListener(v->action.run());return b;
    }
    static void toast(Context c,String message) { Toast.makeText(c,message,Toast.LENGTH_LONG).show(); }
    static TextView title(Context c,String s,int size) { TextView t=text(c,s,size,INK);t.setTypeface(null,Typeface.BOLD);return t; }
    static void counts(Context c,LinearLayout parent,Counter counter,java.util.function.IntConsumer add,java.util.function.IntConsumer restore) {
        GridLayout grid=new GridLayout(c);grid.setColumnCount(counter.size()>20?9:5);
        String[] labels=counter.labels();
        for(int i=0;i<labels.length;i++) {
            final int index=i;
            TextView tile=text(c,labels[i]+"\n"+counter.remaining(i),12,counter.remaining(i)==0?0xff9aa6a0:INK);
            tile.setGravity(17);tile.setBackground(background(Color.WHITE,0xffd5e1d9));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(2,2,2,2);tile.setLayoutParams(p);
            tile.setContentDescription(labels[i]+"，未见"+counter.remaining(i)+"张。点击记入，长按加回");
            tile.setOnClickListener(v->add.accept(index));tile.setOnLongClickListener(v->{restore.accept(index);return true;});
            grid.addView(tile);
        }
        parent.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    }
}
