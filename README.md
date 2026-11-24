
# 🚨 MotionGuard – Smart Bluetooth Motion Detection & Security Alarm

MotionGuard is a smart Arduino-based security system that transforms your Android smartphone into a wireless security alarm. Using an Arduino UNO, PIR motion sensor, and HC-05 Bluetooth module, the system detects movement and instantly sends an alert to your Android app. Once motion is detected, the phone starts a continuous alarm that only stops when you manually press the STOP ALARM button. Simple, affordable, and intelligent – your personal security guard is always ready! 🛡️

---

## 📸 Project Circuit Diagram

![MotionGuard Circuit Diagram](Project%20Diagram.png)

---

## ✨ Key Features

- ⚡ Fast and responsive PIR motion detection.
- 📱 Wireless Bluetooth communication with Android.
- 🚨 Instant motion detection alerts.
- 🔊 Continuous mobile alarm until manually stopped.
- 📳 Phone vibration alert.
- 🔄 Automatically prepares for the next motion event.
- 🔌 No external buzzer or LED required.
- 🏠 Affordable DIY security solution.

---

## 🧰 Required Components

| Component | Quantity |
|---|---|
| Arduino UNO | 1 |
| PIR Motion Sensor | 1 |
| HC-05 Bluetooth Module | 1 |
| Jumper Wires | As required |
| Android Smartphone | 1 |

---

## 🔌 Circuit Connections

### PIR Motion Sensor → Arduino UNO

| PIR Pin | Arduino Pin |
|---|---|
| VCC | 5V |
| GND | GND |
| OUT | D3 |

### HC-05 Bluetooth → Arduino UNO

| HC-05 Pin | Arduino Pin |
|---|---|
| VCC | 5V |
| GND | GND |
| TXD | D0 (RX) |
| RXD | D1 (TX) through voltage divider or level shifter |

**Important:** Use a suitable voltage divider or level shifter between Arduino D1/TX (5V) and HC-05 RXD (typically 3.3V logic). Check your HC-05 breakout board specifications.

Disconnect HC-05 TXD and RXD while uploading the Arduino code.

---

## 💻 Arduino UNO Code

```cpp
const byte PIR_PIN = 3;

// PIR calibration time
const unsigned long CALIBRATION_TIME = 30000;

// Motion state
bool systemReady = false;
bool previousMotionState = false;

unsigned long startupTime = 0;

void setup() {

  pinMode(PIR_PIN, INPUT);

  // HC-05 communication
  Serial.begin(9600);

  startupTime = millis();
}

void loop() {

  // Non-blocking PIR calibration
  if (!systemReady) {

    if (millis() - startupTime >= CALIBRATION_TIME) {

      systemReady = true;

      Serial.println("SYSTEM_READY");
    }

    return;
  }

  // Read PIR sensor
  bool currentMotionState = digitalRead(PIR_PIN);

  // Detect new motion immediately
  if (currentMotionState && !previousMotionState) {

    previousMotionState = true;

    Serial.println("MOTION_DETECTED");
  }

  // Immediately prepare for next motion
  else if (!currentMotionState && previousMotionState) {

    previousMotionState = false;

    Serial.println("MOTION_ENDED");
  }
}
```

---

## 📡 Bluetooth Communication

The Arduino communicates with the Android application through the HC-05 Bluetooth module using a 9600 baud serial connection.

| Bluetooth Command | Description |
|---|---|
| SYSTEM_READY | System initialization completed |
| MOTION_DETECTED | PIR sensor detected movement |
| MOTION_ENDED | PIR sensor output returned to LOW |

---

## ⚙️ How It Works

1. **Startup:** Arduino initializes and allows 30 seconds for PIR sensor calibration.
2. **Detection:** PIR sensor detects movement and sends a HIGH signal to Arduino D3.
3. **Transmission:** Arduino immediately sends MOTION_DETECTED through HC-05.
4. **Mobile Alert:** Android app receives the command and starts a continuous alarm with vibration.
5. **Manual Stop:** The alarm continues even after motion ends. The user must press STOP ALARM.
6. **Ready Again:** Arduino immediately resumes monitoring for the next motion event.

---

## 📱 Android Application Features

- Bluetooth device connection.
- Live motion detection status.
- Continuous alarm sound.
- Manual STOP ALARM button.
- Phone vibration alerts.
- Motion detection history.
- Security ARM/DISARM controls.
- Bluetooth connection status.

**Technology:** Kotlin, Jetpack Compose and Bluetooth Classic SPP.

---

## 🏠 Best Applications

- Home and room security.
- Office and shop monitoring.
- Doorway and entrance surveillance.
- Garage and storage-room protection.
- Personal workspace monitoring.
- DIY smart security projects.

---

## 🚀 Future Improvements

- Custom alarm sound selection.
- Motion detection history with timestamps.
- Automatic Bluetooth reconnection.
- Adjustable alarm volume.
- Advanced security settings.
- Battery backup support.
- Multiple PIR sensor support.

---

## ⚠️ Important Notes

- Actual detection speed depends on the PIR sensor's response characteristics.
- Arduino sends one alert for each LOW-to-HIGH transition.
- A new motion event can be detected after the PIR output returns LOW and subsequently becomes HIGH again.
- Bluetooth connection and Android background restrictions can affect alert delivery.
- The Android app must implement continuous alarm playback and manual stop functionality.

---

## 💡 Build Your Own Smart Security System!

MotionGuard demonstrates how simple electronics and wireless communication can create a useful real-world security solution.

Keep experimenting, keep building, and turn your ideas into intelligent technology! 🔥

---

**Project Name:** MotionGuard

**Category:** Arduino | IoT | Bluetooth | Android | Home Security
