# KIA Sorento bridge — заметки по проекту

Краткая памятка для быстрого погружения (в т.ч. для нового чата с LLM).
Описание кода и история решений — здесь, чтобы не пересказывать это заново.

## Что делает приложение

Мобильное приложение-«мост» для автомобиля. Оно:

1. Захватывает экран телефона (MediaProjection).
2. Распознаёт элементы навигатора (Яндекс Навигатор / Mapcam) — стрелки
   поворотов, ограничения скорости, камеры — через OpenCV (template matching)
   и Tesseract (OCR).
3. Отправляет результат по Bluetooth SPP на контроллер HC-05, подключённый к
   проекционному дисплею (HUD) в машине.

## Структура проекта

Gradle-проект `KIA_YaNav_Bridge`, два подпроекта:

| Модуль | Тип | Назначение |
|---|---|---|
| `:app` | Android application | Основное приложение |
| `:sdk` | Android library | OpenCV 4.10.0 (исходный модуль OpenCV Android SDK) |

### Product flavors (`app/build.gradle.kts`)

Все flavor'ы используют **один и тот же код**, отличаются только `applicationId`,
названием и иконкой. Сделано, чтобы версии можно было ставить рядом на телефон.

| Flavor | applicationId | Название | Иконка |
|---|---|---|---|
| `original` | `com.example.myapplication5` | KIA Sorento bridge | `ic_launcher` (стандартная) |
| `kia3` | `com.example.myapplication5.kia3` | KIA Sorento bridge 3 | `ic_launcher_kia3` (бирюзовая) |

Различия задаются в `build.gradle.kts` через `applicationIdSuffix`,
`resValue("string","app_name",...)` и `manifestPlaceholders["appIcon"]`.
Третий flavor `kia2` был **удалён** как ненужный.

> Рабочая версия — **`kia3`**. Именно она стоит на телефоне и дорабатывается.

## Сборка и установка

```powershell
# сборка
gradlew.bat :app:assembleKia3Debug
gradlew.bat :app:assembleOriginalDebug

# установка на подключённый телефон
adb install -r app\build\outputs\apk\kia3\debug\app-kia3-debug.apk

# запуск
adb shell am start -n com.example.myapplication5.kia3/com.example.myapplication5.MainActivity
```

APK лежат в `app/build/outputs/apk/<flavor>/debug/`.

## Ключевые настройки SDK

| Параметр | Значение | Почему |
|---|---|---|
| `compileSdk` | 34 | Требуют AndroidX-библиотеки |
| `minSdk` | 29 | Нужен Android 10+ |
| `targetSdk` | **29** | Специально занижен: сохраняет старый доступ к памяти |
| `requestLegacyExternalStorage` | `true` | См. ниже |

### ⚠️ Важно: доступ к файлам и Android 11+

Приложение читает абсолютные пути `/storage/emulated/0/...`:

- шаблоны: `/storage/emulated/0/Pictures/Screenshots/*.png`
- данные Tesseract: `/storage/emulated/0/tessdata/` (`rus.traineddata`)

Чтобы это работало на Android 10, в манифесте включён
`android:requestLegacyExternalStorage="true"`, а `targetSdk` занижен до 29.

> [!IMPORTANT]
> На **Android 11+** флаг `requestLegacyExternalStorage` игнорируется, и доступ по
> абсолютным путям пропадёт. Если телефон сменится на Android 11+, нужно будет
> либо запрашивать `MANAGE_EXTERNAL_STORAGE` («Доступ ко всем файлам»), либо
> переносить шаблоны и `tessdata` в приватную папку приложения.

## Компоненты приложения

| Класс | Роль |
|---|---|
| `MainActivity` | Экран, захват экрана, распознавание, Bluetooth-логика |
| `ForegroundService` | Foreground-сервис (обязателен при MediaProjection) |
| `NfcLaunchActivity` | Невидимая точка входа для NFC-метки (см. ниже) |

## NFC-запуск по метке

Запуск приложения касанием NFC-метки в держателе телефона.

| Параметр | Значение | Где |
|---|---|---|
| NDEF URI метки | `kia-bridge://go` | `MainActivity.NFC_TRIGGER_URI` |
| Запасная метка (билет) | UID `041B889A201D90` | `MainActivity.TARGET_NFC_UID` |

