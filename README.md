# BikeMeteo

Простое Android-приложение на Kotlin: анимированный метеорадар из `https://goldwing.dedyn.io/index.php` отображается в WebView.

- GIF продолжает анимироваться — изображение выводится браузерным движком.
- Масштабирование двумя пальцами (pinch to zoom); увеличенную карту можно двигать в обе стороны.
- Кнопка ⟳ загружает свежую анимацию без HTTP-кеша.
- Полноэкранный интерфейс, без рекламы и сторонних библиотек.

## Установка и сборка

Workflow: **Actions → Build signed BikeMeteo APK**. Каждому новому запуску присваивается увеличенный `versionCode` (`github.run_number`) и версия `0.1.N`. Идентификатор пакета `io.github.zoychikf1.bikemeteo` постоянный.

### Подпись (настроить один раз)

В **Settings → Secrets and variables → Actions → Repository secrets** создать:

1. `ANDROID_KEYSTORE_BASE64` — содержимое постоянного PKCS#12 keystore, закодированное Base64.
2. `ANDROID_SIGNING_PASSWORD` — пароль keystore и закрытого ключа.

Можно **использовать тот же keystore и пароль, что для NMEA Bridge**, тогда исходный псевдоним ключа `nmea_bridge` подойдёт без изменений. Если предпочитаете отдельный ключ, создайте его и задайте **Repository variable** `ANDROID_KEY_ALIAS`, например `bike_meteo`. **Не публикуйте keystore или пароль в репозитории!**

Пример создания собственного ключа на компьютере с установленным JDK:

```bash
keytool -genkeypair -keystore bikemeteo-release.p12 -storetype PKCS12 -alias bike_meteo -keyalg RSA -keysize 3072 -validity 10000
```

Ключ и пароль необходимо **сохранить**: при потере ключа заменить ранее установленное приложение обновлением с новой подписью не получится.

Кодировать keystore для GitHub Secrets: Linux `base64 -w 0 bikemeteo-release.p12`, macOS `base64 < bikemeteo-release.p12 | tr -d '\\n'`, Windows PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("bikemeteo-release.p12"))`.

Без этих двух секретов workflow **не выдаёт неподписанный или временно подписанный APK**, чтобы случайно не нарушить цепочку обновлений.

## Примечания

Для GIF используется `<img>` в локальной HTML-обёртке с мета-тегом viewport, где разрешено масштабирование и перемещение страницы. Отображение GIF зависит от доступности сервера и установленного Android System WebView/Chrome. Работоспособность URL надо проверить на реальном телефоне, поскольку с этой среды внешний адрес не удалось проверить.

Версия по умолчанию при локальной сборке — 0.1.0 (`versionCode=1`); в GitHub Actions значения задаются параметрами Gradle.
