package com.adv.poweronoff;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.Objects;

public class AutoPowerBroadcastReceive extends BroadcastReceiver {
    private String TAG = "AutoPowerBroadcastReceive";
    public static final String POWEROFF_TIME = "poweroff_time";
    public static final String POWERON_TIME = "poweron_time";

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        SharedPreferences sharedPreferences = context.getSharedPreferences("private_date", Context.MODE_PRIVATE);
        Log.e(TAG, "ShutdownBroadcastReceive");

        if ((Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED))) {
            Log.d(TAG, "onReceive");
            Intent mServiceIntent = new Intent(context, PowerOnOffService.class);
            mServiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mServiceIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.startService(mServiceIntent);
        }

        boolean autoPowerTimeEnabled = sharedPreferences.getBoolean("auto_power", false);
        if (autoPowerTimeEnabled) {
            Calendar calendar = Calendar.getInstance();

            String poweroff_time = sharedPreferences.getString(POWEROFF_TIME, "23:00");
            String poweron_time = sharedPreferences.getString(POWERON_TIME, "23:00");
            int current_time_day = calendar.get(Calendar.DAY_OF_YEAR);
            String current_time = String.format("%tR", calendar.getTime());
            String[] timeoff = poweroff_time.split(":");
            String[] timeon = poweron_time.split(":");
            long poweroff = Long.valueOf(poweroff_time.replaceAll("[-\\s:]",""));
            long poweron = Long.valueOf(poweron_time.replaceAll("[-\\s:]",""));
            long current = Long.valueOf(current_time.replaceAll("[-\\s:]",""));

            if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {

                if(current > poweroff) {
                    calendar.set(Calendar.DAY_OF_YEAR, current_time_day + 1);
                }

                /* Set Power off time */
                calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeoff[0]));
                calendar.set(Calendar.MINUTE, Integer.parseInt(timeoff[1]));
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                Intent poweroff_intent = new Intent(context, AutoPowerBroadcastReceive.class);
                poweroff_intent.setAction("com.android.settings.action.REQUEST_POWER_OFF");
                PendingIntent pendingIntent_off = PendingIntent.getBroadcast(context, 0,
                        poweroff_intent, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if(am != null) {
                    am.setWindow(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 5, pendingIntent_off);

                    /* Set Power on time */
                    Intent poweron_intent = new Intent(context, AutoPowerBroadcastReceive.class);
                    poweron_intent.setAction("com.android.settings.action.REQUEST_POWER_ON");

                    if ((current > poweron) ||
                            ((current < poweron) && (current > poweroff)) ||
                            ((current < poweron) && (poweron < poweroff))) {
                        calendar.set(Calendar.DAY_OF_YEAR, current_time_day + 1);
                    }

                    calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeon[0]));
                    calendar.set(Calendar.MINUTE, Integer.parseInt(timeon[1]));
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    PendingIntent pendingIntent_on = PendingIntent.getBroadcast(context, 0,
                            poweron_intent, PendingIntent.FLAG_CANCEL_CURRENT);
                    am.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, calendar.getTimeInMillis(), 5, pendingIntent_on);
                }
            } else if (Objects.equals(intent.getAction(), "com.android.settings.action.REQUEST_POWER_OFF")) {
                Log.e(TAG, "Receive Shutdown Broadcast\n");
                String action = "com.android.internal.intent.action.REQUEST_SHUTDOWN";
                if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.N){
                    action = "android.intent.action.ACTION_REQUEST_SHUTDOWN";
                }
                Intent shutdown_intent = new Intent(action);
                shutdown_intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                shutdown_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(shutdown_intent);
            } else if (Objects.equals(intent.getAction(), "com.android.settings.action.REQUEST_POWER_ON")) {
                Log.e(TAG, "Receive PowerOn Broadcast\n");
            }
        }
    }
}
