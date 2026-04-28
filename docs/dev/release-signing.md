# Release signing — App Lab template

Khi nào ta sẵn sàng submit App Lab, build flavor cần một keystore
release. Hiện debug build dùng default `~/.android/debug.keystore` —
không submit được. Đây là template Cognition-friendly: tất cả secret
nằm trong env / `~/.gradle/gradle.properties`, không commit vào repo.

## 1. Tạo keystore

```bash
keytool -genkey -v \
  -keystore vrplayer-release.jks \
  -alias vrplayer \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -storetype JKS
```

Lưu file `.jks` vào nơi an toàn (1Password / Drive cá nhân). KHÔNG
commit. KHÔNG để trong repo.

## 2. Cấu hình build

Thêm vào `~/.gradle/gradle.properties` (KHÔNG vào repo):

```
VRPLAYER_KEYSTORE_PATH=/abs/path/to/vrplayer-release.jks
VRPLAYER_KEYSTORE_PASSWORD=...
VRPLAYER_KEY_ALIAS=vrplayer
VRPLAYER_KEY_PASSWORD=...
```

Trong `app/build.gradle.kts` (chưa thêm — sẽ làm khi App Lab):

```kotlin
android {
    signingConfigs {
        create("release") {
            val ks = providers.gradleProperty("VRPLAYER_KEYSTORE_PATH").orNull
            if (ks != null) {
                storeFile = file(ks)
                storePassword = providers.gradleProperty("VRPLAYER_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("VRPLAYER_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("VRPLAYER_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

## 3. Build release APK

```bash
./gradlew :app:assembleQuestRelease
```

Output: `app/build/outputs/apk/quest/release/app-quest-release.apk`.

## 4. Verify chữ ký

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs app-quest-release.apk
```

## 5. App Lab metadata yêu cầu

- Title: VR Player
- Package: `dev.ngminhphuc.vrplayer.quest`
- Min SDK: 29 (Android 10)
- Target SDK: 32 (Android 12)
- Supported devices: Quest 2, Quest 3, Quest Pro (manifest đã đặt).
- Comfort rating: Comfortable (cinema 180° + procedural skybox + grip
  drag opt-in).
- Privacy policy URL: bắt buộc, ngay cả với app không thu thập data.
  Hosting trên GitHub Pages của repo là đủ.
- Screenshot 4K + trailer 30 s: chụp/quay từ headset.

## 6. CI / GitHub Actions (tùy chọn)

Khi sẵn sàng tự động hoá:
- Lưu keystore base64 vào GitHub Secret `VRPLAYER_KEYSTORE_B64`.
- Lưu password vào secret riêng.
- Workflow `release.yml` decode keystore, set env, chạy
  `assembleQuestRelease`, upload artifact.

Hiện chưa cần — sideload + manual App Lab submission đủ cho cột mốc
hiện tại.
