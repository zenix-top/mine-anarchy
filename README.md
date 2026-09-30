# AnarchyCore — профессиональный сервер-анархия (Paper 1.16.5)

Кастомный плагин для сервера анархии в стиле 2b2t: PvP/PvE в духе 1.8,
буры для копания и многостраничные сундуки.

## Возможности

- **PvE/PvP как на 1.8** — кастомная боевая система с таймером атаки,
  замедленной регенерацией после урона и механиками кристалл-ПвП.
- **Буры (Drills)** — 3 уровня, работают на лаве как топливе,
  несколько режимов копания, крафт и починка.
- **Многостраничные сундуки** — до 9 страниц (162 слота) в одном сундуке,
  GUI-навигация, сохранение содержимого при поломке.
- **Анархия-механики** — /spawn, /home, вечный мир, настройки под server-anarchy.

## Структура репозитория

```
anarchy/plugins/AnarchyCore/   — исходники плагина (Maven)
├── pom.xml
└── src/main/java/ru/anarchy/core/
    ├── AnarchyCore.java       — точка входа
    ├── PveManager.java        — боёвка 1.8 + реген
    ├── DrillManager.java      — буры
    ├── ChestManager.java      — многостраничные сундуки
    ├── AnarchyManager.java    — спавн/мир/ивенты
    ├── CoreCommands.java      — команды
    └── DataStore.java         — хранение данных
```

## Сборка

Требования: **JDK 16+** и **Maven 3.6+**.

```bash
mvn -f anarchy/plugins/AnarchyCore/pom.xml clean package
```

Готовый файл: `anarchy/plugins/AnarchyCore/target/AnarchyCore-1.0.0.jar`

## Установка

1. Скачайте **Paper 1.16.5 (build 794)**: https://papermc.io/legacy → выберите 1.16.5 build 794.
2. Создайте папку сервера, положите туда `paper-1.16.5-794.jar` и примите EULA (`eula.txt` → `eula=true`).
3. Скопируйте собранный `AnarchyCore-1.0.0.jar` в папку `plugins/`.
4. Запуск (на JDK 17 нужны обходные флаги для легаси-Paper):

```bash
java -Xms2G -Xmx4G \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  -jar paper-1.16.5-794.jar nogui
```

На JDK 11/16 сервер запускается обычным `java -jar ... nogui`.

## Конфигурация

Все настройки плагина — в `plugins/AnarchyCore/config.yml`
(тайминги боя, параметры буров, страницы сундуков, радиус спавна).

## Примечание

Бинарные файлы (Paper, world-данные, сборки) намеренно исключены через `.gitignore` —
репозиторий содержит только исходники.

## Лицензия

GPL-3.0 (см. LICENSE). Paper API распространяется по собственным лицензиям.
