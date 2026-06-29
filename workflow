name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:  # Cho phép chạy thủ công

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Lấy code
        uses: actions/checkout@v4

      - name: Cài đặt JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cấp quyền chạy gradlew
        run: chmod +x gradlew

      - name: Build APK
        run: ./gradlew assembleDebug

      - name: Tải APK lên Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: GameBooster-APK
          path: app/build/outputs/apk/debug/app-debug.apk
