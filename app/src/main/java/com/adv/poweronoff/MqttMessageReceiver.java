package com.adv.poweronoff;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.adv.localmqtt.MQTTWrapper;
import com.adv.localmqtt.MqttV3MessageReceiver;
import com.adv.localmqtt.Payload;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;

public class MqttMessageReceiver extends MqttV3MessageReceiver {
    private final String TAG = "MqttMessageReceiver";
    private static final String Action_POWERON = "com.android.settings.action.REQUEST_POWER_ON";
    private static final String Action_POWEROFF = "com.android.settings.action.REQUEST_POWER_OFF";
    private static final String Action_POWERON_CANCLE ="com.android.settings.action.CANCLE_POWER_ON";
    private static final String POWEROFF_TIME = "poweroff_time";
    private static final String POWERON_TIME = "poweron_time";
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;


    MqttMessageReceiver(MQTTWrapper mqttWrapper, String mqttClientId) {
        super(mqttWrapper, mqttClientId);
    }

    @Override
    public void handleMessage(String topic, String message) {
        final int SUCCEED = 0;
        final int UNKNOWN_REASON = 1;
        final int OPERATION_NOT_ALLOWED = 2;
        final int WRONG_FUNCID = 3;
        sharedPreferences = AppContext.getContextObject().getSharedPreferences("private_date", Context.MODE_PRIVATE);
        try {
            Log.d(TAG, " recv:  { " + topic + " [" + message + "]}");
            if (topic.startsWith(REQUEST_TOPIC_STARTER)) { // request from peer
                String[] parms = message.split(";", 6);
                Payload response = null;
                String value = null;
                if (parms.length == 6) {
                    Long messageId = Long.parseLong(parms[0]);
                    String appName = parms[1];
                    String funcId = parms[2];
                    String option = parms[3];
                    String type = parms[4];
                    String param = parms[5];

                    JSONObject jsonObj = new JSONObject();
                    jsonObj.put("pkgname", appName);
                    jsonObj.put("funcid", funcId);
                    JSONObject subJsonObj = new JSONObject();

                    switch (option) {
                        case "1": //get
                            switch (funcId) {
                                case "get_poweronoff_status":
                                    Boolean auto_power = sharedPreferences.getBoolean("auto_power", false);
                                    if (auto_power) {
                                        subJsonObj.put("auto_power", "true");
                                    }else{
                                        subJsonObj.put("auto_power", "false");
                                    }
                                    String poweron_time = sharedPreferences.getString("poweron_time", null);
                                    if (poweron_time != null) {
                                        subJsonObj.put("poweron_time", poweron_time);
                                    }else{
                                        subJsonObj.put("poweron_time", "");
                                    }
                                    String poweroff_time = sharedPreferences.getString("poweroff_time", null);
                                    if (poweroff_time != null) {
                                        subJsonObj.put("poweroff_time", poweroff_time);
                                    }else{
                                        subJsonObj.put("poweroff_time", "");
                                    }
                                    jsonObj.put("result", 0);
                                    jsonObj.put("errcode", SUCCEED);
                                    Log.d(TAG, "#get_poweroff_time#  Get power off time: " + poweroff_time);
                                    break;
                                default:
                                    jsonObj.put("result", 1);
                                    jsonObj.put("errcode", WRONG_FUNCID);
                                    break;
                            }
                            jsonObj.put("data",subJsonObj);
                            break;
                        case "2": //set
                            switch (funcId) {
                                case "set_shutdown":
                                    Log.d(TAG, "#set_shutdown#");
                                    Intent intent_shutdown = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
                                    intent_shutdown.putExtra("android.intent.extra.KEY_CONFIRM", false);
                                    intent_shutdown.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    AppContext.getContextObject().startActivity(intent_shutdown);
                                    jsonObj.put("result", 0);
                                    jsonObj.put("errcode", SUCCEED);
                                    break;
                                case "set_reboot":
                                    Log.d(TAG, "#set_reboot#");
                                    try {
                                        Runtime r = Runtime.getRuntime();
                                        r.exec("/system/bin/reboot");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    jsonObj.put("result", 0);
                                    jsonObj.put("errcode", SUCCEED);
                                    break;
                                case "set_poweronoff_time":
                                    String[] strs = param.split(",");
                                    Log.d(TAG, "#set_poweronoff_time#  poweroff time: " + strs[0]+ " poweron time: " + strs[1]);
                                    if (sharedPreferences.getBoolean("auto_power", false)) {
                                        if(!setPowerOff(AppContext.getContextObject(),Integer.parseInt(getPrefStrHour(strs[0])), Integer.parseInt(getPrefStrMinute(strs[0])))){
                                            jsonObj.put("result", 1);
                                            jsonObj.put("errcode", UNKNOWN_REASON);
                                            break;
                                        }
                                        if(!setPowerOn(AppContext.getContextObject(),Integer.parseInt(getPrefStrHour(strs[1])), Integer.parseInt(getPrefStrMinute(strs[1])), true)){
                                            jsonObj.put("result", 1);
                                            jsonObj.put("errcode", UNKNOWN_REASON);
                                            break;
                                        }
                                        editor = sharedPreferences.edit();
                                        editor.putString("poweroff_time", strs[0]);
                                        editor.putString("poweron_time", strs[1]);
                                        editor.apply();

                                        jsonObj.put("result", 0);
                                        jsonObj.put("errcode", SUCCEED);
                                    }else{
                                        jsonObj.put("result", 1);
                                        jsonObj.put("errcode", OPERATION_NOT_ALLOWED);
                                    }
                                    break;
                                case "close_poweronoff":
                                    Log.d(TAG, "#close_poweronoff# Close poweronoff function");
                                    String poweron_time = sharedPreferences.getString("poweron_time", "23:00");
                                    if(!setPowerOn(AppContext.getContextObject(),Integer.parseInt(getPrefStrHour(poweron_time)), Integer.parseInt(getPrefStrMinute(poweron_time)), false)){
                                        jsonObj.put("result", 1);
                                        jsonObj.put("errcode", UNKNOWN_REASON);
                                        break;
                                    }
                                    editor = sharedPreferences.edit();
                                    editor.putBoolean("auto_power", false);
                                    editor.apply();

                                    jsonObj.put("result", 0);
                                    jsonObj.put("errcode", SUCCEED);
                                    break;
                                case "open_poweronoff":
                                    Log.d(TAG, "#open_poweronoff# Open poweronoff function");

                                    if(!setPowerOff(AppContext.getContextObject(),Integer.parseInt(getPrefStrHour(sharedPreferences.getString("poweroff_time", "23:00"))),
                                            Integer.parseInt(getPrefStrMinute(sharedPreferences.getString("poweroff_time", "23:00"))))){
                                        jsonObj.put("result", 1);
                                        jsonObj.put("errcode", UNKNOWN_REASON);
                                        break;
                                    }
                                    if(!setPowerOn(AppContext.getContextObject(),Integer.parseInt(getPrefStrHour(sharedPreferences.getString("poweron_time", "23:00"))),
                                            Integer.parseInt(getPrefStrMinute(sharedPreferences.getString("poweron_time", "23:00"))), true)){
                                        jsonObj.put("result", 1);
                                        jsonObj.put("errcode", UNKNOWN_REASON);
                                        break;
                                    }
                                    editor = sharedPreferences.edit();
                                    editor.putBoolean("auto_power", true);
                                    editor.apply();

                                    jsonObj.put("result", 0);
                                    jsonObj.put("errcode", SUCCEED);
                                    break;
                                default:
                                    jsonObj.put("result", 1);
                                    jsonObj.put("errcode", WRONG_FUNCID);
                                    break;
                            }
                            jsonObj.put("data","");
                            break;
                        default:
                            break;
                    }

                    response = new Payload(messageId, appName, funcId, Integer.parseInt(option), 2, jsonObj.toString());

                    String pubTopic = genRespTopic();
                    String pubContent = response.genContent();
                    getMQTTWrapper().publish(pubTopic, pubContent);
                } else {
                    Log.e(TAG, " receive an invalid package");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String getPrefStrHour(String prefName)
    {
        String[] time = prefName.split(":");
        return time[0];
    };

    private String getPrefStrMinute(String prefName)
    {
        String[] time = prefName.split(":");
        return time[1];
    };


    private boolean setPowerOff(Context context,int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        int current_time_day = calendar.get(Calendar.DAY_OF_YEAR);
        String current_time = String.format("%tR", calendar.getTime());
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        String poweroff_time = String.format("%tR", calendar.getTime());

        editor = sharedPreferences.edit();
        editor.putString(POWEROFF_TIME, poweroff_time);
        editor.apply();

        String poweron_time = sharedPreferences.getString(POWERON_TIME, "23:00");
        String[] timeoff = poweroff_time.split(":");
        String[] timeon = poweron_time.split(":");
        long poweroff = Long.valueOf(poweroff_time.replaceAll("[-\\s:]",""));
        long poweron = Long.valueOf(poweron_time.replaceAll("[-\\s:]",""));
        long current = Long.valueOf(current_time.replaceAll("[-\\s:]",""));

        //if the triggerTime < now, it will be triggered next day.
        if(current > poweroff) {
            calendar.set(Calendar.DAY_OF_YEAR, current_time_day + 1);
        }

        Intent intent_off = new Intent(context, AutoPowerBroadcastReceive.class);
        intent_off.setAction(Action_POWEROFF);

        PendingIntent pendingIntent_off = PendingIntent.getBroadcast(context, 0,
                intent_off, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if(am != null) {
            am.setWindow(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 5, pendingIntent_off);
            //it link to AlarmManagerService, the set(type) can't be changed

            if ((current > poweron) ||
                    ((current < poweron) && (current > poweroff)) ||
                    ((current < poweron) && (poweron < poweroff))) {
                calendar.set(Calendar.DAY_OF_YEAR, current_time_day + 1);
            }

            calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeon[0]));
            calendar.set(Calendar.MINUTE, Integer.parseInt(timeon[1]));

            Intent intent_on = new Intent(context, AutoPowerBroadcastReceive.class);
            intent_on.setAction(Action_POWERON);
            PendingIntent pendingIntent_on = PendingIntent.getBroadcast(context, 0,
                    intent_on, PendingIntent.FLAG_CANCEL_CURRENT);
            am.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, calendar.getTimeInMillis(), 5, pendingIntent_on);
            return true;
        }else{
            return false;
        }
    };

    private boolean setPowerOn(Context context,int hourOfDay, int minute, boolean enable) {
        Intent intent = new Intent(Action_POWERON);

        if(!enable) {
            intent = new Intent(Action_POWERON_CANCLE);
        }

        Calendar calendar = Calendar.getInstance();
        String current_time = String.format("%tR", calendar.getTime());
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        String poweron_time = String.format("%tR", calendar.getTime());

        sharedPreferences.getString(POWERON_TIME, "23:00");
        editor = sharedPreferences.edit();
        editor.putString(POWERON_TIME, poweron_time);
        editor.apply();

        String poweroff_time = sharedPreferences.getString(POWEROFF_TIME, "23:00");
        String[] timeoff = poweroff_time.split(":");
        String[] timeon = poweron_time.split(":");
        long poweroff = Long.valueOf(poweroff_time.replaceAll("[-\\s:]",""));
        long poweron = Long.valueOf(poweron_time.replaceAll("[-\\s:]",""));
        long current = Long.valueOf(current_time.replaceAll("[-\\s:]",""));
        //if the triggerTime < now, it will be triggered next day.
        if((current > poweron) ||
                ((current < poweron) && (current > poweroff)) ||
                ((current < poweron) && (poweron < poweroff))) {
            calendar.set(Calendar.DAY_OF_YEAR, calendar.get(Calendar.DAY_OF_YEAR) + 1);
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if(am != null) {
            am.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, calendar.getTimeInMillis(), 5, pendingIntent);
            //it link to AlarmManagerService, the set(type) can't be changed
            return true;
        }else{
            return false;
        }
    };
}
