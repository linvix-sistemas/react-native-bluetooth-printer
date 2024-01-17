package com.bluetoothprinter;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices.
 */
public class BluetoothService {
  // Debugging
  private static final String TAG = "RNBluetoothPrinter";

  //UUID must be this
  // Unique UUID for this application
  private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

  // Member fields
  private BluetoothAdapter mAdapter;

  private ConnectedThread mConnectedThread;
  private int mState;

  // Constants that indicate the current connection state
  public static final int STATE_NONE = 0;       // we're doing nothing
  public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
  public static final int STATE_CONNECTED = 3;  // now connected to a remote device


  public static final int MESSAGE_STATE_CHANGE = 4;
  public static final int MESSAGE_READ = 5;
  public static final int MESSAGE_WRITE = 6;
  public static final int MESSAGE_CONNECTION_LOST = 8;
  public static final int MESSAGE_UNABLE_CONNECT = 9;
  public static final int MESSAGE_UNABLE_PRINT = 10;

  // Key names received from the BluetoothService Handler
  public static final String DEVICE_NAME = "device_name";
  public static final String DEVICE_ADDRESS = "device_address";
  public static final String TOAST = "toast";

  public static final String BLUETOOTH_NOT_ENABLED = "BLUETOOTH_NOT_ENABLED";

  public static final String PERMISSION_NOT_GRANTED = "PERMISSION_NOT_GRANTED";

  public static final String NOT_CONNECTED = "NOT_CONNECTED";

  public static final String UNABLE_CONNECT = "UNABLE_CONNECT";

  public static final String UNABLE_PRINT = "UNABLE_PRINT";

  public static String ErrorMessage = "No_Error_Message";

  private static List<BluetoothServiceStateObserver> observers = new ArrayList<BluetoothServiceStateObserver>();
  private String mLastConnectedDeviceAddress = "";

  /**
   * Constructor. Prepares a new BTPrinter session.
   *
   * @param context The UI Activity Context
   */
  public BluetoothService(Context context) {
    mAdapter = BluetoothAdapter.getDefaultAdapter();
    mState = STATE_NONE;
  }

  public void addStateObserver(BluetoothServiceStateObserver observer) {
    observers.add(observer);
  }

  public void removeStateObserver(BluetoothServiceStateObserver observer) {
    observers.remove(observer);
  }

  /**
   * Set the current state of the connection
   *
   * @param state An integer defining the current connection state
   */
  private synchronized void setState(int state, Map<String, Object> bundle) {
    Log.d(TAG, "setState() " + getStateName(mState) + " -> " + getStateName(state));
    mState = state;
    infoObservers(state, bundle, null);
  }

  public String getStateName(int state) {
    if (STATE_NONE == state) {
      return "STATE_NONE";
    }
    if (STATE_CONNECTED == state) {
      return "STATE_CONNECTED";
    }
    if (STATE_CONNECTING == state) {
      return "STATE_CONNECTING";
    }
    return "UNKNOWN:" + state;
  }

  private synchronized void infoObservers(int code, Map<String, Object> bundle, Exception exception) {
    for (BluetoothServiceStateObserver ob : observers) {
      ob.onBluetoothServiceStateChanged(code, bundle, exception);
    }
  }

  /**
   * Return the current connection state.
   */
  //todo: get the method in react to check the current connection state
  public synchronized int getState() {
    return mState;
  }

  /**
   * Start the ConnectThread to initiate a connection to a remote device.
   *
   * @param device The BluetoothDevice to connect
   */
  @SuppressLint("MissingPermission")
  public synchronized void connect(BluetoothDevice device) {
    Log.d(TAG, "connect to: " + device);
    BluetoothDevice connectedDevice = null;

    if (mConnectedThread != null) {
      connectedDevice = mConnectedThread.bluetoothDevice();
    }

    // device already connected into this device with same address
    if (mState == STATE_CONNECTED && connectedDevice != null && connectedDevice.getAddress().equals(device.getAddress())) {
      Map<String, Object> bundle = new HashMap<String, Object>();
      bundle.put(DEVICE_NAME, device.getName());
      bundle.put(DEVICE_ADDRESS, device.getAddress());
      setState(STATE_CONNECTED, bundle);
    } else {
      // Cancel any thread currently running a connection
      this.stop();

      // Start the thread to manage the connection and perform transmissions
      mConnectedThread = new ConnectedThread(device);
      mConnectedThread.start();
    }
  }

  public synchronized boolean isConnectedThreadRunning() {
    return mConnectedThread != null && mConnectedThread.isAlive();
  }

  /**
   * Stop all threads
   */
  public synchronized void stop() {
    if (mConnectedThread != null) {
      mConnectedThread.cancel();
      mConnectedThread = null;
    }
  }

  /**
   * Write to the ConnectedThread in an unsynchronized manner
   *
   * @param out The bytes to write
   * @see ConnectedThread#write(byte[])
   */
  public void write(byte[] out) throws Exception {
    // Create temporary object
    ConnectedThread r;
    // Synchronize a copy of the ConnectedThread
    synchronized (this) {
      if (mState != STATE_CONNECTED) return;
      r = mConnectedThread;
    }
    r.write(out);
  }

