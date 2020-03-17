package com.adv.poweronoff;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import com.adv.localmqtt.MQTTWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;

public class PowerOnOffService extends Service {
    private static String TAG = "PowerOnOffService";
    private MQTTWrapper mqttWrapper = null;
    private String mqttClientId = "com.adv.poweronoff";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        //String command = "ps |grep mosquitto";
                        String command = "ps";
                        if (commandIsRuning(command)) {
                            break;
                        }
                        Log.d(TAG, "ps command ...");
                        Thread.sleep(3000);
                    } catch (InterruptedException | IOException e) {
                        Log.d(TAG, "Exception Error ...");
                        e.printStackTrace();
                        return;
                    }
                }
                Log.d(TAG, "connectMqttBroker ...");

                connectMqttBroker();
            }
        }).start();

    }

    private void connectMqttBroker() {
        Log.d(TAG, "--> onCreate");
        mqttWrapper = new MQTTWrapper(mqttClientId);
        boolean ret = mqttWrapper.connect(new MqttMessageReceiver(mqttWrapper,mqttClientId));
        if (ret) {
            Log.d(TAG, "--> mqtt connected");
        } else {
            Log.e(TAG, "--> mqtt no connect, return");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mqttWrapper.destroy();
    }


    private boolean commandIsRuning(String command) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        Process proc = runtime.exec(command);
        InputStream inputstream = proc.getInputStream();
        InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
        BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
        String line = "";
        StringBuilder sb = new StringBuilder(line);
        while ((line = bufferedreader.readLine()) != null) {
            sb.append(line);
            sb.append('\n');
        }
        try {
            if (proc.waitFor() != 0) {
                Log.e(TAG,"Command exit value = " + proc.exitValue());
                return false;
            }
            //Log.d(TAG,"StringBuilder: " + sb);
            if(sb.toString().contains("mosquitto")){
                return true;
            }
            return false;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

}
