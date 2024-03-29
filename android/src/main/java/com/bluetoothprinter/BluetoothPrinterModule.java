package com.bluetoothprinter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ReactModule(name = BluetoothPrinterModule.NAME)
public class BluetoothPrinterModule extends ReactContextBaseJavaModule implements PermissionListener, ActivityEventListener, BluetoothServiceStateObserver {
  public static final String NAME = "BluetoothPrinter";
  private static final String TAG = "RNBluetoothPrinter";

  private final ReactApplicationContext reactContext;

  public static final String EVENT_DEVICE_ALREADY_PAIRED = "EVENT_DEVICE_ALREADY_PAIRED";
  public static final String EVENT_DEVICE_FOUND = "EVENT_DEVICE_FOUND";
  public static final String EVENT_DEVICE_DISCOVER_DONE = "EVENT_DEVICE_DISCOVER_DONE";
  public static final String EVENT_CONNECTION_LOST = "EVENT_CONNECTION_LOST";
  public static final String EVENT_UNABLE_CONNECT = "EVENT_UNABLE_CONNECT";
  public static final String EVENT_CONNECTED = "EVENT_CONNECTED";
  public static final String EVENT_BLUETOOTH_NOT_SUPPORT = "EVENT_BLUETOOTH_NOT_SUPPORT";

  private static final int REQUEST_CONNECT_DEVICE = 1;
  private static final int REQUEST_ENABLE_BT = 2;
  private static final int REQUEST_PERMISSION = 3;
  private static final int REQUEST_SETTINGS = 4;

  private static final Map<String, Promise> promiseMap = Collections.synchronizedMap(new HashMap<String, Promise>());
  private static final String PROMISE_ENABLE_BT = "ENABLE_BT";
  private static final String PROMISE_SCAN = "SCAN";
  private static final String PROMISE_CONNECT = "CONNECT";
  private static final String PROMISE_PERMISSION = "PERMISSION";
  private static final String PROMISE_SETTINGS = "SETTINGS";

  private String mConnectedDeviceName = null;
  private String mConnectedDeviceAddress = null;
  private BluetoothAdapter mBluetoothAdapter = null;
  private BluetoothService mService = null;

  private HashMap<String, BluetoothDevice> pairedDevices = new HashMap<String, BluetoothDevice>();
  private HashMap<String, BluetoothDevice> foundedDevices = new HashMap<String, BluetoothDevice>();

  public BluetoothPrinterModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.mService = new BluetoothService(reactContext);

    this.reactContext.addActivityEventListener(this);
    this.mService.addStateObserver(this);

