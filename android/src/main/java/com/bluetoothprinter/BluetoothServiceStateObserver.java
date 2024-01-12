package com.bluetoothprinter;

import java.util.Map;

public interface BluetoothServiceStateObserver {
  void onBluetoothServiceStateChanged(int state, Map<String, Object> bundle);
}
