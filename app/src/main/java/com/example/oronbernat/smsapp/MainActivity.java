package com.example.oronbernat.smsapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SMSReceiver.Listener {

    private static final int SMS_PERMISSION_CODE = 50;
    private TextView textView;
    private String addressDestination;
    private EditText eText;
    private Button OKButton;
    private SMSReceiver receiver;
    private Switch submit, alert;
    private SharedPreferences preferences;
    private NotificationManager ntfManager;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate (savedInstanceState);
        setContentView (R.layout.activity_main);

        preferences = getSharedPreferences ("lbltxt", 0);
        addressDestination = preferences.getString ("lbltxt", null);

        eText = findViewById (R.id.Label_id);
        submit = findViewById (R.id.switch2);
        textView = findViewById (R.id.SendToAddress);
        textView.setText (addressDestination);
        OKButton = findViewById (R.id.OKbutton);
        alert = findViewById (R.id.switch3);


    }

    private void requestReadAndSendSmsPermission() {
        ActivityCompat.requestPermissions (this, new String[]{Manifest.permission.READ_SMS}, SMS_PERMISSION_CODE);
    }

    public boolean isSmsPermissionGranted() {
        return ContextCompat.checkSelfPermission (this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,@NonNull String permissions[],@NonNull int[] grantResults) {
        switch (requestCode) {
            case SMS_PERMISSION_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setReceiver ();
                } else {
                    Toast.makeText (this, "You didn't permit read sms", Toast.LENGTH_SHORT).show ();
                }
            }
        }
    }

    private void setReceiver() {
        receiver = new SMSReceiver ();
        registerReceiver (receiver, new IntentFilter (Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
        receiver.setListener (this);
    }

    public void btnChangeLabel(View view) {

        eText.setVisibility (View.VISIBLE);
        OKButton.setVisibility (View.VISIBLE);
    }

    public void btnUpdateLabel(View view) {

        boolean eTextGo = true;
        String check = eText.getText ().toString ();
        for (int i = 0; i < check.length (); i++) {
            if (check.charAt (i) == ' ') {
                Toast.makeText (this, "Can't have spaces", Toast.LENGTH_SHORT).show ();
                eTextGo = false;
                break;
            }
        }
        if (eTextGo) {
            preferences.edit ().putString ("lbltxt",check).apply ();
            eText.setVisibility (View.INVISIBLE);
            OKButton.setVisibility (View.INVISIBLE);

            addressDestination = eText.getText ().toString ();
            textView.setText (addressDestination);
        }
    }

    @Override
    protected void onStart() {
        ntfManager = (NotificationManager) this.getSystemService (Context.NOTIFICATION_SERVICE);
        super.onStart ();
    }

    public void btnSwitch(View view) {
        boolean switchState = submit.isChecked ();
        if (!isSmsPermissionGranted ()) {
            requestReadAndSendSmsPermission ();
        } else {
            Notification ntf = new Notification.Builder (this).setContentText ("Service is Running")
                    .setSmallIcon (R.mipmap.icon_36_)
                    .setContentTitle ("SMS_Listener").setLargeIcon (BitmapFactory.decodeResource (this.getResources (), R.mipmap.icon_48_))
                    .build ();
            if (switchState){
                alert.setVisibility (View.VISIBLE);
                ntfManager.notify (3,ntf);
                setReceiver ();
                startService (new Intent (this,MyService.class));
            }else {
                alert.setVisibility (View.INVISIBLE);
                ntfManager.cancel (3);
                stopService (new Intent (this, MyService.class));
                unregisterReceiver (receiver);
            }
        }



    }


    @SuppressLint("StaticFieldLeak")
    @Override
    public void onTextReceived(final String sender, final String body) {

        final String msg = "/?sender="+sender+"&body="+body;


        new AsyncTask<Void, Void, String> () {

            @Override
            protected void onPostExecute(String s) {
                messageBox (s);
                super.onPostExecute (s);
            }

            @Override
            protected String doInBackground(Void... voids) {

                String result;
                Date currentTime = Calendar.getInstance ().getTime ();
                HttpURLConnection conn = null;
                URL url;

                try {

                    Log.i ("SENDER",sender);
                    Log.i ("MESSAGE", body);
                    Log.i ("TIME", String.valueOf (currentTime));
                    url = new URL (addressDestination + msg);
                    conn = (HttpURLConnection) url.openConnection ();
                    conn.setDoOutput (true);
                    conn.setRequestMethod ("POST");

                    InputStream is = conn.getInputStream ();
                    int actuallyRead;
                    byte[] arr = new byte[512];
                    actuallyRead = is.read (arr);
                    String input = new String (arr,0,actuallyRead);
                    Log.i ("Respond", input);
                    String check = new String (arr,0,15);
                    if (check.equals ("<!DOCTYPE html>"))
                        result = "OK";
                    else
                        result = "ERROR";

                } catch (IOException e) {
                    result ="ERROR: "+e.toString ();
                    Log.i ("ERROR",  e.toString ());
                }finally {
                    if (conn!= null)
                        conn.disconnect ();
                }

                return result;
            }
        }.execute ();

    }

    private void messageBox(String message) {
        if (alert.isChecked ())
            Toast.makeText (this, "Sending: " + message, Toast.LENGTH_SHORT).show ();
    }

    public void onPause() {

        super.onPause ();
    }

    @Override
    protected void onDestroy() {
        ntfManager.cancel (3);
        unregisterReceiver (receiver);
        super.onDestroy ();
    }
}
