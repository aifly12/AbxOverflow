package com.example.abxoverflow.droppedapk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String id = "?";
        try {
            id = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("id").getInputStream())).readLine();
        } catch (IOException e) {}

        StringBuilder s = new StringBuilder();
        s
                .append(
                        "Note: Installation of this app involved registering new signature trusted for sharedUserId=android.uid.system," +
                                " if you uninstall usual way it will stay in system" +
                                " and you will be able to reinstall this app despite mismatched signature." +
                                " To fully uninstall use \"Uninstall\" button within this app" +
                                "\n\nuid=").append(Process.myUid())
                .append("\npid=").append(Process.myPid())
                .append("\n\n").append(id)
                .append("\n\nBelow is list of system services, as this app loads into system_server it can directly tamper with local ones (those that are non-null and non-BinderProxy)");

        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            for (String serviceName : ((String[]) serviceManager.getMethod("listServices").invoke(null))) {
                String serviceStr;
                try {
                    Object serviceObj = serviceManager
                            .getMethod("getService", String.class)
                            .invoke(null, serviceName);
                    if (serviceObj != null) {
                        serviceStr = serviceObj.toString();
                    } else {
                        serviceStr = "null (getService() was disallowed)";
                    }
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
        if (item.getItemId() == R.id.uninstall) {
            try {
                // Delete <pastSigs> by directly editing PackageManagerService state within system_server
                // ServiceManager.getService("package").this$0.mSettings.mSharedUsers.get("android.uid.system").getSigningDetails().mPastSigningCertificates = null
                Object packManImplService = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package");
                Field packManImplThisField = packManImplService.getClass().getDeclaredField("this$0");
                packManImplThisField.setAccessible(true);
                Object packManService = packManImplThisField.get(packManImplService);
                Field settingsField = packManService.getClass().getDeclaredField("mSettings");
                settingsField.setAccessible(true);
                Object settings = settingsField.get(packManService);
                Field sharedUsersField = settings.getClass().getDeclaredField("mSharedUsers");
                sharedUsersField.setAccessible(true);
                Object sharedUser = ((Map) sharedUsersField.get(settings)).get("android.uid.system");
                Object signingDetails = sharedUser.getClass().getMethod("getSigningDetails").invoke(sharedUser);
                Field pastSigningCertificatesField = signingDetails.getClass().getDeclaredField("mPastSigningCertificates");
                pastSigningCertificatesField.setAccessible(true);
                pastSigningCertificatesField.set(signingDetails, null);

                if (item.getItemId() == R.id.switch_slot_b) {
                    try {
                        Class<?> bootControlClass = Class.forName("android.hardware.boot.V1_0.IBootControl");
                        Object bootControl = bootControlClass.getMethod("getService", String.class)
                                .invoke(null, "default");
                        if (bootControl == null) {
                            Toast.makeText(this, "bootctl HAL not found", Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        int current = (int) bootControlClass.getMethod("getCurrentSlot").invoke(bootControl);
                        int numSlots = (int) bootControlClass.getMethod("getNumberSlots").invoke(bootControl);
                        Log.i("SlotSwitch", "current=" + current + " numSlots=" + numSlots);

                        if (numSlots < 2) {
                            Toast.makeText(this, "No B slot", Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        int target = (current == 0) ? 1 : 0;  // 切到另一个槽位
                        // 或者硬编码 target = 1 切到 B
                        int result = (int) bootControlClass.getMethod("setActiveBootSlot", int.class)
                                .invoke(bootControl, target);
                        Log.i("SlotSwitch", "setActiveBootSlot(" + target + ") -> " + result);
                        Toast.makeText(this, "setActiveBootSlot(" + target + ") = " + result, Toast.LENGTH_LONG).show();
                    } catch (Throwable e) {
                        if (e instanceof InvocationTargetException) {
                            e = ((InvocationTargetException) e).getTargetException();
                        }
                        Log.e("SlotSwitch", "failed", e);
                        Toast.makeText(this, "Slot switch failed: " + e, Toast.LENGTH_LONG).show();
                    }
                    return true;
                }

                // Uninstall this app (also triggers write of fixed packages.xml)
                getPackageManager().getPackageInstaller().uninstall(getPackageName(), null);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Uninstall failed", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
