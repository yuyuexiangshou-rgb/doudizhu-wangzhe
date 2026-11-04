package cn.local.tilecounter;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final Handler main=new Handler();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private TextView status,result;
    private ImageView image;
    private final Runnable tick=new Runnable(){public void run(){if(status!=null)status.setText(CounterService.running&&CounterService.instance!=null?CounterService.instance.summary:"未启动 · 请从发牌前开启");main.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    @Override public void onResume(){super.onResume();main.post(tick);}
    @Override public void onPause(){main.removeCallbacks(tick);super.onPause();}
    @Override public void onDestroy(){executor.shutdown();super.onDestroy();}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.PAPER);LinearLayout page=Ui.column(this);int pad=Ui.dp(this,22);page.setPadding(pad,pad,pad,pad);scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v,i)->{page.setPadding(pad,pad+i.getSystemWindowInsetTop(),pad,pad+i.getSystemWindowInsetBottom());return i;});
        page.addView(Ui.title(this,"浮记 · 斗地主",28));
        page.addView(Ui.text(this,"双行悬浮条，自动识牌、自动记牌。",17,Ui.INK));
        status=Ui.text(this,"未启动",14,Ui.GREEN);page.addView(status);
        page.addView(Ui.text(this,"每人先17张，3张底牌归地主后地主20张。叫地主阶段暂不记录出牌，底牌归属确定后自动更新；本局内持续记牌，下一局由你手动重新开始。",14,Ui.INK));
        page.addView(Ui.button(this,"允许显示悬浮条",()->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))));
        page.addView(Ui.button(this,"开始实时记牌",()->{
            if(CounterService.running){moveTaskToBack(true);return;}
            if(!Settings.canDrawOverlays(this)){Ui.toast(this,"请先允许显示悬浮条");return;}
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("notificationAsked",false)) {
                getPreferences(0).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},30);return;
            }
            capture();
        }));
        page.addView(Ui.button(this,"解锁悬浮条 / 恢复拖动",()->{if(CounterService.instance!=null)CounterService.instance.unlock();}));
        page.addView(Ui.button(this,"重新开始记牌（下一局）",()->{if(CounterService.instance!=null)CounterService.instance.restart();}));
        page.addView(Ui.button(this,"停止并关闭悬浮条",()->stopService(new Intent(this,CounterService.class))));
        page.addView(Ui.title(this,"悬浮条怎么用",18));
        page.addView(Ui.text(this,"拖动细条移动位置；轻点打开操作菜单。可缩成小点、调整宽度与透明度、开启点击穿透。悬浮条默认约占横屏宽度36%、高度8.5%，不会展开成打牌面板。\n\n左侧上排：大/小王、2至3；下排：对手剩余数量。右侧：识别状态与两位对手最近出牌。还没识别起手牌时显示“·”，不会用54张假装已识别。",14,Ui.INK));
        page.addView(Ui.title(this,"用截图检查识别",18));
        page.addView(Ui.text(this,"导入截图只作检查，不会影响本局记牌。看不到完整手牌时请先收起其他遮挡；动态出牌仍需真机对局验证。",14,Ui.INK));
        page.addView(Ui.button(this,"导入截图，立即识别",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),20)));
        result=Ui.text(this,"尚未导入截图",14,Ui.GREEN);page.addView(result);
        image=new ImageView(this);image.setAdjustViewBounds(true);page.addView(image,new LinearLayout.LayoutParams(-1,-2));
        page.addView(Ui.text(this,"画面仅在本机处理，无联网权限。中途加入无法补回之前没看到的出牌；重新发牌时可点“新局”。识别冲突和漏帧会提示，允许撤销和手动修正。",13,Ui.INK));
        setContentView(scroll);
    }
    private void capture(){startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),10);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==30)capture();}
    @Override public void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);if(code!=RESULT_OK||data==null)return;
        if(request==10){startForegroundService(new Intent(this,CounterService.class).putExtra("code",code).putExtra("grant",data));moveTaskToBack(true);}
        else if(request==20&&data.getData()!=null){Uri uri=data.getData();result.setText("正在本机识别…");executor.execute(()->inspect(uri));}
    }
    private void inspect(Uri uri){
        try {
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
            try(java.io.InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}
            o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>2400)o.inSampleSize*=2;o.inJustDecodeBounds=false;
            Bitmap source;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){source=BitmapFactory.decodeStream(in,null,o);}
            if(source==null)throw new java.io.IOException("无法读取图片");
            float scale=Math.min(1,1600f/Math.max(source.getWidth(),source.getHeight()));Bitmap b=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),Math.max(1,Math.round(source.getHeight()*scale)),true);if(b!=source)source.recycle();
            int w=b.getWidth(),h=b.getHeight();int[] pixels=new int[w*h];b.getPixels(pixels,0,w,0,0,w,h);
            PokerVision vision=new PokerVision(getAssets().open("poker-glyphs.txt"),getAssets().open("poker-digits.txt"));PokerVision.Frame frame=vision.read(pixels,w,h);
            Bitmap annotated=b.copy(Bitmap.Config.ARGB_8888,true);b.recycle();Canvas c=new Canvas(annotated);Paint p=new Paint(3);p.setStyle(Paint.Style.STROKE);p.setColor(0xff3cff71);p.setStrokeWidth(2);
            for(PokerVision.Card card:frame.cards)c.drawRect(card.l-2,card.t-2,card.r+2,card.b+2,p);
            String text="手牌 "+frame.hand.length+"张："+PokerSession.describe(frame.hand)+"\n中："+PokerSession.describe(frame.center)+" / 右："+PokerSession.describe(frame.right)+"\n"+(frame.hand.length==17||frame.hand.length==20?"可建立完整起手牌基线":"未见完整起手牌：暂不自动建立基线");
            main.post(()->{if(isDestroyed()){annotated.recycle();return;}result.setText(text);image.setImageBitmap(annotated);});
        }catch(Exception e){main.post(()->{if(!isDestroyed())result.setText("读取失败："+e.getMessage());});}
    }
}
