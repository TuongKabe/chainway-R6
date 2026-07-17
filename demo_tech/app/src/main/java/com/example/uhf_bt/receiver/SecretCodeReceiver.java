package com.example.uhf_bt.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.uhf_bt.MainActivity;

public class SecretCodeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("SecretCodeReceiver", "intent.getData().getHost()=" + intent.getData().getHost());
        // *#*#10010#*#*
        if (intent.getAction().equals("android.provider.Telephony.SECRET_CODE")
                && intent.getData().getHost().equals("145789")) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClass(context, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.putExtra("SecretCodeFlag", true);
            context.startActivity(i);
        }
    }
}