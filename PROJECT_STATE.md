# Состояние проекта Minoga TV Box
**Последнее обновление**: 2026-11-04 | **Модуль общей памяти Antigravity**

---

## 🎯 Текущая цель и описание
**Minoga TV Box** — Приложение для Android TV / Google TV с 10-foot UI интерфейсом, оптимизированным для пульта (D-pad), выполненное в стиле Obsidian Cinematic Glassmorphism.

---

## 📋 Статус и компоненты
- [x] Разработана полная спецификация дизайн-системы (`DESIGN.md`).
- [x] Настроена модульная архитектура проекта (`app/`, `core/`, `feature/`, `server/`, `web/`).
- [x] Интегрированы навыки генерации и адаптации UI-компонентов в `.agents/skills/`.
- [x] **Реализована привязка и загрузка плейлиста и EPG по 4-значному коду через веб-страницу `minogatvbox.duckdns.org`**:
  - В приложении кнопка «🌐 Загрузить с ВЕБ-страницы» открывает диалог `WebCodePairingDialog` с 4-значным кодом.
  - На веб-странице `web/index.html` пользователь вводит этот 4-значный код, после чего открывается форма установки **Названия**, **Ссылки на плейлист** и **Ссылки на EPG**.
  - После нажатия «Установить» на веб-странице приложение на ТВ автоматически принимает данные и загружает каналы и телепрограмму.
- [x] **Выполнена выверка и сборка проекта**:
  - Согласован инструментарий сборки: Gradle 9.0, AGP 8.13.2, Kotlin 2.1.0, KSP 2.1.0-1.0.29.
  - Устранены дублирующиеся оверлоуды DAO и расширены централизованные мапперы entities ↔ domain models.
  - Приложение успешно собрано, развернуто и запущено на эмуляторе Android TV.

---

## 📂 Ключевые файлы (Затронутые в последнем обновлении)
- `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties` — конфигурация инструментария сборки.
- `core/core-data/src/main/kotlin/com/minogatv/box/core/data/mapper/EntityMappers.kt` — централизованные функции маппинга сущностей Room в доменные модели.
- `feature/feature-channels/src/main/kotlin/com/minogatv/box/feature/channels/ChannelListScreen.kt` & `SettingsScreen.kt` — D-pad навигация и элементы интерфейса.

---

## 🚀 Следующие шаги для продолжающей модели
1. Следовать правилам дизайн-системы из `DESIGN.md`.
2. Перед завершением ответа фиксировать прогресс в данном файле `PROJECT_STATE.md`.
3. **Веб-портал развёрнут на Server1 (130.61.183.21, Ubuntu 22.04 ARM64) — https://minogatvbox.duckdns.org ✅**
   - SSH: `ssh -i "C:\Users\User\Desktop\ORACLE CLoud\Keys\ssh-key-2026-09-27.key" ubuntu@130.61.183.21`
   - Повторный деплой: `.\server\deploy\deploy.ps1 -Target ubuntu@130.61.183.21 -Key "C:\Users\User\Desktop\ORACLE CLoud\Keys\ssh-key-2026-09-27.key"`
   - systemd-сервис `minogatvbox` (Java 17, `/opt/minogatvbox`, класс `server.IptvMockServer`, порт 8091 на 127.0.0.1-прокси).
   - Nginx vhost `/etc/nginx/sites-enabled/minogatvbox.duckdns.org` → proxy 127.0.0.1:8091; Let's Encrypt (certbot, автопродление, истекает 2027-02-02); HTTP → 301 на HTTPS. Соседний сайт `foxygift.lv` не затронут.
   - Проверен полный цикл register → verify → submit → poll на живом домене.
   - Приложение: `DEFAULT_WEB_CONFIG_URL = https://minogatvbox.duckdns.org`, `buildCandidateServerBases` сначала пробует HTTPS (301 теряет тело POST).
   - Ограничение: сессии кодов хранятся в памяти (теряются при рестарте сервиса, TTL 30 мин).
   - **Скачивание APK с сайта ✅**: карточка «📥 Скачать приложение» в `web/index.html` (версия/размер из `/download/version.json`).
     - Ссылки: `https://minogatvbox.duckdns.org/download/MinogaTVBox.apk`, короткая `minogatvbox.duckdns.org/apk` (302; работает и по HTTP без редиректа на HTTPS — для старых ТВ-боксов/Downloader).
     - Файлы на сервере: `/var/www/minogatvbox/download/` (раздаёт Nginx напрямую).
     - Публикация новой сборки: `.\gradlew.bat assembleDebug`, затем `.\server\deploy\publish-apk.ps1 -Target ubuntu@130.61.183.21 -Key "C:\Users\User\Desktop\ORACLE CLoud\Keys\ssh-key-2026-09-27.key"`.
     - Nginx: общий сниппет `minogatvbox-common.conf` + `minogatvbox.nginx.ssl.conf` (HTTPS) / `minogatvbox.nginx.conf` (bootstrap до получения сертификата). `remote-install.sh` больше не затирает HTTPS-конфиг.
     - Публикуется **debug-APK** (release не подписан — нет signingConfig). TODO: создать release keystore и публиковать подписанный release; повышать `versionCode` при каждом релизе.
