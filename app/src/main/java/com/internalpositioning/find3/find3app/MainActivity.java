package com.internalpositioning.find3.find3app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static com.internalpositioning.find3.find3app.AlarmReceiverLife.context;

public class MainActivity extends AppCompatActivity {

    // logging
    private final String TAG = "MainActivity";


    // background manager
    private PendingIntent recurringLl24 = null;
    private Intent ll24 = null;
    AlarmManager alarms = null;

    Timer timer = null;

    @Override
    protected void onDestroy() {
        Log.d(TAG, "MainActivity onDestroy()");
        if (alarms != null) alarms.cancel(recurringLl24);
        android.app.NotificationManager mNotificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(0);
        Intent scanService = new Intent(this, ScanService.class);
        stopService(scanService);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity.onCreate");

        // check permissions
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.INTERNET, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE}, 1);
        }

        TextView rssi_msg = (TextView) findViewById(R.id.textOutput);
        rssi_msg.setText("not running");


        // check to see if there are preferences
        SharedPreferences sharedPref = MainActivity.this.getPreferences(Context.MODE_PRIVATE);
        EditText familyNameEdit = (EditText) findViewById(R.id.familyName);
        familyNameEdit.setText(sharedPref.getString("familyName", ""));
        EditText deviceNameEdit = (EditText) findViewById(R.id.deviceName);
        deviceNameEdit.setText(sharedPref.getString("deviceName", ""));
        EditText serverAddressEdit = (EditText) findViewById(R.id.serverAddress);
        serverAddressEdit.setText(sharedPref.getString("serverAddress", ((EditText) findViewById(R.id.serverAddress)).getText().toString()));

        class RemindTask extends TimerTask {
            private Integer counter = 0;
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mWebSocketClient != null) {
                            if (mWebSocketClient.isClosed()) {
                                connectWebSocket();
                            }
                        }
                    }
                });
            }
        }

        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    TextView rssi_msg = (TextView) findViewById(R.id.textOutput);
                    String familyName = ((EditText) findViewById(R.id.familyName)).getText().toString();
                    if (familyName.equals("")) {
                        rssi_msg.setText("family name cannot be empty");
                        buttonView.toggle();
                        return;
                    }
                    String serverAddress = ((EditText) findViewById(R.id.serverAddress)).getText().toString();
                    if (serverAddress.equals("")) {
                        rssi_msg.setText("server address cannot be empty");
                        buttonView.toggle();
                        return;
                    }
                    String deviceName = ((EditText) findViewById(R.id.deviceName)).getText().toString();
                    if (deviceName.equals("")) {
                        rssi_msg.setText("device name cannot be empty");
                        buttonView.toggle();
                        return;
                    }

                    String locationName = ((EditText) findViewById(R.id.locationName)).getText().toString();

                    SharedPreferences sharedPref = MainActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("familyName", familyName);
                    editor.putString("deviceName", deviceName);
                    editor.putString("serverAddress", serverAddress);
                    editor.putString("locationName", locationName);
                    editor.commit();

                    rssi_msg.setText("running");
                    // 24/7 alarm
                    ll24 = new Intent(MainActivity.this, AlarmReceiverLife.class);
                    Log.d(TAG, "setting familyName to [" + familyName + "]");
                    ll24.putExtra("familyName", familyName);
                    ll24.putExtra("deviceName", deviceName);
                    ll24.putExtra("serverAddress", serverAddress);
                    ll24.putExtra("locationName", locationName);
                    recurringLl24 = PendingIntent.getBroadcast(MainActivity.this, 0, ll24, PendingIntent.FLAG_CANCEL_CURRENT);
                    alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    alarms.setRepeating(AlarmManager.RTC_WAKEUP, SystemClock.currentThreadTimeMillis(), 60000, recurringLl24);


                    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(MainActivity.this)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("title")
                            .setContentText("message")
                            .setContentIntent(recurringLl24);
                    android.app.NotificationManager notificationManager =
                            (android.app.NotificationManager) MainActivity.this.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());

                    findViewById((R.id.progressBar1)).setVisibility(View.VISIBLE);
                    timer= new Timer();
                    timer.scheduleAtFixedRate(new RemindTask(),1000,1000);
                    connectWebSocket();
                } else {
                    findViewById((R.id.progressBar1)).setVisibility(View.INVISIBLE);
                    TextView rssi_msg = (TextView) findViewById(R.id.textOutput);
                    rssi_msg.setText("not running");
                    Log.d(TAG, "toggle set to false");
                    alarms.cancel(recurringLl24);
                    android.app.NotificationManager mNotificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.cancel(0);
                    timer.cancel();
                }
            }
        });


    }

    WebSocketClient mWebSocketClient = null;

    private void connectWebSocket() {
        URI uri;
        try {
            String serverAddress = ((EditText) findViewById(R.id.serverAddress)).getText().toString();
            String familyName = ((EditText) findViewById(R.id.familyName)).getText().toString();
            String deviceName = ((EditText) findViewById(R.id.deviceName)).getText().toString();
            serverAddress = serverAddress.replace("http","ws");
            uri = new URI(serverAddress + "/ws?family="+familyName+"&device="+deviceName );
            Log.d("Websocket","connect to websocket at " + uri.toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("Websocket", "Opened");
                mWebSocketClient.send("Hello");
            }

            @Override
            public void onMessage(String s) {
                final String message = s;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("Websocket","message: "+ message);
                        JSONObject json = null;
                        JSONObject fingerprint = null;
                        JSONObject sensors = null;
                        JSONObject bluetooth = null;
                        JSONObject wifi = null;
                        String deviceName = "";
                        String locationName = "";
                        String familyName = "";
                        try {
                            json = new JSONObject(message);
                        } catch (Exception e) {
                            Log.d("Websocket","json error: "+ e.toString());
                            return;
                        }
                        try {
                            fingerprint = new JSONObject(json.get("sensors").toString());
                            Log.d("Websocket","fingerprint: " + fingerprint);
                        } catch (Exception e) {
                            Log.d("Websocket","json error: "+ e.toString());
                        }
                        try {
                            sensors = new JSONObject(fingerprint.get("s").toString());
                            deviceName = fingerprint.get("d").toString();
                            familyName = fingerprint.get("f").toString();
                            locationName = fingerprint.get("l").toString();
                            Log.d("Websocket","sensors: " + sensors);
                        } catch (Exception e) {
                            Log.d("Websocket","json error: "+ e.toString());
                        }
                        try {
                            wifi = new JSONObject(sensors.get("wifi").toString());
                            Log.d("Websocket","wifi: " + wifi);
                        } catch (Exception e) {
                            Log.d("Websocket","json error: "+ e.toString());
                        }
                        try {
                            bluetooth = new JSONObject(sensors.get("bluetooth").toString());
                            Log.d("Websocket","bluetooth: " + bluetooth);
                        } catch (Exception e) {
                            Log.d("Websocket","json error: "+ e.toString());
                        }
                        Log.d("Websocket",bluetooth.toString());
                        Integer bluetoothPoints = bluetooth.length();
                        Integer wifiPoints = wifi.length();
                        Long secondsAgo = null;
                        try {
                           secondsAgo= fingerprint.getLong("t");
                        } catch (Exception e) {
                            Log.w("Websocket",e);
                        }
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm:ss");
                        Date resultdate = new Date(secondsAgo);
                        String message = sdf.format(resultdate) + ": " + bluetoothPoints.toString() + " bluetooth and " + wifiPoints.toString() + " wifi points inserted for " + familyName + "/" + deviceName;
                        if (locationName.equals("") == false) {
                            message += " at " + locationName;
                        }
                        TextView rssi_msg = (TextView) findViewById(R.id.textOutput);
                        Log.d("Websocket",message);
                        rssi_msg.setText(message);

                    }
                });
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Closed " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e.getMessage());
            }
        };
        mWebSocketClient.connect();
    }



}
