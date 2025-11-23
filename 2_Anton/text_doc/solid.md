# Применение принципов SOLID

Этот документ демонстрирует, как принципы SOLID были применены в различных модулях проекта для создания гибкой и поддерживаемой архитектуры.

## Модуль: `domain`

-   **S (Single Responsibility):** Класс `Account` отвечает только за управление состоянием счета. Класс `User` — только за хранение данных пользователя.
-   **O (Open/Closed):** Классы закрыты для модификации, но открыты для расширения. Новую логику (например, комиссии) можно добавить в сервисах, использующих эти доменные объекты, не меняя сами объекты.
-   **D (Dependency Inversion):** `User` не зависит от класса `Account`, а хранит `List<UUID>`. Это инвертирует зависимость: не домен решает, как загружать счета, а слой приложения.

## Модуль: `core-engine`

-   **S (Single Responsibility):** Каждый класс `Action` (`DepositAction`, `WithdrawAction`) отвечает за одну бизнес-операцию. Каждый `Consumer` в Disruptor (`BusinessLogicConsumer`, `JournalingConsumer`) отвечает за один этап конвейера.
-   **O (Open/Closed):** Для добавления нового типа транзакции достаточно создать новый класс `Action` и зарегистрировать его в фабрике. Код конвейера при этом не меняется.
-   **L (Liskov Substitution):** Все классы `Action` реализуют общие интерфейсы (`SingleAccountAction`, `TransferAction`), что позволяет `BusinessLogicConsumer` работать с ними единообразно.
-   **I (Interface Segregation):** Интерфейсы разделены: `SingleAccountAction` для операций с одним счетом, `TransferAction` — для операций с двумя. Классам не нужно реализовывать лишние методы.
-   **D (Dependency Inversion):** Модуль зависит от абстракций (портов) — `BatchPersister`, `JournalingService`, — а не от конкретных реализаций для работы с БД.

## Модуль: `persistence`

-   **S (Single Responsibility):** `AccountMapper` только преобразует данные. `JdbcAccountRepository` только загружает счета. `JdbcJournalRepository` только пишет в журнал.
-   **O (Open/Closed):** Модуль реализует (расширяет) интерфейсы из `core`, не изменяя их. Если мы захотим перейти на JPA, мы создадим новый модуль-адаптер, не трогая `core`.
-   **I (Interface Segregation):** Интерфейсы, которые реализует модуль (`AccountRepository`, `BatchPersister`), очень сфокусированы.
-   **D (Dependency Inversion):** `core` (высокоуровневый модуль) не зависит от `persistence` (низкоуровневый). Оба зависят от абстракций (интерфейсов), которые принадлежат `core`.

## Модуль: `application`

-   **S (Single Responsibility):** `OutboxPoller` только опрашивает БД и публикует команды. `IdempotencyCheckConsumer` только проверяет идемпотентность.
-   **O (Open/Closed):** Конвейер Disruptor легко расширить, добавив новые `Consumer` в цепочку, не изменяя существующие.
-   **I (Interface Segregation):** Порты (`ProcessedTransactionRepository`, `TransactionalOutboxRepository`) специфичны и не перегружены лишними методами.
-   **D (Dependency Inversion):** `BankApplication` зависит от абстракций (`TransactionalOutboxRepository`, `BatchPersister`), а конкретные реализации внедряются извне.

## Модуль: `api`

-   **S (Single Responsibility):** `ApiTransactionService` только ставит команды в очередь. `AdminService` — только для админ-задач. `QueryService` — только для чтения данных.
-   **O (Open/Closed):** Можно легко добавить новый тип транзакции, добавив метод в `ApiTransactionService`, не меняя существующую логику.
-   **I (Interface Segregation):** Функциональность четко разделена между сервисами. Клиенту, которому нужно только читать данные, не нужно знать о методах изменения состояния.

## Модуль: `ui`

-   **S (Single Responsibility):** В GUI-части: `ViewBuilder` только создает компоненты. `MainController` отвечает за логику UI. `SessionContext` — за хранение состояния сессии CLI.
-   **O (Open/Closed):** Можно легко создать новую реализацию `ServerConnection` (например, для REST API), и ни один класс UI не потребует изменений.
-   **L (Liskov Substitution):** Любая реализация `ServerConnection` может быть подставлена вместо базового типа без нарушения работы программы.
-   **D (Dependency Inversion):** Высокоуровневые компоненты (`MainController`, `ConsoleRunner`) зависят от абстракции `ServerConnection`, а не от конкретной реализации `DirectAdapter`.
