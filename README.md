


# WootzApp WebView Sample Application

This sample application demonstrates the implementation of WootzWebView with extension support. The project showcases how to integrate and use WootzWebView extensions in your Android application.

## Prerequisites

- Android Studio 
- Android device or emulator (ARM64)
- ADB (Android Debug Bridge) installed and configured
- WootzWebview library (v1.0.0)

## Setup Instructions

### 1. Project Configuration

1. Clone this repository to your local machine
2. Open the project in Android Studio
3. Add the WootzWebview library to your project:
   - Copy `wootzview-v1.0.0.arm64.aar` to the `app/libs` directory
   - Add the following to your app's `build.gradle`:
   ```gradle
   dependencies {
       implementation files('libs/wootzview-v1.0.0.arm64.aar')
   }
   ```

### 2. Installing Extensions

After building and running the application, you need to push the extension files to the device using ADB:

```bash
adb push exts/ext-demo /data/local/tmp/ext-test
```

## Project Structure

```
├── app/
│   ├── libs/
│   │   └── wootzview-v1.0.0.arm64.aar
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       └── res/
└── exts/
    └── ext-demo/
```

## Features

- Demonstrates basic WebView functionality
- Shows extension integration
- Provides sample extension implementation
- Includes extension loading and initialization

## Usage

1. Build and run the application on your device/emulator
2. Push the required extensions using ADB as shown above
3. Launch the application
4. The WebView will load with the extensions enabled
5. Test the extension functionality using the provided demo interface

## Troubleshooting

If you encounter issues:

1. Verify that the AAR library is correctly added to your project
2. Ensure the extension files are properly pushed to the device
3. Check logcat for any error messages
4. Verify that your device architecture matches (ARM64)

## Contributing

Feel free to submit issues and enhancement requests.

