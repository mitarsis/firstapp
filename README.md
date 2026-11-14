# FirstApp

Минимальный Android-проект на Jetpack Compose. Собирается в GitHub Actions,
потому что на телефоне Gradle живёт плохо.

## Как собрать

1. Запушить в `main`.
2. Открыть вкладку **Actions** → workflow **Build APK**.
3. Дождаться зелёной галочки (~3-5 минут на холодную).
4. Скачать артефакт `app-debug.zip` → внутри `app-debug.apk`.
5. Установить на телефон (разрешить установку из неизвестных источников).

## Структура

- `app/src/main/java/com/example/firstapp/MainActivity.kt` — весь UI.
- `app/build.gradle.kts` — зависимости и версии SDK.
- `.github/workflows/build.yml` — CI.
