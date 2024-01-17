package com.bluetoothprinter;

import androidx.annotation.Nullable;

import java.util.Map;

public interface BluetoothServiceStateObserver {
  void onBluetoothServiceStateChanged(int state, Map<String, Object> bundle, Exception exception);
}
