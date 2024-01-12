export type iDevice = {
  name: string;
  address: string;
};

export type iRequestPermissionResponse = {
  success: boolean;
  rejected: boolean;
};

export type iEnableBluetoothResponse = {
  success: boolean;
  rejected: boolean;
  devices?: iDevice[];
};

export type iScanDevicesResponse = {
  paired: iDevice[];
  found: iDevice[];
};