4. **Сенсорное управление (жесты → виртуальный пульт) ✅**
   - `app/src/main/kotlin/com/minogatv/box/input/TouchRemoteController.kt` — перехват касаний в `MainActivity.dispatchTouchEvent`, синтез D-pad KeyEvent через `Activity.dispatchKeyEvent` (тот же путь, что пульт).
   - Двойной тап = OK; удержание = долгое OK (DOWN при long-press, UP при отпускании); свайп влево = ВПРАВО, вправо = ВЛЕВО, вверх = ВНИЗ, вниз = ВВЕРХ; длинный свайп повторяет шаг (до 6); тап двумя пальцами = НАЗАД.
   - Касания главного окна поглощаются (Compose остаётся в режиме клавиатуры, фокус сохраняется). Диалоги (`AlertDialog`/`Dialog`) — отдельные окна, в них работает обычный тач.
   - Переключатель в Настройках: «Управление жестами (сенсорный экран)», pref `touch_gestures_enabled` (по умолчанию вкл.). Отключено в режиме PiP.
   - Требует проверки на реальном сенсорном устройстве.
   - **Исправлено (v1.0.2 — гибридное управление для смартфонов/планшетов и ТВ ✅)**:
     - На смартфоне (Samsung S21) предыдущая реализация поглощала все события касания в `MainActivity.dispatchTouchEvent` (`return true` без `super.dispatchTouchEvent(ev)`), блокируя нажатия по кнопкам/папкам/каналам и скролл списков, а в `InputMode.Touch` и на экранах `EmptyChannelsView`/`FolderGrid` события `DPAD_*` не доходили до `onPreviewKeyEvent`.
     - В `MainActivity.kt` и `TouchRemoteController.kt`: касания передаются в `super.dispatchTouchEvent(ev)` (одиночный тап по кнопкам/папкам/каналам/переключателям и вертикальная прокрутка списков работают нативно), а при срабатывании свайпа или тапа двумя пальцами отправляется `ACTION_CANCEL` для предотвращения ложного клика.
     - Добавлен `ScreenKeyDispatcher` / `LocalScreenKeyDispatcher` (`RemoteControlHintBar.kt`) и прямая маршрутизация в `MainActivity.dispatchKeyEvent`, чтобы `KeyEvent` от жестов и пульта доставлялись в `ChannelListScreen` и `PlayerScreen` независимо от `InputMode.Touch`.
     - В `ChannelListScreen.kt` и `EpgSidePanel.kt`: одиночный тап по невыделенному каналу/передаче выделяет его (запускает превью и EPG), повторный или двойной тап запускает плеер, долгое нажатие открывает контекстное меню, все подсказки нижней панели `RemoteControlHintBar` кликабельны пальцем.
   - **Адаптация под экран смартфона (v1.0.3, `versionCode = 4` ✅)**:
     - **Размер шрифта по умолчанию — `40%` (`ui_scale_percent = 40`)**: в `ChannelListScreen.kt` и `SettingsScreen.kt` (с однократной миграцией `ui_scale_default_40_applied = true`, чтобы при обновлении приложения масштаб автоматически переключался на `40%`).
     - **Полное отображение текущей передачи (время и название) в списке каналов (`ChannelListItem`)**: убраны двойные горизонтальные отступы в `ChannelList`, время текущей передачи (`HH:mm–HH:mm`) зафиксировано с `maxLines = 1, softWrap = false`, а название передачи растянуто через `Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis`.
     - **Уменьшено окно живого превью и уплотнена панель телепрограммы и архива (`StitchLivePreviewCard`, `EpgSidePanel`, `TopBar`, `RemoteControlHintBar`)**: на компактных экранах смартфонов (`screenHeightDp < 500 || screenWidthDp < 750`) высота `StitchLivePreviewCard` уменьшена со `170.dp` до `68.dp` (`52.dp` при открытом описании передачи), шапка `EpgSidePanel` сделана однострочной (`ПРОГРАММА • <Канал>`), а строки передач и архива масштабируются так, чтобы на экране телефона одновременно помещалось 6–8 передач.
   - **Полная адаптация всех окон под вертикальный (портретный) режим смартфона (v1.0.4, `versionCode = 5` ✅)**:
     - **Сетка папок (`FolderGrid` & `KeyEventHandler.kt`)**: в портретном режиме автоматически переключается на 2 колонки (`GridCells.Fixed(2)`), а шаг навигации вверх/вниз в `KeyEventHandler` динамически учитывает 2 колонки.
     - **3-ярусный вертикальный макет каналов и EPG (`ChannelListScreen.kt`)**: в портретном режиме сверху закреплено живое мини-превью `16:9` во всю ширину экрана, под ним расположен переключатель вкладок `📺 Каналы` / `📅 Программа и Архив`, а под ним список каналов или телепрограммы открывается на 100% ширины экрана.
     - **Полноширинная карточка описания передачи (`EpgSidePanel.kt`)**: в портретном режиме `ProgramDetailsCard` открывается на всю ширину панели (вместо деления 35%/65%) с кнопками `◀ К списку передач` и `▶ Смотреть`.
     - **Видеоплеер (`PlayerScreen.kt`)**: двухстрочная адаптивная верхняя панель в портретном режиме + кнопка переключения ориентации (`Альбомный` / `Портретный`) с автосбросом при выходе из плеера.
     - **Шапка, нижняя панель и диалоги (`TopBar`, `RemoteControlHintBar.kt`, `ChannelSearchDialog.kt`, `ExitConfirmDialog.kt`)**: компактные кнопки-иконки в шапке, горизонтальный скролл подсказок и адаптивные отступы диалогов для узких экранов.
   - **Защита от затухания экрана и Пользовательское соглашение при первом запуске (v1.0.5, `versionCode = 6` ✅)**:
     - **Экран не гаснет при просмотре каналов**: в `MainActivity.kt`, `PlayerScreen.kt` и `StitchLivePreviewCard` (`ChannelListScreen.kt`) установлены `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` и `PlayerView.keepScreenOn = true`.
     - **Экран Пользовательского соглашения и Отказа от ответственности (`DisclaimerScreen.kt`, `MainActivity.kt`)**: при первом запуске (`accepted_terms == false` в `minoga_tv_prefs`) отображается полный текст Пользовательского соглашения (4 пункта, «Minoga TV Box») с чекбоксом и кнопками «Я согласен» / «Не согласен (Выйти)». При нажатии «Я согласен» сохраняется `accepted_terms = true`, при отказе или нажатии «Назад» приложение закрывается (`finishAffinity()`).
   - **Фоновая проверка обновлений и установка из Настроек (v1.0.6, `versionCode = 7` ✅)**:
      - **`AppUpdateManager.kt` (`feature/feature-channels/src/main/kotlin/com/minogatv/box/feature/update/AppUpdateManager.kt`)**: при запуске приложения и открытии настроек сверяет локальный `versionCode` с `https://minogatvbox.duckdns.org/download/version.json`.
      - **Деликатное уведомление на главном экране (`ChannelListScreen.kt`)**: если на сервере доступна более новая версия, на кнопке «⚙ Настройки» в `TopBar` загорается зелёный индикатор (`• vX.Y.Z`), а под шапкой появляется компактная закрываемая плашка «Доступно обновление программы — перейдите в Настройки, чтобы установить».
      - **Скачивание и установка из Настроек (`SettingsScreen.kt`, `AndroidManifest.xml`, `res/xml/file_paths.xml`)**: карточка «ОБНОВЛЕНИЕ ПРОГРАММЫ» отображает текущую версию, позволяет вручную проверить обновления, скачивает `MinogaTVBox.apk` с индикатором прогресса (`0..100%`, МБ), проверяет контрольную сумму `SHA-256` и запускает системный установщик Android через `FileProvider` (с автоматическим запросом разрешения `ACTION_MANAGE_UNKNOWN_APP_SOURCES` при необходимости).
