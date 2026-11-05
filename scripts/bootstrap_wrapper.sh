#!/usr/bin/env bash
# Один раз генерирует gradlew, gradlew.bat и gradle-wrapper.jar. Нужен установленный Gradle (>= 8.5) и сеть.
# После этого закоммитьте gradlew*, gradle/wrapper/gradle-wrapper.jar.
set -euo pipefail
cd "$(dirname "$0")/.."
gradle wrapper --gradle-version 8.9 --distribution-type bin
echo "Готово. Проверка: ./gradlew --version"
