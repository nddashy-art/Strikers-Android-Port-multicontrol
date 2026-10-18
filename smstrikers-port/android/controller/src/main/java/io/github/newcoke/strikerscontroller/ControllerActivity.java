package io.github.newcoke.strikerscontroller;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ControllerActivity extends Activity {
    private static final int PORT=47653;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private EditText hostField,pinField;
    private TextView status;
    private Client client;
    private String host="",pin="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showSetup();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private void showSetup(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28),dp(16),dp(28),dp(16));root.setBackgroundColor(Color.rgb(17,24,39));
        TextView title=text("Strikers Wi-Fi Controller",24);title.setTypeface(null,1);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
        root.addView(text("Conecta ambos teléfonos a la misma Wi-Fi o conecta este teléfono al punto de acceso del móvil que ejecuta Strikers.",14),
                new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(0);
        hostField=new EditText(this);hostField.setSingleLine(true);hostField.setHint("IP del teléfono con Strikers");
        hostField.setText(host);hostField.setTextColor(Color.WHITE);hostField.setHintTextColor(Color.LTGRAY);
        hostField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        fields.addView(hostField,new LinearLayout.LayoutParams(0,dp(54),2f));
        pinField=new EditText(this);pinField.setSingleLine(true);pinField.setHint("Código de 6 dígitos");
        pinField.setText(pin);pinField.setTextColor(Color.WHITE);pinField.setHintTextColor(Color.LTGRAY);
        pinField.setInputType(InputType.TYPE_CLASS_NUMBER);
        fields.addView(pinField,new LinearLayout.LayoutParams(0,dp(54),1f));
        root.addView(fields,new LinearLayout.LayoutParams(-1,dp(58)));
        Button connect=new Button(this);connect.setText("Conectar y jugar");connect.setOnClickListener(v->connect());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(54));cp.topMargin=dp(12);root.addView(connect,cp);
        status=text("En Strikers, abre Ajustes → Mandos físicos y copia la IP y el código.",14);
        status.setTextColor(Color.LTGRAY);root.addView(status,new LinearLayout.LayoutParams(-1,dp(45)));
        setContentView(root);
    }
    private void connect(){
        host=hostField.getText().toString().trim();pin=pinField.getText().toString().trim();
        if(host.isEmpty()||!pin.matches("\\d{6}")){status.setText("Escribe la IP y el código de 6 dígitos de Strikers.");return;}
        if(client!=null)client.close();
        client=new Client(host,pin,ok->ui.post(()->{if(status!=null)status.setText(ok?"Conectado a Strikers. ¡A jugar!":"Buscando Strikers… revisa IP, código y Wi-Fi.");}));
        showPad();client.start();
    }
    private void showPad(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(17,24,39));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(text("Conectando con "+host+":"+PORT,13),new LinearLayout.LayoutParams(0,dp(36),1f));
        Button change=new Button(this);change.setText("Cambiar conexión");
        change.setOnClickListener(v->{if(client!=null)client.close();client=null;showSetup();});
        bar.addView(change,new LinearLayout.LayoutParams(-2,dp(38)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(40)));
        root.addView(new Pad(this,(b,l,r,x,y)->{if(client!=null)client.setState(b,l,r,x,y,0,0);}),
                new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);
    }
    @Override public void onBackPressed(){if(client!=null)client.close();client=null;showSetup();}
    @Override protected void onDestroy(){if(client!=null)client.close();ui.removeCallbacksAndMessages(null);super.onDestroy();}

    private interface ConnectionListener{void changed(boolean value);}
    private interface PadListener{void state(int buttons,int lt,int rt,int lx,int ly);}
    private static final class Client{
        final String host,pin;final ConnectionListener listener;volatile boolean running,connected;
        volatile int buttons,lt,rt,lx,ly,cx,cy;volatile DatagramSocket socket;
        Client(String h,String p,ConnectionListener l){host=h;pin=p;listener=l;}
        void setState(int b,int l,int r,int x,int y,int rx,int ry){buttons=b;lt=l;rt=r;lx=x;ly=y;cx=rx;cy=ry;}
        void start(){running=true;Thread t=new Thread(this::loop,"strikers-controller-udp");t.setDaemon(true);t.start();}
        void close(){running=false;DatagramSocket s=socket;if(s!=null)s.close();setState(0,0,0,0,0,0,0);}
        void loop(){
            boolean shown=false;long helloAt=0;
            try(DatagramSocket s=new DatagramSocket()){
                socket=s;s.setSoTimeout(1);InetAddress target=InetAddress.getByName(host);byte[] buf=new byte[128];
                while(running){
                    long now=System.currentTimeMillis();
                    if(!connected&&now-helloAt>400){send(s,target,"STRIKERS_WIFI|HELLO|"+pin);helloAt=now;}
                    send(s,target,String.format(Locale.US,"STRIKERS_WIFI|STATE|%s|%d|%d|%d|%d|%d|%d|%d",pin,buttons,lt,rt,lx,ly,cx,cy));
                    try{
                        DatagramPacket p=new DatagramPacket(buf,buf.length);s.receive(p);
                        String[] v=new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8).split("\\|");
                        if(v.length==3&&"STRIKERS_WIFI".equals(v[0])&&"OK".equals(v[1])&&pin.equals(v[2]))connected=true;
                    }catch(SocketTimeoutException ignored){}
                    if(shown!=connected){shown=connected;listener.changed(connected);}
                    Thread.sleep(30);
                }
            }catch(Exception e){if(running)listener.changed(false);}
            finally{socket=null;connected=false;listener.changed(false);}
        }
        static void send(DatagramSocket s,InetAddress a,String msg){
            try{byte[] d=msg.getBytes(StandardCharsets.UTF_8);s.send(new DatagramPacket(d,d.length,a,PORT));}catch(Exception ignored){}
        }
    }

    private static final class Pad extends View{
        static final int A=1,B=2,X=4,Y=8,Z=16,START=32,UP=64,DOWN=128,LEFT=256,RIGHT=512,BACK=8192;
        static final int STICK=-100,LT=-101,RT=-102;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final PadListener listener;final Map<Integer,Integer> fingers=new HashMap<>();
        float sx,sy,sr;int buttons,lt,rt,lx,ly;
        Pad(Activity a,PadListener l){super(a);listener=l;}
        float dist(float x,float y,float a,float b){return (float)Math.hypot(x-a,y-b);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();c.drawColor(Color.rgb(17,24,39));
            sx=w*.18f;sy=h*.60f;sr=Math.min(w*.145f,h*.30f);circle(c,sx,sy,sr,0xFF273449);
            circle(c,sx+lx/32767f*sr*.65f,sy+ly/32767f*sr*.65f,sr*.35f,0xFF1689FF);
            float d=Math.min(w*.045f,h*.115f),dx=w*.43f,dy=h*.58f;
            key(c,dx,dy-d,d,"↑",UP);key(c,dx,dy+d,d,"↓",DOWN);key(c,dx-d,dy,d,"←",LEFT);key(c,dx+d,dy,d,"→",RIGHT);
            float r=Math.min(w*.052f,h*.145f),fx=w*.80f,fy=h*.58f;
            key(c,fx,fy-r,r,"Y",Y);key(c,fx-r,fy,r,"X",X);key(c,fx+r,fy,r,"B",B);key(c,fx,fy+r,r,"A",A);
            float sh=Math.min(w*.06f,h*.10f);
            key(c,w*.12f,h*.13f,sh,"L",LT);key(c,w*.25f,h*.13f,sh,"Z",Z);
            key(c,w*.50f,h*.13f,sh,"START",START);key(c,w*.75f,h*.13f,sh,"BACK",BACK);key(c,w*.88f,h*.13f,sh,"R",RT);
        }
        void circle(Canvas c,float x,float y,float r,int color){p.setColor(color);c.drawCircle(x,y,r,p);}
        void key(Canvas c,float x,float y,float r,String label,int id){
            boolean on=id==LT?lt>0:id==RT?rt>0:id>0&&(buttons&id)!=0;
            circle(c,x,y,r,on?0xFF1689FF:0xFF344256);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(11,r*.56f));c.drawText(label,x,y+p.getTextSize()*.34f,p);
        }
        int hit(float x,float y){
            float w=getWidth(),h=getHeight();sx=w*.18f;sy=h*.60f;sr=Math.min(w*.145f,h*.30f);
            if(dist(x,y,sx,sy)<=sr)return STICK;
            float d=Math.min(w*.045f,h*.115f),dx=w*.43f,dy=h*.58f;
            if(dist(x,y,dx,dy-d)<=d)return UP;if(dist(x,y,dx,dy+d)<=d)return DOWN;
            if(dist(x,y,dx-d,dy)<=d)return LEFT;if(dist(x,y,dx+d,dy)<=d)return RIGHT;
            float r=Math.min(w*.052f,h*.145f),fx=w*.80f,fy=h*.58f;
            if(dist(x,y,fx,fy-r)<=r)return Y;if(dist(x,y,fx-r,fy)<=r)return X;
            if(dist(x,y,fx+r,fy)<=r)return B;if(dist(x,y,fx,fy+r)<=r)return A;
            float sh=Math.min(w*.06f,h*.10f);
            if(dist(x,y,w*.12f,h*.13f)<=sh)return LT;if(dist(x,y,w*.25f,h*.13f)<=sh)return Z;
            if(dist(x,y,w*.50f,h*.13f)<=sh)return START;if(dist(x,y,w*.75f,h*.13f)<=sh)return BACK;
            if(dist(x,y,w*.88f,h*.13f)<=sh)return RT;return 0;
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int a=e.getActionMasked(),i=e.getActionIndex();
            if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
                int id=e.getPointerId(i),ctl=hit(e.getX(i),e.getY(i));if(ctl!=0)fingers.put(id,ctl);update();return true;
            }
            if(a==MotionEvent.ACTION_MOVE){
                for(Map.Entry<Integer,Integer> f:fingers.entrySet())if(f.getValue()==STICK){
                    int ix=e.findPointerIndex(f.getKey());if(ix>=0){
                        float dx=(e.getX(ix)-sx)/sr,dy=(e.getY(ix)-sy)/sr,m=(float)Math.hypot(dx,dy);
                        if(m>1){dx/=m;dy/=m;}lx=Math.round(Math.max(-1,Math.min(1,dx))*32767);ly=Math.round(Math.max(-1,Math.min(1,dy))*32767);
                    }break;
                }update();return true;
            }
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_CANCEL){
                if(a==MotionEvent.ACTION_CANCEL)fingers.clear();else fingers.remove(e.getPointerId(i));
                if(!fingers.containsValue(STICK)){lx=0;ly=0;}update();return true;
            }return true;
        }
        void update(){
            buttons=0;lt=0;rt=0;for(int ctl:fingers.values()){
                if(ctl==LT){lt=255;continue;}if(ctl==RT){rt=255;continue;}if(ctl>0)buttons|=ctl;
            }listener.state(buttons,lt,rt,lx,ly);invalidate();
        }
    }
}
