package com.kaioo.volume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serviço que roda na central multimídia e abre um pequeno servidor HTTP.
 * O celular (na mesma rede Wi-Fi) abre http://IP-DA-CENTRAL:8080 no navegador
 * e controla o volume da central por uma página web.
 */
public class VolumeServerService extends Service {

    public static final int PORT = 8080;
    private static final String TAG = "VolumeRemoto";
    private static final String CHANNEL_ID = "volume_remoto";

    static volatile boolean running = false;

    private ServerSocket serverSocket;
    private ExecutorService pool;
    private AudioManager audio;

    // Nome -> constante do Android para cada "tipo" de volume.
    private static final String[] STREAM_NAMES = {
            "music", "ring", "notification", "alarm", "system", "call", "accessibility"
    };
    private static final int[] STREAM_IDS = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_VOICE_CALL,
            10 // AudioManager.STREAM_ACCESSIBILITY (API 26)
    };

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, VolumeServerService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        startForeground(1, buildNotification());
        pool = Executors.newFixedThreadPool(4);
        new Thread(new Runnable() {
            @Override
            public void run() {
                serve();
            }
        }, "http-accept").start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        if (pool != null) pool.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Volume Remoto", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("Volume Remoto ativo")
                .setContentText("Abra http://" + NetUtil.firstIp() + ":" + PORT + " no celular")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    // ------------------------------------------------------------------ HTTP

    private void serve() {
        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setReuseAddress(true);
            running = true;
            Log.i(TAG, "Servidor ouvindo na porta " + PORT);
            while (!serverSocket.isClosed()) {
                final Socket s = serverSocket.accept();
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        handle(s);
                    }
                });
            }
        } catch (IOException e) {
            Log.w(TAG, "Servidor parou: " + e);
        } finally {
            running = false;
        }
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            String requestLine = readLine(in);
            // Descarta os cabeçalhos.
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) { /* ignora */ }

            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String target = parts[1];
            String path = target;
            Map<String, String> q = new HashMap<>();
            int qi = target.indexOf('?');
            if (qi >= 0) {
                path = target.substring(0, qi);
                for (String kv : target.substring(qi + 1).split("&")) {
                    int e = kv.indexOf('=');
                    if (e > 0) {
                        q.put(URLDecoder.decode(kv.substring(0, e), "UTF-8"),
                                URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
                    } else if (!kv.isEmpty()) {
                        q.put(URLDecoder.decode(kv, "UTF-8"), "");
                    }
                }
            }

            OutputStream out = s.getOutputStream();
            if (path.equals("/") || path.equals("/index.html")) {
                send(out, 200, "text/html; charset=utf-8", readAsset("index.html"));
            } else if (path.equals("/api/status")) {
                send(out, 200, "application/json", statusJson().getBytes("UTF-8"));
            } else if (path.equals("/api/volume")) {
                String err = applyVolume(q);
                if (err != null) {
                    send(out, 400, "application/json",
                            ("{\"error\":\"" + err + "\"}").getBytes("UTF-8"));
                } else {
                    send(out, 200, "application/json", statusJson().getBytes("UTF-8"));
                }
            } else {
                send(out, 404, "text/plain", "nao encontrado".getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro na requisicao: " + e);
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') buf.write(c);
            if (buf.size() > 8192) break;
        }
        if (c == -1 && buf.size() == 0) return null;
        return buf.toString("UTF-8");
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 400 ? "Bad Request" : "Not Found";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    private byte[] readAsset(String name) throws IOException {
        InputStream in = getAssets().open(name);
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return buf.toByteArray();
        } finally {
            in.close();
        }
    }

    // ---------------------------------------------------------------- Volume

    private int streamId(String name) {
        if (name == null || name.isEmpty()) return AudioManager.STREAM_MUSIC;
        for (int i = 0; i < STREAM_NAMES.length; i++) {
            if (STREAM_NAMES[i].equals(name)) return STREAM_IDS[i];
        }
        return -1;
    }

    /** Aplica a ação pedida. Retorna null se deu certo, ou uma mensagem de erro. */
    @SuppressWarnings("deprecation")
    private String applyVolume(Map<String, String> q) {
        int stream = streamId(q.get("stream"));
        if (stream < 0) return "stream invalido";
        String action = q.get("action");
        if (action == null) return "faltou action";
        // mode=key imita o botão físico de volume (a central escolhe qual volume mexer).
        boolean keyMode = "key".equals(q.get("mode"));
        int flags = AudioManager.FLAG_SHOW_UI;
        int steps = 1;
        try {
            if (q.containsKey("steps")) steps = Math.max(1, Math.min(50, Integer.parseInt(q.get("steps"))));
        } catch (NumberFormatException ignored) {
        }

        try {
            switch (action) {
                case "up":
                case "down": {
                    int dir = action.equals("up") ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                    for (int i = 0; i < steps; i++) {
                        if (keyMode) {
                            audio.adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, flags);
                        } else {
                            audio.adjustStreamVolume(stream, dir, flags);
                        }
                    }
                    return null;
                }
                case "set": {
                    int max = audio.getStreamMaxVolume(stream);
                    int v = Integer.parseInt(q.get("value"));
                    v = Math.max(0, Math.min(max, v));
                    audio.setStreamVolume(stream, v, flags);
                    return null;
                }
                case "max":
                    audio.setStreamVolume(stream, audio.getStreamMaxVolume(stream), flags);
                    return null;
                case "mute":
                    if (Build.VERSION.SDK_INT >= 23) {
                        audio.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, flags);
                    } else {
                        audio.setStreamMute(stream, true);
                    }
                    return null;
                default:
                    return "action invalida";
            }
        } catch (SecurityException e) {
            return "sem permissao para alterar esse volume";
        } catch (Exception e) {
            return "erro: " + e.getClass().getSimpleName();
        }
    }

    private String statusJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"device\":\"").append(esc(Build.MANUFACTURER + " " + Build.MODEL))
                .append("\",\"android\":\"").append(esc(Build.VERSION.RELEASE))
                .append("\",\"streams\":{");
        boolean first = true;
        for (int i = 0; i < STREAM_NAMES.length; i++) {
            int id = STREAM_IDS[i];
            int max;
            int cur;
            boolean muted = false;
            try {
                max = audio.getStreamMaxVolume(id);
                cur = audio.getStreamVolume(id);
                if (Build.VERSION.SDK_INT >= 23) muted = audio.isStreamMute(id);
            } catch (Exception e) {
                continue;
            }
            if (max <= 0) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(STREAM_NAMES[i]).append("\":{\"cur\":").append(cur)
                    .append(",\"max\":").append(max)
                    .append(",\"muted\":").append(muted).append('}');
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