  /**
   * Indicate that the connection attempt failed.
   */
  private void connectionFailed(Exception e) {
    infoObservers(MESSAGE_UNABLE_CONNECT, null, e);
    setState(STATE_NONE, null);
  }

  /**
   * Indicate that the connection was lost and notify the UI Activity.
   */
  private void connectionLost(@Nullable Exception exception) {
    infoObservers(MESSAGE_CONNECTION_LOST, null, exception);
    setState(STATE_NONE, null);
  }

  /**
   * This thread runs during a connection with a remote device.
   * It handles all incoming and outgoing transmissions.
   */
  private class ConnectedThread extends Thread {
    private final BluetoothDevice mmDevice;
    private BluetoothSocket mmSocket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;

    public ConnectedThread(BluetoothDevice device) {
      mmDevice = device;
      device.getAddress();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
      //
      setState(STATE_CONNECTING, null);

      Log.i(TAG, "BEGIN mConnectThread");
      setName("ConnectThread");
      Map<String, Object> bundle = new HashMap<String, Object>();

      // Always cancel discovery because it will slow down a connection
      mAdapter.cancelDiscovery();

      BluetoothSocket tmp = null;
      Exception exception = null;

      // try to connect with socket inner method firstly.
      for (int i = 1; i <= 3; i++) {
        try {
          tmp = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", int.class).invoke(mmDevice, i);
        } catch (Exception e) {
          Log.e(TAG, "createRfcommSocket() failed:", e);
          e.printStackTrace();
          exception = e;
        }
        if (tmp != null) {
          mmSocket = tmp;
          break;
        }
      }

      // try with given uuid
      if (mmSocket == null) {
        try {
          tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
          e.printStackTrace();
          exception = e;
        }
        if (tmp == null) {
          Log.e(TAG, "createRfcommSocketToServiceRecord() failed:", exception);
          connectionFailed(exception);
          return;
        }
      }
      mmSocket = tmp;

      // Make a connection to the BluetoothSocket
      try {
        // This is a blocking call and will only return on a
        // successful connection or an exception
        mmSocket.connect();
      } catch (Exception e) {
        e.printStackTrace();
        connectionFailed(e);

        // Close the socket
        try {
          mmSocket.close();
        } catch (Exception e2) {
          Log.e(TAG, "unable to close() socket during connection failure", e2);
        }
        return;
      }

      Log.d(TAG, "create ConnectedThread");
      InputStream tmpIn = null;
      OutputStream tmpOut = null;

      // Get the BluetoothSocket input and output streams
      try {
        tmpIn = mmSocket.getInputStream();
        tmpOut = mmSocket.getOutputStream();
      } catch (IOException e) {
        Log.e(TAG, "temp sockets not created", e);
      }

      mmInStream = tmpIn;
      mmOutStream = tmpOut;

      bundle.put(DEVICE_NAME, mmDevice.getName());
      bundle.put(DEVICE_ADDRESS, mmDevice.getAddress());
      setState(STATE_CONNECTED, bundle);

      Log.i(TAG, "Connected");
      int bytes;

      //keep the address of last connected device and get this address directly in the .js code
      mLastConnectedDeviceAddress = mmDevice.getAddress();

      // Keep listening to the InputStream while connected
      while (true) {
        try {
          byte[] buffer = new byte[256];
          // Read from the InputStream
          bytes = mmInStream.read(buffer);
          if (bytes > 0) {
            // Send the obtained bytes to the UI Activity
            bundle = new HashMap<String, Object>();
            bundle.put("bytes", bytes);
            infoObservers(MESSAGE_READ, bundle, null);
          } else {
            Log.e(TAG, "disconnected");
            connectionLost(null);
            break;
          }
        } catch (IOException e) {
          Log.e(TAG, "disconnected", e);
          connectionLost(e);
          break;
        }
      }
      Log.i(TAG, "ConnectedThread End");
    }

    /**
     * Write to the connected OutStream.
     *
     * @param buffer The bytes to write
     */
    public void write(byte[] buffer) throws Exception {
      try {
        mmOutStream.write(buffer);
        mmOutStream.flush(); // clean cache
        Log.i(TAG, new String(buffer, StandardCharsets.UTF_8));
        Map<String, Object> bundle = new HashMap<String, Object>();
        bundle.put("bytes", buffer);
        infoObservers(MESSAGE_WRITE, bundle, null);
      } catch (IOException e) {
        Log.e(TAG, "Exception during write", e);
        infoObservers(MESSAGE_UNABLE_PRINT, null, e);
        throw e;
      }
    }

    public BluetoothDevice bluetoothDevice() {
      if (mmSocket != null && mmSocket.isConnected()) {
        return mmSocket.getRemoteDevice();
      } else {
        return null;
      }
    }

    public void cancel() {
      try {
        mmSocket.close();
        connectionLost(null);
      } catch (IOException e) {
        Log.e(TAG, "close() of connect socket failed", e);
      }
    }
  }


  //Method to get the address of the last connected device
  public String getLastConnectedDeviceAddress() {
    return mLastConnectedDeviceAddress;
  }
}
