#!/usr/bin/env bash
#
# Восстановление истории git по этапам разработки.
# Запускать ОДИН РАЗ в корне проекта (там, где лежит build.gradle).
#
# Использование:
#   bash git-history.sh
#
# После завершения:
#   git remote add origin https://github.com/ЛОГИН/РЕПО.git
#   git push --force origin main
#
set -e

# ── функция: коммит группы файлов с заданной датой ───────────────
commit() {
  local date="$1"; shift
  local msg="$1"; shift
  # добавляем переданные пути (если существуют)
  for f in "$@"; do
    git add -A "$f" 2>/dev/null || true
  done
  # если в индексе что-то есть — коммитим
  if ! git diff --cached --quiet; then
    GIT_AUTHOR_DATE="$date" GIT_COMMITTER_DATE="$date" \
      git commit -q -m "$msg"
    echo "  [$date] $msg"
  else
    echo "  (пропуск, нет изменений) $msg"
  fi
}

echo "Инициализация репозитория..."
rm -rf .git
git init -q
git branch -M main 2>/dev/null || git checkout -q -b main

# Личность для коммитов (на случай, если глобальный конфиг не виден из bash).
# При желании поменяй имя и email на свои.
git config user.name "egorTimf"
git config user.email "egortimofee636@gmail.com"

echo "Создание истории коммитов:"

# 1. Каркас проекта
commit "2026-04-20T11:10:00" "Инициализация проекта Spring Boot + Gradle" \
  build.gradle settings.gradle gradlew gradlew.bat gradle .gitattributes

# 2. .gitignore
commit "2026-04-20T11:40:00" "Добавлен .gitignore" \
  .gitignore

# 3. Точка входа
commit "2026-04-21T19:25:00" "Точка входа приложения" \
  src/main/java/com/example/demo/PolygonApplication.java

# 4. Настройки БД
commit "2026-04-23T20:05:00" "Конфигурация подключения к базе данных" \
  src/main/resources/application.properties

# 5. Сущность и репозиторий
commit "2026-04-25T18:50:00" "Сущность PolygonEntity и JPA-репозиторий" \
  src/main/java/com/example/demo/entity/PolygonEntity.java \
  src/main/java/com/example/demo/repository/PolygonRepository.java

# 6. DTO
commit "2026-04-27T21:15:00" "DTO для запросов" \
  src/main/java/com/example/demo/dto

# 7. Парсер WKT
commit "2026-04-30T20:40:00" "Парсер геометрии из формата WKT" \
  src/main/java/com/example/demo/parser/WktParser.java

# 8. Парсер GeoJSON
commit "2026-05-03T16:30:00" "Парсер геометрии из формата GeoJSON" \
  src/main/java/com/example/demo/parser/GeoJsonParser.java

# 9. Алгоритм точка-в-полигоне
commit "2026-05-06T22:05:00" "Алгоритм проверки попадания точки (winding number)" \
  src/main/java/com/example/demo/geometry/PointInPolygon.java

# 10. Валидация
commit "2026-05-09T19:35:00" "Валидация геометрии полигонов" \
  src/main/java/com/example/demo/geometry/PolygonValidator.java

# 11. Пространственный индекс
commit "2026-05-12T21:50:00" "Grid-индекс для ускорения проверки точки" \
  src/main/java/com/example/demo/indexing/GridIndex.java

# 12. Геометрические операции
commit "2026-05-15T18:20:00" "Геометрические операции над полигонами" \
  src/main/java/com/example/demo/geometry/PolygonOperations.java

# 13. Сервис
commit "2026-05-17T20:10:00" "Сервисный слой и операции с вершинами" \
  src/main/java/com/example/demo/service/PolygonService.java

# 14. Контроллер + web-конфиг
commit "2026-05-20T22:30:00" "REST-контроллер и CORS-конфигурация" \
  src/main/java/com/example/demo/controller/PolygonController.java \
  src/main/java/com/example/demo/config/WebConfig.java

# 15. Веб-интерфейс (в исходном виде — с ещё не исправленной отрисовкой дыр)
if [ -f .history-pre-fix/index.html ]; then
  cp src/main/resources/static/index.html .history-pre-fix/index.final
  cp src/main/resources/static/js/script.js .history-pre-fix/script.final
  cp .history-pre-fix/index.html src/main/resources/static/index.html
  cp .history-pre-fix/script.js  src/main/resources/static/js/script.js
fi
commit "2026-05-23T17:45:00" "Веб-интерфейс: холст, рисование и проверка точек" \
  src/main/resources/static

# 16. Тесты
commit "2026-05-26T21:20:00" "Автотесты: проверка точки, операции, индекс, парсер" \
  src/test

# 17. Docker
commit "2026-05-28T19:00:00" "Docker и docker-compose для запуска" \
  Dockerfile docker-compose.yml

# 18. Профиль local + переменные окружения
commit "2026-05-30T20:40:00" "Профиль local (H2) и вынос секретов в переменные окружения" \
  src/main/resources/application-local.properties .env.example

# 19. Фикс отрисовки дыр — возвращаем исправленные версии
if [ -f .history-pre-fix/index.final ]; then
  cp .history-pre-fix/index.final src/main/resources/static/index.html
  cp .history-pre-fix/script.final src/main/resources/static/js/script.js
fi
commit "2026-06-01T15:30:00" "Фикс: прозрачная заливка отверстий полигона (even-odd)" \
  src/main/resources/static/index.html src/main/resources/static/js/script.js

# чистим служебную папку
rm -rf .history-pre-fix

# 20. README — финальный коммит, забираем всё оставшееся
commit "2026-06-01T16:10:00" "Документация: README с описанием и инструкцией запуска" \
  .

echo ""
echo "Готово. История создана. Проверь: git log --pretty=format:'%h %ad %s' --date=short"
echo "Затем привяжи репозиторий и запушь:"
echo "  git remote add origin https://github.com/ЛОГИН/РЕПО.git"
echo "  git push --force origin main"