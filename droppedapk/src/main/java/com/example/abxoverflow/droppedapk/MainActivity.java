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
        Class<?> hwBinderClass = Class.forName("android.os.HwBinder");
        Object binder = hwBinderClass
                .getMethod("getService", String.class, String.class)
                .invoke(null, "android.hardware.boot@1.0::IBootControl", "default");
        if (binder == null) {
            Toast.makeText(this, "bootctl HIDL not found", Toast.LENGTH_LONG).show();
            return;
        }

        int current = hidlCallInt(binder, 2, "android.hardware.boot@1.0::IBootControl", 0);
        int numSlots = hidlCallInt(binder, 1, "android.hardware.boot@1.0::IBootControl", 0);
        int slotBBootable = hidlCallInt(binder, 6, "android.hardware.boot@1.0::IBootControl", 1);
        int slotBMarked = hidlCallInt(binder, 7, "android.hardware.boot@1.0::IBootControl", 1);

        Log.i(TAG, "current=" + current + " numSlots=" + numSlots
                + " slotB bootable=" + slotBBootable + " marked=" + slotBMarked);
        if (current == 1) {
            Toast.makeText(this, "Already on B slot", Toast.LENGTH_LONG).show();
            return;
        }

        // transact 4 = setActiveBootSlot(1)，返回 CommandResult(int result, String message)
        Object[] result = hidlCallCommandResult(
                binder, 4, "android.hardware.boot@1.0::IBootControl", 1);
        int code = (int) result[0];
        String msg = (String) result[1];
        Log.i(TAG, "setActiveBootSlot(1) -> " + code + " (" + msg + ")");
        Toast.makeText(this, "setActiveBootSlot(1) = " + code
                + " " + msg + "\nReboot to apply", Toast.LENGTH_LONG).show();
    } catch (Throwable e) {
        if (e instanceof InvocationTargetException) {
            e = ((InvocationTargetException) e).getTargetException();
        }
        Log.e(TAG, "switch failed", e);
        Toast.makeText(this, "Switch failed: " + e, Toast.LENGTH_LONG).show();
    }
}

private static int hidlCallInt(Object binder, int code, String iface, int arg)
        throws Exception {
    Class<?> hwParcelClass = Class.forName("android.os.HwParcel");
    Object req = hwParcelClass.getConstructor().newInstance();
    Object rep = hwParcelClass.getConstructor().newInstance();
    try {
        hwParcelClass.getMethod("writeInterfaceToken", String.class).invoke(req, iface);
        hwParcelClass.getMethod("writeInt32", int.class).invoke(req, arg);
        binder.getClass().getMethod("transact", int.class, hwParcelClass, hwParcelClass, int.class)
                .invoke(binder, code, req, rep, 0);
        hwParcelClass.getMethod("verifySuccess").invoke(rep);
        return (int) hwParcelClass.getMethod("readInt32").invoke(rep);
    } finally {
        hwParcelClass.getMethod("release").invoke(rep);
        hwParcelClass.getMethod("releaseTemporaryStorage").invoke(req);
    }
}

private static Object[] hidlCallCommandResult(Object binder, int code, String iface, int arg)
        throws Exception {
    Class<?> hwParcelClass = Class.forName("android.os.HwParcel");
    Object req = hwParcelClass.getConstructor().newInstance();
    Object rep = hwParcelClass.getConstructor().newInstance();
    try {
        hwParcelClass.getMethod("writeInterfaceToken", String.class).invoke(req, iface);
        hwParcelClass.getMethod("writeInt32", int.class).invoke(req, arg);
        binder.getClass().getMethod("transact", int.class, hwParcelClass, hwParcelClass, int.class)
                .invoke(binder, code, req, rep, 0);
        hwParcelClass.getMethod("verifySuccess").invoke(rep);
        int result = (int) hwParcelClass.getMethod("readInt32").invoke(rep);
        String message = (String) hwParcelClass.getMethod("readString").invoke(rep);
        return new Object[]{result, message};
    } finally {
        hwParcelClass.getMethod("release").invoke(rep);
        hwParcelClass.getMethod("releaseTemporaryStorage").invoke(req);
    }
}
}    
