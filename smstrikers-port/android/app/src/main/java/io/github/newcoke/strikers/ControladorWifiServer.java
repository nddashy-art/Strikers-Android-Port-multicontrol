package io.github.newcoke.strikers;

import android.util.Log;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Locale;

/** PIN-protected UDP receiver for the second-phone controller on the local network. */
final class ControladorWifiServer {
    static final int PUERTO = 47653;
    private static final String TAG = "StrikersRemote";
    private static final long TIMEOUT_MS = 650L;
    private final String pin = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1_000_000));
    private final String ip = buscarIpLocal();
    private volatile boolean ejecutando, conectado;
    private volatile long ultimoPaquete;
    private volatile DatagramSocket socket;
    private Thread hilo;

    void start() {
        if (ejecutando) return;
        ejecutando = true;
        hilo = new Thread(this::bucle, "strikers-wifi-controller");
        hilo.setDaemon(true);
        hilo.start();
    }
    String[] informacion() { return new String[] {ip, pin, conectado ? "Conectado" : "Esperando"}; }

    private void bucle() {
        try (DatagramSocket s = new DatagramSocket(null)) {
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(PUERTO));
            s.setSoTimeout(120);
            socket = s;
            byte[] buffer = new byte[512];
            while (ejecutando) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    s.receive(packet);
                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    procesar(s, packet, msg);
                } catch (SocketTimeoutException ignored) {
                } catch (Exception e) {
                    if (ejecutando) Log.w(TAG, "Paquete ignorado", e);
                }
                if (conectado && System.currentTimeMillis() - ultimoPaquete > TIMEOUT_MS) {
                    conectado = false;
                    enviarNativo(false,0,0,0,0,0,0,0);
                }
            }
        } catch (Exception e) {
            if (ejecutando) Log.e(TAG, "No se pudo abrir UDP " + PUERTO, e);
        } finally {
            socket = null;
            conectado = false;
            enviarNativo(false,0,0,0,0,0,0,0);
        }
    }
    private void procesar(DatagramSocket s, DatagramPacket p, String msg) {
        String[] v = msg.trim().split("\\|");
        if (v.length < 3 || !"STRIKERS_WIFI".equals(v[0]) || !pin.equals(v[2])) return;
        if ("HELLO".equals(v[1]) && v.length == 3) {
            responder(s,p,"STRIKERS_WIFI|OK|"+pin); return;
        }
        if (!"STATE".equals(v[1]) || v.length != 10) return;
        try {
            int buttons=limitar(Integer.parseInt(v[3]),0,16383);
            int lt=limitar(Integer.parseInt(v[4]),0,255), rt=limitar(Integer.parseInt(v[5]),0,255);
            int lx=limitar(Integer.parseInt(v[6]),-32767,32767), ly=limitar(Integer.parseInt(v[7]),-32767,32767);
            int cx=limitar(Integer.parseInt(v[8]),-32767,32767), cy=limitar(Integer.parseInt(v[9]),-32767,32767);
            conectado=true; ultimoPaquete=System.currentTimeMillis();
            enviarNativo(true,buttons,lt,rt,lx,ly,cx,cy);
        } catch (NumberFormatException ignored) {}
    }
    private static int limitar(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    private static void responder(DatagramSocket s,DatagramPacket p,String msg){
        try{byte[] d=msg.getBytes(StandardCharsets.UTF_8);s.send(new DatagramPacket(d,d.length,p.getAddress(),p.getPort()));}
        catch(Exception e){Log.d(TAG,"No se pudo responder",e);}
    }
    private static String buscarIpLocal(){
        String backup="No disponible";
        try{
            Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
            while(en!=null&&en.hasMoreElements()){
                NetworkInterface ni=en.nextElement();
                if(!ni.isUp()||ni.isLoopback())continue;
                String name=ni.getName().toLowerCase(Locale.ROOT);
                Enumeration<InetAddress> as=ni.getInetAddresses();
                while(as.hasMoreElements()){
                    InetAddress a=as.nextElement();
                    if(!(a instanceof Inet4Address)||a.isLoopbackAddress()||a.isLinkLocalAddress())continue;
                    String value=a.getHostAddress();
                    if(name.startsWith("wlan")||name.startsWith("wifi")||name.startsWith("ap")||name.contains("wireless"))return value;
                    if("No disponible".equals(backup))backup=value;
                }
            }
        }catch(Exception e){Log.w(TAG,"No se pudo consultar IP local",e);}
        return backup;
    }
    private static void enviarNativo(boolean active,int b,int lt,int rt,int lx,int ly,int cx,int cy){
        try{StrikersActivity.nativeRemoteState(active,b,lt,rt,lx,ly,cx,cy);}
        catch(UnsatisfiedLinkError e){Log.w(TAG,"Biblioteca nativa no lista",e);}
    }
    void close(){
        ejecutando=false;DatagramSocket s=socket;if(s!=null)s.close();if(hilo!=null)hilo.interrupt();
    }
}