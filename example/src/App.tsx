/* eslint-disable react-native/no-inline-styles */
import React, { useEffect } from 'react';
import {
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

import BluetoothPrinter, {
  type iDevice,
} from '@linvix-sistemas/react-native-bluetooth-printer';

import {
  Printer,
  Style,
  Align,
  Model,
  InMemory,
} from '@linvix-sistemas/react-native-escpos-buffer';

export default function App() {
  const [Devices, setDevices] = React.useState<iDevice[]>([]);

  const goScanDevices = async () => {
    try {
      setDevices([]);
      const devices = await BluetoothPrinter.scanDevices();

      (devices?.paired ?? []).map((device) => {
        setDevices((old) => {
          if (!old.some((d) => d.address === device.address)) {
            old.push(device);
          }

          return [...old];
        });
      });

      (devices?.found ?? []).map((device) => {
        setDevices((old) => {
          if (!old.some((d) => d.address === device.address)) {
            old.push(device);
          }

          return [...old];
        });
      });

      console.log(devices);
    } catch (error: Error | any) {
      Alert.alert(error.message);
    }
  };

  const goEnableBluetooth = async () => {
    try {
      await BluetoothPrinter.requestPermission();
      await BluetoothPrinter.enableBluetooth();
    } catch (error: Error | any) {
      Alert.alert(error.message);
    }
  };

  const goConnect = async (device: iDevice) => {
    try {
      const connect = await BluetoothPrinter.connect(device.address);
      console.log(connect);
    } catch (error: Error | any) {
      Alert.alert(error.message);
    }
  };

  const goPrint = async () => {
    /**
     * Classe de geração do Impresso.
     */
    const connection = new InMemory();
    const printer = await Printer.CONNECT('TM-T20', connection);

    const capability = Model.EXPAND(Model.FIND('TM-T20'));
    const { feed } = capability;

    // printer.setColumns(48);
    await printer.writeln('TESTÉ');
    await printer.writeln('Align Center', 0, Align.Center);
    await printer.writeln('Align Left', 0, Align.Left);
    await printer.writeln('Align Right', 0, Align.Right);
    await printer.writeln();
    await printer.writeln('Bold Text', Style.Bold, Align.Center);
    await printer.writeln('Italic Text', Style.Italic, Align.Center);
    await printer.writeln('Underline Text', Style.Underline, Align.Center);
    await printer.writeln('Condensed Text', Style.Condensed, Align.Center);
    await printer.writeln(
      'All Styles',
      Style.Bold + Style.Italic + Style.Underline + Style.Condensed,
      Align.Center
    );
    await printer.writeln();
    await printer.writeln('DOUBLE WIDTH', Style.DoubleWidth, Align.Center);
    await printer.writeln('DOUBLE HEIGHT', Style.DoubleHeight, Align.Center);
    await printer.writeln(
      'WIDTH AND HEIGHT',
      Style.DoubleWidth + Style.DoubleHeight,
      Align.Center
    );
    await printer.writeln();
    await printer.writeln('With bold', 0, Align.Center);
    await printer.writeln(
      'DOUBLE WIDTH',
      Style.DoubleWidth + Style.Bold,
      Align.Center
    );
    await printer.writeln(
      'DOUBLE HEIGHT',
      Style.DoubleHeight + Style.Bold,
      Align.Center
    );
    await printer.writeln(
      'WIDTH AND HEIGHT',
      Style.DoubleWidth + Style.DoubleHeight + Style.Bold,
      Align.Center
    );
    await printer.writeln();
    await printer.writeln(
      'Áçênts $ 5',
      Style.DoubleWidth | Style.DoubleWidth,
      Align.Center
    );
    await printer.writeln();
    await printer.writeln(`Columns: ${printer.columns}`, 0, Align.Center);
    await printer.writeln(
      '1       10        20        30        40        50        60       70         80'
    );
    await printer.writeln('|                                              |');
    await printer.writeln(
      '12345678901234567890123456789012345678901234567890123456789012345678901234567890'
    );

    await printer.writeln('');

    await printer.writeln('QR Code', 0, Align.Center);
    await printer.setAlignment(Align.Center);
    await printer.qrcode(
      'https://github.com/linvix-sistemas/react-native-escpos-buffer',
      11
    );
    await printer.setAlignment(Align.Left);
    await printer.writeln('End QR Code', 0, Align.Center);

    await printer.writeln(`Last Line - Feed: ${feed}`, 0, Align.Center);

    await printer.cutter();

    const buffer = connection.buffer();

    const bytes = convertBufferToBytes(buffer);

    try {
      const print = await BluetoothPrinter.printRaw(bytes);
      console.log(print);
    } catch (error: Error | any) {
      Alert.alert(error.message);
    }
  };

  const convertBufferToBytes = (buffer: Buffer) => {
    const bytes: number[] = [];
    Array.from(buffer).map((byte) => {
      bytes.push(byte);
    });
    return bytes;
  };

  useEffect(() => {
    const listener = BluetoothPrinter.onDeviceFound((device: iDevice) => {
      console.log(device);
      setDevices((old) => {
        if (!old.some((d) => d.address === device.address)) {
          old.push(device);
        }

        return [...old];
      });
    });
    return () => listener?.remove();
  }, []);

  useEffect(() => {
    const listener = BluetoothPrinter.onDeviceAlreadyPaired(
      (devices: iDevice[]) => {
        (devices ?? []).map((device) => {
          setDevices((old) => {
            if (!old.some((d) => d.address === device.address)) {
              old.push(device);
            }

            return [...old];
          });
        });
      }
    );
    return () => listener?.remove();
  }, []);

  return (
    <View style={styles.container}>
      <View
        style={{
          flexDirection: 'row',
          justifyContent: 'space-between',
          marginBottom: 20,
        }}
      >
        <TouchableOpacity onPress={goEnableBluetooth}>
          <Text style={{ color: '#405e7b' }}>Ligar bluetooth</Text>
        </TouchableOpacity>

        <TouchableOpacity onPress={goScanDevices}>
          <Text style={{ color: '#405e7b' }}>Buscar dispositivos</Text>
        </TouchableOpacity>
      </View>

      <View
        style={{
          flexDirection: 'row',
          justifyContent: 'center',
          marginBottom: 20,
        }}
      >
        <TouchableOpacity onPress={goPrint}>
          <Text style={{ color: '#405e7b' }}>Imprimir texto</Text>
        </TouchableOpacity>
      </View>

      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ flexGrow: 1, padding: 10 }}
      >
        {Devices.map((device) => {
          return (
            <View key={device.address} style={{ flexDirection: 'row' }}>
              <View
                style={{ flex: 1, flexDirection: 'column', marginBottom: 10 }}
              >
                <Text>{device.name}</Text>
                <Text>{device.address}</Text>
              </View>

              <TouchableOpacity onPress={() => goConnect(device)}>
                <Text style={{ color: '#405e7b' }}>Conectar</Text>
              </TouchableOpacity>
            </View>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
