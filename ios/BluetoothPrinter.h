
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNBluetoothPrinterSpec.h"

@interface BluetoothPrinter : NSObject <NativeBluetoothPrinterSpec>
#else
#import <React/RCTBridgeModule.h>

@interface BluetoothPrinter : NSObject <RCTBridgeModule>
#endif

@end
