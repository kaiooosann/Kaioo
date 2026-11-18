package com.kaioo.volume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Liga o servidor automaticamente quando a central inicia. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        VolumeServerService.start(context);
    }
}
