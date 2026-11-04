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
        if (item.getItemId() == R.id.switch_inactive_slot) {
            switchToInactiveSlot();
            return true;
    }
        return super.onOptionsItemSelected(item);
    }

    private void switchToInactiveSlot() {
    try {
        Class<?> hwBinderClass = Class.forName("android.os.HwBinder");
        Object binder = hwBinderClass
                .getMethod("getService", String.class, String.class)
                .invoke(null, "android.hardware.boot@1.0::IBootControl", "default");
        if (binder == null) {
            Toast.makeText(this, "bootctl HIDL not found", Toast.LENGTH_LONG).show();
            return;
        }

        String iface = "android.hardware.boot@1.0::IBootControl";
        int current   = hidlCallInt(binder, 2, iface, 0);   // getCurrentSlot
        int numSlots  = hidlCallInt(binder, 1, iface, 0);   // getNumberSlots
        int target    = 1 - current;                        // 不活跃槽

        if (numSlots <= 1 || target >= numSlots) {
            Toast.makeText(this, "No inactive slot (numSlots=" + numSlots + ")",
                    Toast.LENGTH_LONG).show();
            return;
        }

        int bootable = hidlCallInt(binder, 6, iface, target); // isSlotBootable
        int marked   = hidlCallInt(binder, 7, iface, target); // isSlotMarkedSuccessful

        Log.i(TAG, "current=" + current + " numSlots=" + numSlots
                + " target=" + target + " bootable=" + bootable + " marked=" + marked);

        if (bootable != 1 /* BOOTABLE_OK */) {
            Toast.makeText(this, "Slot " + slotLetter(target) + " not bootable ("
                    + bootable + ")", Toast.LENGTH_LONG).show();
            return;
        }
        if (marked != 1 /* MARKED_SUCCESSFUL */) {
            Toast.makeText(this, "Slot " + slotLetter(target)
                    + " not marked successful (" + marked + ")", Toast.LENGTH_LONG).show();
            return;
        }

        // transact 4 = setActiveBootSlot(target)
        Object[] result = hidlCallCommandResult(binder, 4, iface, target);
        int code = (int) result[0];
        String msg = (String) result[1];
        Log.i(TAG, "setActiveBootSlot(" + target + ") -> " + code + " (" + msg + ")");
        Toast.makeText(this, "setActiveBootSlot(" + slotLetter(target) + ") = " + code
                + " " + msg + "\nReboot to apply", Toast.LENGTH_LONG).show();
    } catch (Throwable e) {
        if (e instanceof InvocationTargetException) {
            e = ((InvocationTargetException) e).getTargetException();
        }
        Log.e(TAG, "switch failed", e);
        Toast.makeText(this, "Switch failed: " + e, Toast.LENGTH_LONG).show();
    }
}

private static String slotLetter(int slot) {
    return String.valueOf((char) ('A' + slot));
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