    // Register for broadcasts when a device is discovered
    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    this.reactContext.registerReceiver(discoverReceiver, filter);
  }

  @Override
  public Map<String, Object> getConstants() {
    Map<String, Object> constants = new HashMap<>();
    constants.put(EVENT_DEVICE_ALREADY_PAIRED, EVENT_DEVICE_ALREADY_PAIRED);
    constants.put(EVENT_DEVICE_DISCOVER_DONE, EVENT_DEVICE_DISCOVER_DONE);
    constants.put(EVENT_DEVICE_FOUND, EVENT_DEVICE_FOUND);
    constants.put(EVENT_CONNECTION_LOST, EVENT_CONNECTION_LOST);
    constants.put(EVENT_UNABLE_CONNECT, EVENT_UNABLE_CONNECT);
    constants.put(EVENT_CONNECTED, EVENT_CONNECTED);
    constants.put(EVENT_BLUETOOTH_NOT_SUPPORT, EVENT_BLUETOOTH_NOT_SUPPORT);

    constants.put(BluetoothService.DEVICE_NAME, BluetoothService.DEVICE_NAME);
    constants.put(BluetoothService.DEVICE_ADDRESS, BluetoothService.DEVICE_ADDRESS);
    return constants;
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  private BluetoothAdapter getBluetoothAdapter() {
    if (mBluetoothAdapter == null) {
      // Get local Bluetooth adapter
      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // If the adapter is null, then Bluetooth is not supported
    if (mBluetoothAdapter == null) {
      sendReactNativeEvent(EVENT_BLUETOOTH_NOT_SUPPORT, Arguments.createMap());
    }

    return mBluetoothAdapter;
  }

  private boolean getPermissionState() {
    // Android 12+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      int permissionBluetoothConnectChecked = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.BLUETOOTH_CONNECT);
      int permissionBluetoothScanChecked = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.BLUETOOTH_SCAN);

      return permissionBluetoothConnectChecked == PackageManager.PERMISSION_GRANTED && permissionBluetoothScanChecked == PackageManager.PERMISSION_GRANTED;
    } else {
      int permissionChecked = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.ACCESS_FINE_LOCATION);

      return permissionChecked == PackageManager.PERMISSION_GRANTED;
    }
  }

  @ReactMethod
  @SuppressLint("MissingPermission")
  public void requestPermission(final Promise promise) {
    Log.i(TAG, "Requesting permission to access bluetooth device");

    // check if app has permission to interact with bluetooth device
    boolean hasPermission = getPermissionState();

    // if has permission, stop script
    if (hasPermission) {
      promise.resolve(createRequestPermissionResponse(true, false).toString());
      return;
    }

    PermissionAwareActivity activity = (PermissionAwareActivity) reactContext.getCurrentActivity();

    if (activity == null) {
      Log.e(TAG, "Activity not found");
      promise.reject("ACTIVITY_NOT_FOUND");
      return;
    }

    // Android 12+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      try {
        // request permission to location
        activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQUEST_PERMISSION, this);

        // put on promises
        promiseMap.put(PROMISE_PERMISSION, promise);
      } catch (Exception e) {
        promise.resolve(createRequestPermissionResponse(false, true).toString());
      }
    } else {
      try {
        // request permission to location
        activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION, this);

        // put on promises
        promiseMap.put(PROMISE_PERMISSION, promise);
      } catch (Exception e) {
        promise.resolve(createRequestPermissionResponse(false, true).toString());
      }
    }

  }

  @ReactMethod
  public void openSettings(final Promise promise) {
    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);

    intent.setData(Uri.fromParts("package", getReactApplicationContext().getPackageName(), null));

    // put on promises
    promiseMap.put(PROMISE_SETTINGS, promise);

    // request to enable bluetooth
    this.reactContext.startActivityForResult(intent, REQUEST_SETTINGS, Bundle.EMPTY);
  }

  @ReactMethod
  @SuppressLint("MissingPermission")
  public void enableBluetooth(final Promise promise) {
    if (!getPermissionState()) {
      promise.reject(BluetoothService.PERMISSION_NOT_GRANTED, new Exception("Permission required to interact with bluetooth not granted"));
      return;
    }

    BluetoothAdapter adapter = this.getBluetoothAdapter();
    if (adapter == null) {
      Log.e(TAG, "Bluetooth not supported on this device");
      promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT, new Exception("Bluetooth not supported on this device"));
      return;
    }

    if (!adapter.isEnabled()) {
      Log.i(TAG, "Requesting enabling bluetooth on device");

      // If Bluetooth is not on, request that it be enabled.
      // setupChat() will then be called during onActivityResult
      Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      promiseMap.put(PROMISE_ENABLE_BT, promise);

      // request to enable bluetooth
      this.reactContext.startActivityForResult(enableIntent, REQUEST_ENABLE_BT, Bundle.EMPTY);
    } else {
      Log.i(TAG, "Bluetooth enabled, resolving promise");
      promise.resolve(createEnableBluetoothResponse(true).toString());
    }
  }

  @ReactMethod
  @SuppressLint("MissingPermission")
  public void disableBluetooth(final Promise promise) {
    BluetoothAdapter adapter = this.getBluetoothAdapter();
    if (adapter == null) {
      promise.resolve(true);
    } else {
      if (mService != null && mService.getState() != BluetoothService.STATE_NONE) {
        mService.stop();
      }
      promise.resolve(!adapter.isEnabled() || adapter.disable());
    }
  }

  @ReactMethod
  public void isBluetoothEnabled(final Promise promise) {
    BluetoothAdapter adapter = this.getBluetoothAdapter();
    promise.resolve(adapter != null && adapter.isEnabled());
  }

  @ReactMethod
  public void isDeviceConnected(final Promise promise) {
    if (!getPermissionState()) {
      promise.reject(BluetoothService.PERMISSION_NOT_GRANTED, new Exception("Permission required to search for devices was not granted"));
      return;
    }

    if (mService != null) {
      promise.resolve(mService.getState() == BluetoothService.STATE_CONNECTED);
    } else {
      promise.reject(BluetoothService.BLUETOOTH_NOT_ENABLED, new Exception("Bluetooth not enabled on this device"));
    }
  }


  @ReactMethod
  @SuppressLint("MissingPermission")
  public void scanDevices(final Promise promise) {
    if (!getPermissionState()) {
      promise.reject(BluetoothService.PERMISSION_NOT_GRANTED, new Exception("Permission required to search for devices was not granted"));
      return;
    }

    BluetoothAdapter adapter = this.getBluetoothAdapter();
    if (adapter == null) {
      promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT, new Exception("Bluetooth not supported on this device"));
    } else {
      cancelScanDiscovery();

      sendReactNativeEventArray(EVENT_DEVICE_ALREADY_PAIRED, createMapDevices(getBondedDevices().values()));

      if (adapter.startDiscovery()) {
        promiseMap.put(PROMISE_SCAN, promise);
      } else {
        cancelScanDiscovery();
        promise.reject("DISCOVER", "NOT_STARTED");
      }
    }
  }

  @ReactMethod
  public void connect(String address, final Promise promise) {
    if (!getPermissionState()) {
      promise.reject(BluetoothService.PERMISSION_NOT_GRANTED, new Exception("Permission required to search for devices was not granted"));
      return;
    }

    BluetoothAdapter adapter = this.getBluetoothAdapter();
    if (adapter == null) {
      promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT, new Exception("Bluetooth not supported on this device"));
    } else {
      if (adapter.isEnabled()) {
        BluetoothDevice device = adapter.getRemoteDevice(address);
        promiseMap.put(PROMISE_CONNECT, promise);
        mService.connect(device);
      } else {
        promise.reject(BluetoothService.BLUETOOTH_NOT_ENABLED, new Exception("Bluetooth not enabled on this device"));
      }
    }
  }

  @ReactMethod
  public void disconnect(String address, final Promise promise) {
    if (!getPermissionState()) {
      promise.reject(BluetoothService.PERMISSION_NOT_GRANTED, new Exception("Permission required to search for devices was not granted"));
      return;
    }

    BluetoothAdapter adapter = this.getBluetoothAdapter();
    if (adapter == null) {
      promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT, new Exception("Bluetooth not supported on this device"));
    } else {
      if (adapter.isEnabled()) {
        try {
          mService.stop();
        } catch (Exception e) {
          Log.e(TAG, e.getMessage());
        }
        promise.resolve(address);
      } else {
        promise.reject(BluetoothService.BLUETOOTH_NOT_ENABLED, new Exception("Bluetooth not enabled on this device"));
      }
    }
  }

  @ReactMethod
  public void unpair(String address, final Promise promise) {
    if (!getPermissionState()) {
      promise.reject(BluetoothService.PERMISSION_NOT_GRANTED, new Exception("Permission required to search for devices was not granted"));
      return;
    }

    BluetoothAdapter adapter = this.getBluetoothAdapter();
    if (adapter == null) {
      promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT, new Exception("Bluetooth not supported on this device"));
    } else {
      if (adapter.isEnabled()) {
        BluetoothDevice device = adapter.getRemoteDevice(address);
        this.unpairDevice(device);
        promise.resolve(address);
      } else {
        promise.reject(BluetoothService.BLUETOOTH_NOT_ENABLED, new Exception("Bluetooth not enabled on this device"));
      }
    }
  }

  @ReactMethod
  public void printRaw(ReadableArray message, final Promise promise) {
    if (!getPermissionState()) {
      promise.reject(BluetoothService.PERMISSION_NOT_GRANTED, new Exception("Permission required to search for devices was not granted"));
      return;
    }

    BluetoothAdapter adapter = this.getBluetoothAdapter();
    if (adapter == null) {
      promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT, new Exception("Bluetooth not supported on this device"));
    } else {
      if (mService != null && adapter.isEnabled()) {

        if (mService.getState() == BluetoothService.STATE_CONNECTED) {
          byte[] decoded = new byte[message.size()];

          for (int i = 0; i < message.size(); i++) {
            decoded[i] = new Integer(message.getInt(i)).byteValue();
          }

          try {
            mService.write(decoded);
            promise.resolve(true);
          } catch (Exception e) {
            promise.reject(BluetoothService.UNABLE_PRINT, e);
          }
        } else {
          promise.reject(BluetoothService.NOT_CONNECTED, new Exception("Not connected to any device"));
        }

      } else {
        promise.reject(BluetoothService.BLUETOOTH_NOT_ENABLED, new Exception("Bluetooth not enabled on this device"));
      }
    }
  }

  @SuppressLint("MissingPermission")
  private void cancelScanDiscovery() {
    try {
      BluetoothAdapter adapter = this.getBluetoothAdapter();
      if (adapter != null && adapter.isDiscovering()) {
        adapter.cancelDiscovery();
      }
    } catch (Exception ignored) {
    }
  }


  private void unpairDevice(BluetoothDevice device) {
    try {
      Method m = device.getClass().getMethod("removeBond", (Class[]) null);
      m.invoke(device, (Object[]) null);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }
  }

  @SuppressLint("MissingPermission")
  private HashMap<String, BluetoothDevice> getBondedDevices() {
    BluetoothAdapter adapter = this.getBluetoothAdapter();

    Set<BluetoothDevice> boundDevices = adapter.getBondedDevices();

    pairedDevices = new HashMap<String, BluetoothDevice>();

    for (BluetoothDevice d : boundDevices) {
      pairedDevices.put(d.getAddress(), (BluetoothDevice) d);
    }

    return pairedDevices;
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    BluetoothAdapter adapter = this.getBluetoothAdapter();
    Log.d(TAG, "onActivityResult " + resultCode);
    switch (requestCode) {
      case REQUEST_CONNECT_DEVICE: {
        // When DeviceListActivity returns with a device to connect
        if (resultCode == Activity.RESULT_OK) {
          // Get the device MAC address
          String address = data.getExtras().getString(BluetoothService.DEVICE_ADDRESS);

          // Get the Bluetooth object
          if (adapter != null && BluetoothAdapter.checkBluetoothAddress(address)) {
            BluetoothDevice device = adapter.getRemoteDevice(address);

            // Attempt to connect to the device
            mService.connect(device);
          }
        }
      }

      case REQUEST_SETTINGS: {
        Promise promise = promiseMap.remove(PROMISE_SETTINGS);

        if (promise != null) {
          // When the request to enable Bluetooth returns
          if (resultCode == Activity.RESULT_OK) {
            promise.resolve(true);
          } else {
            promise.resolve(false);
          }
        }
      }

      case REQUEST_ENABLE_BT: {
        Promise promise = promiseMap.remove(PROMISE_ENABLE_BT);

        // When the request to enable Bluetooth returns
        if (resultCode == Activity.RESULT_OK && promise != null) {
          // Bluetooth is now enabled, so set up a session
          if (adapter != null) {
            promise.resolve(createEnableBluetoothResponse(true).toString());
          } else {
            promise.resolve(createEnableBluetoothResponse(false).toString());
          }
        } else {
          // User did not enable Bluetooth or an error occurred
          Log.d(TAG, BluetoothService.BLUETOOTH_NOT_ENABLED);
          if (promise != null) {
            promise.resolve(createEnableBluetoothResponse(false, true).toString());
          }
        }
      }
    }
  }

  // The BroadcastReceiver that listens for discovered devices
  @SuppressLint("MissingPermission")
  private final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      Log.d(TAG, "on receive:" + action);

      // When discovery finds a device
      if (BluetoothDevice.ACTION_FOUND.equals(action)) {
        // Get the BluetoothDevice object from the Intent
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
          if (foundedDevices.get(device.getAddress()) == null) {
            foundedDevices.put(device.getAddress(), device);
            sendReactNativeEvent(EVENT_DEVICE_FOUND, createMapDevice(device));
          }
        }
      }

      if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
        Promise promise = promiseMap.remove(PROMISE_SCAN);
        if (promise != null) {
          WritableNativeMap params = new WritableNativeMap();

          params.putArray("paired", (ReadableArray) createMapDevices(pairedDevices.values()));
          params.putArray("found", (ReadableArray) createMapDevices(foundedDevices.values()));

          promise.resolve(params.toString());
          sendReactNativeEvent(EVENT_DEVICE_DISCOVER_DONE, params);
        }
      }
    }
  };

  @Override
  public void onBluetoothServiceStateChanged(int state, Map<String, Object> bundle, Exception exception) {
    Log.i(TAG, "onBluetoothServiceStateChanged: " + state + " (" + mService.getStateName(state) + ")");

    switch (state) {
      case BluetoothService.STATE_CONNECTED: {
        mConnectedDeviceName = (String) bundle.get(BluetoothService.DEVICE_NAME);
        mConnectedDeviceAddress = (String) bundle.get(BluetoothService.DEVICE_ADDRESS);

        WritableNativeMap params = new WritableNativeMap();

        params.putString("name", mConnectedDeviceName);
        params.putString("address", mConnectedDeviceAddress);

        Promise p = promiseMap.remove(PROMISE_CONNECT);

        if (p != null) {
          p.resolve(params.toString());
        }

        Log.i(TAG, "Connection to the device was successful");

        sendReactNativeEvent(EVENT_CONNECTED, params);
      }

      case BluetoothService.MESSAGE_CONNECTION_LOST: {
        Log.e(TAG, "Connection with device has been lost");
        sendReactNativeEvent(EVENT_CONNECTION_LOST, null);
      }

      case BluetoothService.MESSAGE_UNABLE_CONNECT: {
        Log.e(TAG, "Unable to connect: " + mService.getStateName(mService.getState()));

        // if only current status is connecting (request connect by user)
        if (mService.getState() == BluetoothService.STATE_CONNECTING) {
          Promise p = promiseMap.remove(PROMISE_CONNECT);

          if (p != null) {
            p.reject(BluetoothService.UNABLE_CONNECT, exception);
          } else {
            WritableNativeMap params = new WritableNativeMap();

            if (exception != null) {
              params.putString("message", exception.getMessage());
              params.putString("stack_trace", Arrays.toString(exception.getStackTrace()));
            }

            sendReactNativeEvent(EVENT_UNABLE_CONNECT, params);
          }
        }
      }
      default: {
      }
    }
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    Log.i(TAG, "Permission request result :" + requestCode + Arrays.toString(permissions) + Arrays.toString(grantResults));
    if (requestCode == REQUEST_PERMISSION) {
      Promise p = promiseMap.remove(PROMISE_PERMISSION);
      if (p != null) {

        if (getPermissionState()) {
          Log.i(TAG, "Permission granted");
          p.resolve(createRequestPermissionResponse(true, false).toString());
        } else {
          Log.i(TAG, "Permission granted");
          p.resolve(createRequestPermissionResponse(false, true).toString());
        }

//        // Android 12+
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
//          if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
//            Log.i(TAG, "Permission granted");
//            p.resolve(createRequestPermissionResponse(true, false).toString());
//          }
//
//          if (grantResults[0] == PackageManager.PERMISSION_DENIED || grantResults[1] == PackageManager.PERMISSION_DENIED) {
//            Log.i(TAG, "Permission denied");
//            p.resolve(createRequestPermissionResponse(false, true).toString());
//          }
//        } else {
//          if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            Log.i(TAG, "Permission granted");
//            p.resolve(createRequestPermissionResponse(true, false).toString());
//          }
//
//          if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
//            Log.i(TAG, "Permission denied");
//            p.resolve(createRequestPermissionResponse(false, true).toString());
//          }
//        }
      } else {
        Log.e(TAG, "Permission promise not found");
      }
    }
    return true;
  }

  @SuppressLint("MissingPermission")
  private WritableNativeMap createMapDevice(BluetoothDevice device) {
    WritableNativeMap writableNativeMap = new WritableNativeMap();
    try {
      writableNativeMap.putString("name", device.getName() != null ? device.getName() : "NO NAME");
      writableNativeMap.putString("address", device.getAddress());
    } catch (Exception ignored) {
    }
    return writableNativeMap;
  }

  @SuppressLint("MissingPermission")
  private WritableNativeArray createMapDevices(Collection<BluetoothDevice> devices) {
    WritableNativeArray writableNativeArray = new WritableNativeArray();
    for (BluetoothDevice d : devices) {
      writableNativeArray.pushMap(createMapDevice(d));
    }
    return writableNativeArray;
  }


  @SuppressLint("MissingPermission")
  private WritableNativeMap createEnableBluetoothResponse(Boolean success, Boolean rejected) {
    WritableNativeMap writableNativeMap = new WritableNativeMap();

    writableNativeMap.putBoolean("success", Boolean.TRUE.equals(success));
    writableNativeMap.putBoolean("rejected", Boolean.TRUE.equals(rejected));
    if (success) {
      writableNativeMap.putArray("devices", createMapDevices(getBondedDevices().values()));
    }
    return writableNativeMap;
  }

  @SuppressLint("MissingPermission")
  private WritableNativeMap createEnableBluetoothResponse(Boolean success) {
    return createEnableBluetoothResponse(success, false);
  }

  @SuppressLint("MissingPermission")
  private WritableNativeMap createRequestPermissionResponse(Boolean success, Boolean rejected) {
    WritableNativeMap writableNativeMap = new WritableNativeMap();
    writableNativeMap.putBoolean("success", Boolean.TRUE.equals(success));
    writableNativeMap.putBoolean("rejected", Boolean.TRUE.equals(rejected));
    return writableNativeMap;
  }


  @Override
  public void onNewIntent(Intent intent) {
  }

  private void sendReactNativeEvent(String event, @Nullable WritableMap params) {
    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(event, params);
  }

  private void sendReactNativeEventArray(String event, @Nullable WritableArray params) {
    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(event, params);
  }

}
