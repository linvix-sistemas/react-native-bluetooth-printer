import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

import type {
  iDevice,
  iEnableBluetoothResponse,
  iRequestPermissionResponse,
  iScanDevicesResponse,
} from './types';

const LINKING_ERROR =
  `The package '@linvix-sistemas/react-native-bluetooth-printer' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const BluetoothPrinterModule = NativeModules.BluetoothPrinter
  ? NativeModules.BluetoothPrinter
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const BluetoothPrinterEventEmitter = new NativeEventEmitter(
  BluetoothPrinterModule
);

/**
 * Solicita a permissão necessária para poder utilizar os recursos
 */
const requestPermission = async (): Promise<iRequestPermissionResponse> => {
  const permissionPromise = JSON.parse(
    await BluetoothPrinterModule.requestPermission()
  );
  return permissionPromise as iRequestPermissionResponse;
};

/**
 * Liga o bluetooth do dispositivo
 */
const enableBluetooth = async (): Promise<iEnableBluetoothResponse> => {
  const enableBluetoothPromise = JSON.parse(
    await BluetoothPrinterModule.enableBluetooth()
  );
  return enableBluetoothPromise as iEnableBluetoothResponse;
};

/**
 * Desativa o bluetooth do dispositivo
 */
const disableBluetooth = async (): Promise<boolean> => {
  return (await BluetoothPrinterModule.disableBluetooth()) === true;
};

/**
 * Verifica se o bluetooth está ligado
 */
const isBluetoothEnabled = async (): Promise<boolean> => {
  return (await BluetoothPrinterModule.isBluetoothEnabled()) === true;
};

/**
 * Verifica se o dispositivo está conectado
 */
const isDeviceConnected = async (): Promise<boolean> => {
  return (await BluetoothPrinterModule.isDeviceConnected()) === true;
};

/**
 * Busca os dispositivos
 */
const scanDevices = async (): Promise<iScanDevicesResponse> => {
  const scanPromise = JSON.parse(await BluetoothPrinterModule.scanDevices());
  return scanPromise as iScanDevicesResponse;
};

/**
 * Conecta em um dispositivo pelo endereço
 */
const connect = async (address: string) => {
  const connectPromise = JSON.parse(
    await BluetoothPrinterModule.connect(address)
  );
  return connectPromise as iDevice;
};

/**
 * Desconecta do dispositivo
 */
const disconnect = async (address: string) => {
  const disconnectPromise = JSON.parse(
    await BluetoothPrinterModule.disconnect(address)
  );
  return disconnectPromise as string;
};

/**
 * Desemparelha o o dispositivo
 */
const unpair = async (address: string) => {
  const unpariPromise = JSON.parse(
    await BluetoothPrinterModule.unpair(address)
  );
  return unpariPromise as string;
};

/**
 * Envia os bytes para impressão
 */
const printRaw = async (bytes: any) => {
  const connectPromise = await BluetoothPrinterModule.printRaw(bytes);
  return connectPromise as boolean;
};

/**
 * Quando localizar um novo dispositivo bluetooth
 */
const onDeviceFound = (callback: (device: iDevice) => void) => {
  const listener = BluetoothPrinterEventEmitter.addListener(
    'EVENT_DEVICE_FOUND',
    callback
  );
  return listener;
};

/**
 * Quando enviar os dispositivos já pareados
 */
const onDeviceAlreadyPaired = (callback: (devices: iDevice[]) => void) => {
  const listener = BluetoothPrinterEventEmitter.addListener(
    'EVENT_DEVICE_ALREADY_PAIRED',
    callback
  );
  return listener;
};

/**
 * Quando finalizar a pesquisa de dispositivos
 */
const onScanDone = (callback: (data: iScanDevicesResponse) => void) => {
  const listener = BluetoothPrinterEventEmitter.addListener(
    'EVENT_DEVICE_DISCOVER_DONE',
    callback
  );
  return listener;
};

/**
 * Quando receber aviso do bluetooth não suportado
 */
const onBluetoothNotSupported = (callback: () => void) => {
  const listener = BluetoothPrinterEventEmitter.addListener(
    'EVENT_BLUETOOTH_NOT_SUPPORT',
    callback
  );
  return listener;
};

/**
 * Quando conectar em um dispositivo
 */
const onDeviceConnect = (callback: (device: iDevice) => void) => {
  const listener = BluetoothPrinterEventEmitter.addListener(
    'EVENT_CONNECTED',
    callback
  );
  return listener;
};

/**
 * Quando desconectar o dispositivo
 */
const onDeviceDisconnect = (callback: () => void) => {
  const listener = BluetoothPrinterEventEmitter.addListener(
    'EVENT_CONNECTION_LOST',
    callback
  );
  return listener;
};

/**
 * Quando receber mensagem que não foi possível se conectar
 */
const onUnableToConnect = (callback: () => void) => {
  const listener = BluetoothPrinterEventEmitter.addListener(
    'EVENT_UNABLE_CONNECT',
    callback
  );
  return listener;
};

const BluetoothPrinter = {
  requestPermission,
  enableBluetooth,
  disableBluetooth,
  isBluetoothEnabled,
  isDeviceConnected,
  scanDevices,
  connect,
  disconnect,
  printRaw,
  unpair,

  onBluetoothNotSupported,
  onDeviceAlreadyPaired,
  onDeviceDisconnect,
  onUnableToConnect,
  onDeviceConnect,
  onDeviceFound,
  onScanDone,
};

export * from './types';
export default BluetoothPrinter;
