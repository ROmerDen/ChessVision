# ChessVision (этап 1: Stockfish/UCI)

Статус: **код написан, но НЕ собирался и НЕ запускался** (в среде создания не было Gradle, Android SDK/NDK, устройства).
Первая проверка — ваша: см. шаги ниже.

## Что внутри
- `engine/` — UCI-модуль: `EngineConfig`, `UciTransport` → `ProcessUciTransport`, `UciParser`, `FenValidator`, `UciEngine` (MultiPV=3).
- `chess/` — собственная шахматная модель: `Position` (строгий FEN, легальные ходы), `GameState` (история → FEN, сопоставление распознанной расстановки с ходом).
- `analysis/` — безопасные пределы настроек (`EngineLimits`), учебная классификация хода (`MoveClassifier`).
- `overlay/` — чистая геометрия стрелок и клеток (без Android-зависимостей, покрыта JVM-тестами).
- `ui/` — Compose-экран: демо-доска со стрелками, FEN, статус, настройки.
- Тесты: JVM (парсер, FEN, движок на скриптованном транспорте, транспорт на shell-скриптах, перфт, история партии, геометрия) и instrumented с реальным Stockfish.

## Архитектурное решение
Stockfish запускается **отдельным процессом** (`libstockfish.so` — исполняемый файл в `jniLibs/arm64-v8a`,
запуск из `nativeLibraryDir`). Это вместо JNI-вызова `main()`: stdin/stdout процесса глобальны, а Stockfish
вызывает `exit` на `quit`, поэтому in-process JNI хрупок (нельзя перезапустить, падение движка роняет приложение).
`UciTransport` — интерфейс: JNI-реализацию можно добавить позже без изменений `UciEngine`.

## Сборка и проверка
1. Откройте папку в Android Studio (JDK 17). Один раз создайте wrapper: `scripts/bootstrap_wrapper.sh`
   (нужен установленный Gradle ≥ 8.5), закоммитьте `gradlew*` и `gradle/wrapper/gradle-wrapper.jar`.
   Версии зависимостей — в `gradle/libs.versions.toml`; проверьте их актуальность.
2. JVM-тесты (без NDK и устройства): `./gradlew :app:testDebugUnitTest`. Сначала смотрите перфт-тесты в `PositionTest`:
   они проверяют генератор ходов. Тесты `ProcessUciTransportTest` используют `/bin/sh` (Linux/macOS).
3. Stockfish: `ANDROID_NDK_HOME=… scripts/build_stockfish.sh` (NDK r26+, нужны curl/wget для `make net`).
   Скрипт делает `make net` → `make build ARCH=armv8 COMP=ndk`, проверяет, что результат — AArch64 без `libc++_shared`
   и с выравниванием ≥ 16 КБ, и кладёт `libstockfish.so` в `jniLibs/arm64-v8a`. `SMOKE_ADB=1` дополнительно прогоняет
   `uci/isready` на устройстве по adb. Обновить Stockfish: `STOCKFISH_TAG=<тег>`, удалив `third_party/Stockfish`.
4. Реальное устройство arm64 (эмулятор x86_64 не подойдёт): `./gradlew :app:connectedDebugAndroidTest`, затем запуск
   приложения: на стартовой позиции — три стрелки (зелёная, синяя, фиолетовая) и три строки с оценкой (с точки зрения белых).
5. Ошибки поиска/запуска движка показываются в UI.

См. `docs/LICENSING.md`. Полный список недостающих задач и критерии готовности целевого приложения: `docs/ROADMAP_TO_FULL_APP.md`.
