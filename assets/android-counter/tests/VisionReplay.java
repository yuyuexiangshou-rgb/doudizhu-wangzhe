package cn.local.tilecounter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class VisionReplay {
    static PokerVision.Frame read(PokerVision vision,BufferedImage source,int width) {
        int height=source.getHeight()*width/source.getWidth();
        BufferedImage scaled=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=scaled.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(source,0,0,width,height,null);g.dispose();
        return vision.read(scaled.getRGB(0,0,width,height,null,0,width),width,height);
    }
    public static void main(String[] args) throws Exception {
        PokerVision vision=new PokerVision(new FileInputStream("app/src/main/assets/poker-glyphs.txt"),new FileInputStream("app/src/main/assets/poker-digits.txt"));
        for(String file:args) {
            BufferedImage source=ImageIO.read(new File(file));
            for(int width:new int[]{1920,1600,1280}) {
                long start=System.nanoTime();PokerVision.Frame frame=read(vision,source,width);
                System.out.println(new File(file).getName()+" / "+width+" / hand="+Arrays.toString(frame.hand)+" / center="+Arrays.toString(frame.center)+" / right="+Arrays.toString(frame.right)+" / self="+Arrays.toString(frame.self)+" / badges="+frame.centerRemaining+","+frame.rightRemaining+" / ms="+(System.nanoTime()-start)/1000000);
                String name=new File(file).getName();
                if(name.startsWith("ee4b")||name.startsWith("131cb")||name.startsWith("f752")) {
                    int[] expected={13,11,11,11,10,9,9,9,9,7,7,7,6,6,6,0,0};
                    if(!Arrays.equals(frame.hand,expected))throw new AssertionError("New 17-card feedback regression");
                    if(name.startsWith("ee4b")&&!Arrays.equals(frame.bottom,new int[]{1,1,1}))throw new AssertionError("Three bottom fours must be recognized: "+Arrays.toString(frame.bottom));
                    if(name.startsWith("131cb")&&!frame.bidding)throw new AssertionError("Face-down bottom cards mark bidding stage");
                    PokerSession startup=new PokerSession();for(int k=0;k<3;k++)startup.update(frame,k*60);
                    if(!startup.ready()||startup.total()!=37)throw new AssertionError("Actual 17-card startup must initialize");
                }
                if(name.startsWith("c04d") && width>=1600 && (frame.centerRemaining!=17||frame.rightRemaining!=13))throw new AssertionError("Actual moved badges must read 17 and 13");
                if(name.startsWith("2a921") && (!Arrays.equals(frame.self,new int[]{2,2,2,2})||frame.center.length>0||frame.right.length>0))throw new AssertionError("Own final bomb must never be assigned to opponent");
                if(name.startsWith("fadd")) {
                    if(!Arrays.equals(frame.hand,SessionCheck.HAND))throw new AssertionError("Screenshot hand mismatch at "+width);
                    if(frame.center.length!=0||frame.right.length!=0)throw new AssertionError("Opponent false positive");
                    if(frame.centerRemaining!=17||frame.rightRemaining!=17)throw new AssertionError("Badge mismatch");
                }
                if(name.startsWith("343")) {if(frame.hand.length!=0||frame.center.length!=0||frame.right.length!=0)throw new AssertionError("Intro false positive");}
                if(name.startsWith("1eba")) {
                    PokerSession session=new PokerSession();for(int k=0;k<4;k++)session.update(frame,k*250);if(session.ready())throw new AssertionError("Occluded screenshot established baseline");
                }
                if(name.startsWith("synthetic-")) {
                    int rank=Integer.parseInt(name.substring(10,name.indexOf('.')));
                    if(!Arrays.equals(frame.center,new int[]{rank})) {for(PokerVision.Glyph glyph:frame.glyphs)if(glyph.t<source.getHeight()*(double)width/source.getWidth()*.56)System.out.println(glyph);throw new AssertionError("Synthetic public rank mismatch: "+name+" at "+width+" -> "+Arrays.toString(frame.center));}
                }
                if(name.startsWith("sequence-")){
                    int expected=Integer.parseInt(name.substring(9,name.indexOf('.')));
                    if(frame.centerRemaining!=expected||!Arrays.equals(frame.center,new int[]{0}))throw new AssertionError("Public card / badge sequence mismatch: "+name+" at "+width);
                }
                if(name.startsWith("right-"))if(frame.rightRemaining!=16||!Arrays.equals(frame.right,new int[]{0}))throw new AssertionError("Right player mismatch at "+width);
            }
        }
        File directory=args.length==0?new File("build/feedback"):new File(args[0]).getParentFile();
        File baseline=new File(directory,"fadd8f31c30010a22fabe917c10b9650.jpg");
        if(baseline.isFile() && new File(directory,"sequence-15.jpg").isFile()) {
            PokerSession session=new PokerSession();long clock=0;
            for(String file:new String[]{baseline.getName(),"sequence-16.jpg","sequence-15.jpg","right-16.jpg"}) {
                PokerVision.Frame f=read(vision,ImageIO.read(new File(directory,file)),1600);
                if(file.equals("sequence-15.jpg")) {PokerVision.Frame blank=read(vision,ImageIO.read(baseline),1600);blank.centerRemaining=16;session.update(blank,clock+=250);session.update(blank,clock+=250);}
                for(int i=0;i<4;i++)session.update(f,clock+=250);
            }
            if(session.total()!=31 || session.history.size()!=3)throw new AssertionError("End-to-end vision/accounting sequence failed: "+session.total());
            System.out.println("PASS: end-to-end synthetic play sequence, total 34 -> 33 -> 32 -> 31");
        }
        System.out.println("PASS: screenshot replay (real feedback and explicitly synthetic fixtures)");
    }
}