- Основной способ — метка **NTAG215**, на которую записан URI `kia-bridge://go`
  (запись типа **«URL / URI»**, а не «Запуск приложения»!).
  Схема уникальна, поэтому система запускает приложение без диалога выбора.
- Запасной способ — транспортная карта MIFARE Classic (по UID). На неё также
  реагируют HUAWEI Wallet и Яндекс Метро, поэтому появляется выбор приложения.

### Архитектурное решение по NFC

Фильтры `NDEF_DISCOVERED` / `TECH_DISCOVERED` объявлены на **`NfcLaunchActivity`**,
а не на `MainActivity`. Причина: метка в поле зрения срабатывает многократно, и
если интент приходит в `MainActivity`, она выходит на передний план и **сбрасывает
системный диалог разрешения записи экрана**.

`NfcLaunchActivity` ничего не делает, если сессия уже идёт
(`MainActivity.isSessionBusy()`), и запускает `MainActivity` с
`EXTRA_AUTO_START` только для первой — «холодной» — активации.

## Bluetooth HC-05

| Параметр | Значение |
|---|---|
| MAC-адрес | `20:15:04:14:00:91` |
| SPP UUID | `00001101-0000-1000-8000-00805F9B34FB` |
| Период опроса | 3000 мс |
| Таймаут автовыключения | 30 минут |

Поведение (реализовано «сторожем» в отдельном потоке):

- HC-05 **пропал** → захват приостанавливается (VirtualDisplay отпускается, но
  `MediaProjection` **сохраняется**, чтобы не требовать повторного разрешения),
  распознавание и передача останавливаются.
- HC-05 **появился** → автоматическое переподключение и возобновление работы.
- HC-05 **не найден 30 минут** при активной сессии → приложение выключается
  полностью (стоп захвата, сервиса, сокета, Tesseract; завершение процесса).

`MediaProjection` намеренно не уничтожается при паузе — иначе возобновление
требовало бы нового подтверждения пользователя.

## Автозапуск захвата

- Запуск **с метки** → автостарт сессии (запрос разрешения экрана выводится сразу).
- Запуск **с иконки** → автостарта нет, ждёт нажатия START.
- Автостарт выполняется в `onResume()`, а не в `onCreate`: запрос
  `MediaProjection` требует, чтобы активити уже была в фоне-фокусе.

> [!NOTE]
> Системный диалог «Разрешить запись экрана» показывается **всегда** — это защита
> Android, обойти нельзя. То есть один тап «РАЗРЕШИТЬ» на сессию остаётся.

## Экран (UI)

- Material 3, светлая тема (в т.ч. в dark mode — чтобы не «переключалось» в машине).
- Секции: карточка «РАСПОЗНАВАНИЕ» (4 чекбокса) и «АВТОЗАПУСК» (2 чекбокса),
  крупная кнопка START/STOP, строка состояния HC-05.
- Тема переопределена с `Theme.AppCompat` на Material 3 — иначе `MaterialCardView`
  падает в рантайме.
- ID элементов в `activity_main.xml` **связаны с кодом**, менять осторожно:
  `checkBox_speed`, `checkBox_turns`, `checkBox_speed_cams_yandex`,
  `checkBox_speedmapcams`, `checkBox_start_ya_navi`, `checkBox_start_mapcam`,
  `toggle` (обязательно `ToggleButton`), `btStatusText`, `btStatusDot`.

## Известные ограничения

- Windows-разработка; `gradlew.bat`.
- Логика распознавания — «хрупкая»: содержит защитные проверки на пустой результат
  OCR и ненайденные шаблоны (иначе кадр падал и передача прекращалась целиком).
- `targetSdk 29` не пройдёт требования Google Play (≥ 33) — публикация не планируется,
  приложение ставится напрямую.

## Git

- Репозиторий: https://github.com/AK-48-bit/KIA_YaNav_Bridge (ветка `master`).
- `.idea/` и артефакты сборки не отслеживаются (см. `.gitignore`).
- Автор коммитов: `AK-48-bit <AK-48-bit@users.noreply.github.com>`.
