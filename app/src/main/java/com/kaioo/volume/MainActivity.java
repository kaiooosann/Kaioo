package com.kaioo.volume;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Tela simples na central: liga o servidor e mostra o endereço para abrir no celular. */
public class MainActivity extends Activity {

    private TextView info;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VolumeServerService.start(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#101418"));

        TextView title = new TextView(this);
        title.setText("Volume Remoto");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        root.addView(title);

        info = new TextView(this);
        info.setTextColor(Color.parseColor("#C8D2DC"));
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        info.setPadding(0, dp(16), 0, dp(16));
        root.addView(info);

        Button refresh = new Button(this);
        refresh.setText("Atualizar");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VolumeServerService.start(MainActivity.this);
                render();
            }
        });
        root.addView(refresh);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Dá um tempinho para o servidor subir antes de mostrar o status.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                render();
            }
        }, 600);
    }

    private void render() {
        StringBuilder sb = new StringBuilder();
        sb.append(VolumeServerService.running ? "Servidor LIGADO\n\n" : "Servidor iniciando...\n\n");
        sb.append("No celular (conectado no mesmo Wi-Fi da central), abra no navegador:\n\n");
        List<String> ips = NetUtil.allIps();
        if (ips.isEmpty()) {
            sb.append("(nenhuma rede encontrada: ligue o Wi-Fi ou o hotspot da central)\n");
        }
        for (String s : ips) {
            String ip = s.substring(s.indexOf(": ") + 2);
            sb.append("  http://").append(ip).append(":").append(VolumeServerService.PORT)
                    .append("    (").append(s.substring(0, s.indexOf(':'))).append(")\n");
        }
        sb.append("\nPode fechar esta tela: o servidor continua rodando em segundo plano.");
        info.setText(sb.toString());
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
