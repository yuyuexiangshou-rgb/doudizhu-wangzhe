package cn.local.tilecounter;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class CounterService extends Service {
    static volatile boolean running;
    static CounterService instance;
    volatile String summary="等待游戏画面";
    private final Handler main=new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler worker;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    private final ArrayDeque<int[]> buffers=new ArrayDeque<>();
    private static final class Sample{int[] pixels;int w,h;long time;Sample(int[] p,int w,int h,long t){pixels=p;this.w=w;this.h=h;time=t;}}
    private volatile long captured,processed,overflow;
    private volatile long lastVisionMs,queueDelayMs;
    private final PokerSession session=new PokerSession();
    private volatile PokerVision vision;
    private WindowManager wm;
    private CompactOverlay strip;
    private WindowManager.LayoutParams position;
    private android.content.SharedPreferences prefs;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private int width,height,density;
    private volatile boolean destroying,paused;
    private boolean locked,tiny;
    private long lastImage,lastUi;
    private Bitmap captureBitmap;
    private int[] capturePixels;
    private java.nio.ByteBuffer paddedBuffer;
    private final List<AlertDialog> dialogs=new ArrayList<>();
    private Snapshot snapshot=new Snapshot();
    private static final class Snapshot {
        int[] counts=new int[15];boolean ready,ended;String status="等待发牌",center="中：—",right="右：—",history="暂无自动出牌记录";
    }
    @Override public void onCreate() {
        super.onCreate();instance=this;wm=(WindowManager)getSystemService(WINDOW_SERVICE);prefs=getSharedPreferences("display",0);
        thread=new HandlerThread("poker-vision",android.os.Process.THREAD_PRIORITY_BACKGROUND);thread.start();worker=new Handler(thread.getLooper());
        captureThread=new HandlerThread("poker-capture");captureThread.start();captureHandler=new Handler(captureThread.getLooper());
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || "stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if("unlock".equals(intent.getAction())){if(running){locked=false;applyLayout();}else stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        try {
            if(!Settings.canDrawOverlays(this))throw new IllegalStateException("请先允许悬浮窗");
            Notification n=notification();
            if(Build.VERSION.SDK_INT>=29)startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(7,n);
            Intent grant=intent.getParcelableExtra("grant");if(grant==null)throw new IllegalStateException("需要重新授权屏幕采集");
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(intent.getIntExtra("code",0),grant);
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){if(!destroying)stopSelf();}
                @Override public void onCapturedContentResize(int w,int h){worker.post(()->resize(w,h));}
            },main);
            strip=new CompactOverlay(this);
            position=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
            position.gravity=Gravity.TOP|Gravity.LEFT;
            wm.addView(strip,position);applyLayout();installDrag();running=true;
            worker.post(()->{
                try {
                    vision=new PokerVision(getAssets().open("poker-glyphs.txt"),getAssets().open("poker-digits.txt"));
                    DisplayMetrics m=metrics();density=m.densityDpi;int[] s=scaled(m.widthPixels,m.heightPixels);width=s[0];height=s[1];
                    reader=createReader(width,height);
                    display=projection.createVirtualDisplay("FloatCounter",width,height,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
                }catch(Exception e){fail("采集启动失败："+e.getMessage());}
            });
        }catch(Exception e){Ui.toast(this,"启动失败："+e.getMessage());stopSelf();}
        return START_NOT_STICKY;
    }
    private Notification notification() {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("capture","斗地主实时记牌",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CounterService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent unlock=PendingIntent.getService(this,2,new Intent(this,CounterService.class).setAction("unlock"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"capture").setSmallIcon(R.drawable.ic_tile).setContentTitle("浮记 · 斗地主实时记牌")
            .setContentText("点击打开设置；穿透模式可从此解锁").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"解锁悬浮条",unlock).build()).addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
    }
    private DisplayMetrics metrics(){DisplayMetrics m=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(m);return m;}
    private static int[] scaled(int w,int h){float s=Math.min(1,1600f/Math.max(w,h));return new int[]{Math.max(1,Math.round(w*s)),Math.max(1,Math.round(h*s))};}
    private ImageReader createReader(int w,int h){ImageReader r=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,4);r.setOnImageAvailableListener(this::image,captureHandler);return r;}
    private void resize(int w,int h) {
        if(destroying||display==null||w<=0||h<=0)return;int[] size=scaled(w,h);if(width==size[0]&&height==size[1])return;
        try {ImageReader old=reader;width=size[0];height=size[1];reader=createReader(width,height);display.resize(width,height,density);display.setSurface(reader.getSurface());captureHandler.post(old::close);main.post(this::applyLayout);}
        catch(Exception e){fail("画面尺寸变化，请重新启动采集");}
    }
    private void image(ImageReader source) {
        if(destroying)return;
        try(Image image=source.acquireNextImage()) {
            long now=SystemClock.elapsedRealtime();
            if(image==null||paused||now-lastImage<60||vision==null)return;lastImage=now;
            synchronized(samples){if(samples.size()>=8){overflow++;return;}}
            Image.Plane plane=image.getPlanes()[0];int padded=plane.getRowStride()/plane.getPixelStride();
            if(captureBitmap==null||captureBitmap.getWidth()!=padded||captureBitmap.getHeight()!=image.getHeight()){
                if(captureBitmap!=null)captureBitmap.recycle();captureBitmap=Bitmap.createBitmap(padded,image.getHeight(),Bitmap.Config.ARGB_8888);
            }
            {
                java.nio.ByteBuffer data=plane.getBuffer();int bytes=padded*image.getHeight()*4;
                if(data.remaining()<bytes){if(paddedBuffer==null||paddedBuffer.capacity()!=bytes)paddedBuffer=java.nio.ByteBuffer.allocate(bytes);paddedBuffer.clear();paddedBuffer.put(data);paddedBuffer.rewind();data=paddedBuffer;}
                captureBitmap.copyPixelsFromBuffer(data);
                int w=image.getWidth(),h=image.getHeight();int[] pixels;
                synchronized(samples){pixels=buffers.pollFirst();}
                if(pixels==null||pixels.length!=w*h)pixels=new int[w*h];captureBitmap.getPixels(pixels,0,w,0,0,w,h);
                synchronized(samples){samples.addLast(new Sample(pixels,w,h,now));}captured++;worker.post(this::processSample);
            }
        }catch(Exception e){summary="识别帧异常："+e.getClass().getSimpleName();main.post(()->{if(strip!=null){strip.status="画面异常 · 重启采集";strip.invalidate();}});}
    }
    private void processSample(){
        if(destroying)return;Sample s;synchronized(samples){s=samples.pollFirst();}if(s==null)return;
        try{if(!paused){long begin=SystemClock.elapsedRealtime();queueDelayMs=begin-s.time;PokerVision.Frame frame=vision.read(s.pixels,s.w,s.h);session.update(frame,s.time);lastVisionMs=SystemClock.elapsedRealtime()-begin;processed++;long t=SystemClock.elapsedRealtime();if(t-lastUi>=100){publish();lastUi=t;}}}
        catch(Exception e){summary="识别异常："+e.getClass().getSimpleName();}
        finally{synchronized(samples){if(buffers.size()<9)buffers.addLast(s.pixels);}}
    }
    private void publish() {
        Snapshot next=new Snapshot();next.ready=session.ready();next.ended=session.finished();next.status=session.ready()?session.status.equals("自动记牌中")?"实时 · 余"+session.total():session.status:session.status;
        for(int i=0;i<15;i++)next.counts[i]=session.remaining(i);
        String[] latest={"—","—"};StringBuilder all=new StringBuilder();
        for(PokerSession.Play play:session.history){String line=(play.seat==0?"中":"右")+"："+PokerSession.describe(play.cards);latest[play.seat]=PokerSession.describe(play.cards);all.append(line).append('\n');}
        next.center="中 "+(session.badge(0)<0?"?":session.badge(0))+"："+latest[0];next.right="右 "+(session.badge(1)<0?"?":session.badge(1))+"："+latest[1];
        next.history=all.length()==0?"暂无自动出牌记录":all.toString();
        int pending;synchronized(samples){pending=samples.size();}
        summary=next.status+" · 手牌"+session.ownCount()+"张 / 采样"+captured+" / 处理"+processed+" / 队列"+pending+" / 溢出"+overflow+" / 单帧"+lastVisionMs+"ms / 排队"+queueDelayMs+"ms";
        main.post(()->{if(destroying||strip==null)return;snapshot=next;strip.counts=next.counts;strip.ready=next.ready;strip.status=next.status;strip.center=next.center;strip.right=next.right;strip.paused=paused;strip.invalidate();});
    }
    private void fail(String text){main.post(()->{if(!destroying){Ui.toast(this,text);stopSelf();}});}
    void applyLayout() {
        if(strip==null||destroying)return;DisplayMetrics m=metrics();boolean landscape=m.widthPixels>m.heightPixels;
        float fraction=prefs.getFloat("width",.36f);
        int barH=Math.max(Ui.dp(this,24),Math.min(Ui.dp(this,40),(int)(m.heightPixels*.085f)));
        position.width=tiny?Ui.dp(this,30):landscape?(int)(m.widthPixels*fraction):m.widthPixels-Ui.dp(this,24);
        position.height=tiny?Ui.dp(this,26):barH;
        position.x=Math.max(0,Math.min(m.widthPixels-position.width,(int)(prefs.getFloat("x",.025f)*m.widthPixels)));
        position.y=Math.max(0,Math.min(m.heightPixels-position.height,(int)(prefs.getFloat("y",.115f)*m.heightPixels)));
        position.alpha=Math.max(.40f,Math.min(.80f,prefs.getFloat("alpha",.78f)));
        position.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|(locked?WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE:0);
        strip.tiny=tiny;strip.invalidate();wm.updateViewLayout(strip,position);
    }
    private void installDrag() {
        strip.setOnTouchListener(new View.OnTouchListener(){
            int sx,sy;float x,y;boolean dragged;
            public boolean onTouch(View v,MotionEvent e){
                if(e.getAction()==MotionEvent.ACTION_DOWN){sx=position.x;sy=position.y;x=e.getRawX();y=e.getRawY();dragged=false;return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getRawX()-x,dy=e.getRawY()-y;if(Math.abs(dx)+Math.abs(dy)>Ui.dp(CounterService.this,5))dragged=true;
                    if(dragged){DisplayMetrics m=metrics();position.x=Math.max(0,Math.min(m.widthPixels-position.width,sx+(int)dx));position.y=Math.max(0,Math.min(m.heightPixels-position.height,sy+(int)dy));wm.updateViewLayout(strip,position);}return true;}
                if(e.getAction()==MotionEvent.ACTION_UP){DisplayMetrics m=metrics();prefs.edit().putFloat("x",(float)position.x/m.widthPixels).putFloat("y",(float)position.y/m.heightPixels).apply();if(!dragged){if(snapshot.ended)restart();else menu();}return true;}return true;
            }
        });
    }
    private void menu() {
        if(tiny){tiny=false;applyLayout();return;}
        String[] entries={paused?"继续自动识牌":"暂停自动识牌","缩成小点","开启点击穿透（从通知或App解锁）","大小与透明度","重新开始记牌（下一局）","撤销最近一次对手出牌","查看出牌记录","手动修正一张牌","停止并关闭悬浮条"};
        show(new AlertDialog.Builder(this).setTitle("浮记").setItems(entries,(d,index)->{
            switch(index){
                case 0:paused=!paused;strip.paused=paused;strip.invalidate();break;
                case 1:tiny=true;applyLayout();break;
                case 2:locked=true;applyLayout();Ui.toast(this,"已穿透；到浮记或通知点解锁");break;
                case 3:displaySettings();break;
                case 4:restart();break;
                case 5:worker.post(()->{session.undo();publish();});break;
                case 6:show(new AlertDialog.Builder(this).setTitle("本局出牌记录").setMessage(snapshot.history).setPositiveButton("关闭",null).create());break;
                case 7:manual();break;
                case 8:stopSelf();break;
            }
        }).create());
    }
    private void manual(){
        show(new AlertDialog.Builder(this).setTitle("修正哪张牌？").setItems(PokerSession.LABELS,(d,rank)->show(new AlertDialog.Builder(this).setTitle(PokerSession.LABELS[rank]).setItems(new String[]{"少一张：扣除","多一张：加回"},(dd,choice)->worker.post(()->{try{session.adjust(rank,choice==0?1:-1);publish();}catch(IllegalArgumentException e){main.post(()->Ui.toast(this,e.getMessage()));}})).create())).create());
    }
    private void displaySettings(){
        LinearLayout form=Ui.column(this);form.setPadding(24,0,24,0);
        form.addView(Ui.text(this,"宽度（横屏的28%–52%）",14,Ui.INK));SeekBar size=new SeekBar(this);size.setMax(24);size.setProgress(Math.round(prefs.getFloat("width",.36f)*100)-28);form.addView(size);
        form.addView(Ui.text(this,"不透明度（40%–80%）",14,Ui.INK));SeekBar alpha=new SeekBar(this);alpha.setMax(40);alpha.setProgress(Math.round(prefs.getFloat("alpha",.78f)*100)-40);form.addView(alpha);
        show(new AlertDialog.Builder(this).setTitle("双行悬浮条").setView(form).setPositiveButton("应用",(d,w)->{prefs.edit().putFloat("width",(28+size.getProgress())/100f).putFloat("alpha",(40+alpha.getProgress())/100f).apply();applyLayout();}).setNegativeButton("取消",null).create());
    }
    private void show(AlertDialog dialog){dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);dialogs.add(dialog);dialog.setOnDismissListener(d->dialogs.remove(dialog));dialog.show();}
    void unlock(){locked=false;applyLayout();}
    void restart(){paused=false;synchronized(samples){samples.clear();}worker.post(()->{session.reset();publish();});}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);applyLayout();}
    @Override public void onDestroy(){
        destroying=true;running=false;instance=null;
        for(AlertDialog d:new ArrayList<>(dialogs))d.dismiss();if(strip!=null&&strip.isAttachedToWindow())wm.removeView(strip);
        worker.post(()->{if(display!=null)display.release();if(projection!=null)projection.stop();thread.quitSafely();});
        captureHandler.post(()->{if(reader!=null)reader.close();if(captureBitmap!=null)captureBitmap.recycle();synchronized(samples){samples.clear();buffers.clear();}paddedBuffer=null;captureThread.quitSafely();});
        stopForeground(true);super.onDestroy();
    }
}
