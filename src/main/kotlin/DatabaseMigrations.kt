import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DatabaseMigrations {

    fun runAllMigrations() {
        migration_001_AddSubscriptionDate()
        migration_002_AddSubscriptionStatusFields()
        migration_003_AddSubscribedAtToUserPerformanceSubscriptions()
        migration_004_AddNotificationCountToSubscribers()
        migration_005_RemoveNonPerformanceEntries()
        migration_006_AddMaxPriceToSubscribers()
        migration_007_AddMaxPriceToUserPerformanceSubscriptions()
    }

    /**
     * Миграция #001: Добавление поля subscription_date в таблицу Subscribers
     */
    private fun migration_001_AddSubscriptionDate() {
        transaction {
            try {
                val defaultDate = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

                exec("ALTER TABLE Subscribers ADD COLUMN subscription_date TEXT NOT NULL DEFAULT '$defaultDate'")
                println("✅ Миграция #001: Колонка subscription_date успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #001: Колонка subscription_date уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #001: Ошибка - ${e.message}")
                    throw e
                }
            }
        }
    }

    /**
     * Миграция #002: Добавление полей is_active и unsubscription_date для soft delete
     */
    private fun migration_002_AddSubscriptionStatusFields() {
        transaction {
            try {
                exec("ALTER TABLE Subscribers ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1")
                println("✅ Миграция #002: Колонка is_active успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #002: Колонка is_active уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #002: Ошибка при добавлении is_active - ${e.message}")
                }
            }

            try {
                exec("ALTER TABLE Subscribers ADD COLUMN unsubscription_date TEXT")
                println("✅ Миграция #002: Колонка unsubscription_date успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #002: Колонка unsubscription_date уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #002: Ошибка при добавлении unsubscription_date - ${e.message}")
                }
            }
        }
    }

    /**
     * Миграция #003: Добавление поля subscribed_at в таблицу UserPerformanceSubscriptions
     */
    private fun migration_003_AddSubscribedAtToUserPerformanceSubscriptions() {
        transaction {
            try {
                val defaultDate = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

                exec("ALTER TABLE UserPerformanceSubscriptions ADD COLUMN subscribed_at TEXT NOT NULL DEFAULT '$defaultDate'")
                println("✅ Миграция #003: Колонка subscribed_at успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #003: Колонка subscribed_at уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #003: Ошибка - ${e.message}")
                    throw e
                }
            }
        }
    }

    /**
     * Миграция #005: Удаление некорректных записей спектаклей (навигационных ссылок)
     */
    private fun migration_005_RemoveNonPerformanceEntries() {
        transaction {
            exec("""
                DELETE FROM UserPerformanceSubscriptions
                WHERE performance_id IN (
                    SELECT id FROM Performances
                    WHERE url NOT LIKE 'https://fomenki.ru/performance/%/%'
                )
            """)
            exec("""
                DELETE FROM Performances
                WHERE url NOT LIKE 'https://fomenki.ru/performance/%/%'
            """)
            println("✅ Миграция #005: Удалены некорректные записи спектаклей")
        }
    }

    /**
     * Миграция #006: Добавление поля max_price в таблицу Subscribers (устарела — поле не используется).
     * Оставлена для совместимости с уже применёнными миграциями.
     */
    private fun migration_006_AddMaxPriceToSubscribers() {
        transaction {
            try {
                exec("ALTER TABLE Subscribers ADD COLUMN max_price INTEGER")
                println("✅ Миграция #006: Колонка max_price в Subscribers добавлена (не используется)")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #006: Колонка max_price в Subscribers уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #006: Ошибка - ${e.message}")
                    throw e
                }
            }
        }
    }

    /**
     * Миграция #007: Добавление поля max_price в таблицу UserPerformanceSubscriptions (лимит цены на подписку)
     */
    private fun migration_007_AddMaxPriceToUserPerformanceSubscriptions() {
        transaction {
            try {
                exec("ALTER TABLE UserPerformanceSubscriptions ADD COLUMN max_price INTEGER")
                println("✅ Миграция #007: Колонка max_price в UserPerformanceSubscriptions добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #007: Колонка max_price уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #007: Ошибка - ${e.message}")
                    throw e
                }
            }
        }
    }

    /**
     * Миграция #004: Добавление поля notification_count в таблицу Subscribers
     */
    private fun migration_004_AddNotificationCountToSubscribers() {
        transaction {
            try {
                exec("ALTER TABLE Subscribers ADD COLUMN notification_count INTEGER NOT NULL DEFAULT 0")
                println("✅ Миграция #004: Колонка notification_count успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #004: Колонка notification_count уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #004: Ошибка - ${e.message}")
                    throw e
                }
            }
        }
    }
}
