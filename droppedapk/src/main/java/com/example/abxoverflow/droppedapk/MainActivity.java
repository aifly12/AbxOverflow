package com.example.abxoverflow.droppedapk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;

public class MainActivity extends Activity {

    private static final String TAG = "SlotSwitch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String id = "?";
        try {
            id = new BufferedReader(
                    new InputStreamReader(Runtime.getRuntime().exec("id").getInputStream())
            ).readLine();
        } catch (IOException ignored) {}

        StringBuilder s = new StringBuilder();
        s.append("uid=").append(Process.myUid())
                .append("\npid=").append(Process.myPid())
                .append("\n\n").append(id)
                .append("\n\nUse menu -> Switch to B slot");

        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            for (String serviceName : (String[]) serviceManager
                    .getMethod("listServices").invoke(null)) {
                String serviceStr;
                try {
                    Object serviceObj = serviceManager
                            .getMethod("getService", String.class)
                            .invoke(null, serviceName);
                    serviceStr = serviceObj != null ? serviceObj.toString() : "null";
                } catch (Throwable e) {
                    if (e instanceof InvocationTargetException) {
                        e = ((InvocationTargetException) e).getTargetException();
                    }
                    serviceStr = e.getClass().getName() + ": " + e.getMessage();
                }
                s.append("\n\n").append(serviceName).append(":\n").append(serviceStr);
            }
        } catch (Exception e) {
            s.append("\n\nFailed listing services");
        }

        ((TextView) findViewById(R.id.app_text)).setText(s.toString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.switch_slot_b) {
            switchToSlotB();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void switchToSlotB() {
        try {
            Class<?> bootControlClass = Class.forName("android.hardware.boot.V1_0.IBootControl");
            Object bootControl = bootControlClass
                    .getMethod("getService", String.class)
                    .invoke(null, "default");
            if (bootControl == null) {
                Toast.makeText(this, "bootctl HAL not found", Toast.LENGTH_LONG).show();
                return;
            }

            int current = (int) bootControlClass.getMethod("getCurrentSlot").invoke(bootControl);
            int numSlots = (int) bootControlClass.getMethod("getNumberSlots").invoke(bootControl);
            boolean slotBBootable = (boolean) bootControlClass
                    .getMethod("isSlotBootable", int.class)
                    .invoke(bootControl, 1);
            boolean slotBMarked = (boolean) bootControlClass
                    .getMethod("isSlotMarkedSuccessful", int.class)
                    .invoke(bootControl, 1);

            Log.i(TAG, "current=" + current + " numSlots=" + numSlots
                    + " slotB bootable=" + slotBBootable
                    + " markedSuccessful=" + slotBMarked);

            if (numSlots < 2) {
                Toast.makeText(this, "No B slot", Toast.LENGTH_LONG).show();
                return;
            }
            if (!slotBBootable) {
                Toast.makeText(this, "B slot not bootable, abort", Toast.LENGTH_LONG).show();
                return;
            }
            if (current == 1) {
                Toast.makeText(this, "Already on B slot", Toast.LENGTH_LONG).show();
                return;
            }

            int result = (int) bootControlClass
                    .getMethod("setActiveBootSlot", int.class)
                    .invoke(bootControl, 1);
            Log.i(TAG, "setActiveBootSlot(1) -> " + result);
            Toast.makeText(this, "setActiveBootSlot(1) = " + result
                    + "\nReboot to apply", Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                e = ((InvocationTargetException) e).getTargetException();
            }
            Log.e(TAG, "switch failed", e);
            Toast.makeText(this, "Switch failed: " + e, Toast.LENGTH_LONG).show();
        }
    }
                    }
