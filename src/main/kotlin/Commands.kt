import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode.HTML
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import java.time.format.DateTimeFormatter

fun Dispatcher.perfCommands() {
    command("perfs") {
        val userId = message.from?.id ?: return@command
        val performances = getAllPerformances()
        if (performances.isEmpty()) {
            bot.sendMessage(ChatId.fromId(message.chat.id), "ℹ На данный момент нет доступных спектаклей.")
            return@command
        }

        val buttons = performances.map { (id, title, _) ->
            val subscribed = isUserSubscribedToPerformance(userId, id)
            val label = if (subscribed) "✅ $title" else title
            val callbackData = if (subscribed) "-perf::$id" else "+perf::$id"
            listOf(InlineKeyboardButton.CallbackData(text = label, callbackData = callbackData))
        }

        bot.sendMessage(
            chatId = ChatId.fromId(message.chat.id),
            text = "📜 Выберите спектакли для подписки на уведомления:",
            replyMarkup = InlineKeyboardMarkup.create(buttons)
        )
    }
}

fun Dispatcher.callbackCommands() {
    callbackQuery("perf::") {
        val data = callbackQuery.data
        val userId = callbackQuery.from.id
        val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
        val messageId = callbackQuery.message?.messageId ?: return@callbackQuery
        val firstName = callbackQuery.from.firstName
        val lastName = callbackQuery.from.lastName
        val username = callbackQuery.from.username

        val subscribe = data.startsWith("+perf::")
        val perfId = data.removePrefix("+perf::").removePrefix("-perf::").toIntOrNull() ?: return@callbackQuery

        ensureSubscriberExists(userId, firstName, lastName, username)

        if (subscribe) {
            subscribeUserToPerformance(userId, perfId)
            val perfTitle = getAllPerformances().find { it.first == perfId }?.second ?: ""
            bot.sendMessage(
                ChatId.fromId(chatId),
                "✅ Подписан на <b>$perfTitle</b>.\n💰 Лимит цены: /setprice $perfId 3000",
                parseMode = HTML
            )
        } else {
            unsubscribeUserFromPerformance(userId, perfId)
        }

        // Rebuild keyboard with updated state
        val performances = getAllPerformances()
        val buttons = performances.map { (id, title, _) ->
            val subscribed = isUserSubscribedToPerformance(userId, id)
            val label = if (subscribed) "✅ $title" else title
            val callbackData = if (subscribed) "-perf::$id" else "+perf::$id"
            listOf(InlineKeyboardButton.CallbackData(text = label, callbackData = callbackData))
        }

        bot.editMessageReplyMarkup(
            chatId = ChatId.fromId(chatId),
            messageId = messageId,
            replyMarkup = InlineKeyboardMarkup.create(buttons)
        )
    }
}

fun Dispatcher.statusCommands() {
    command("status") {
        val userId = message.from?.id ?: return@command
        val subscriptions = getUserSubscriptionsWithPrice(userId)

        if (subscriptions.isEmpty()) {
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                "ℹ Вы не подписаны ни на один спектакль.\nИспользуйте /perfs чтобы выбрать спектакли."
            )
        } else {
            val list = subscriptions.joinToString("\n") { (_, title, price) ->
                val priceStr = if (price != null) " — до $price ₽" else ""
                "🎭 $title$priceStr"
            }
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                "✅ Вы подписаны на уведомления о билетах:\n$list"
            )
        }
    }

    command("mysubs") {
        val userId = message.from?.id ?: return@command
        val subscriptions = getUserSubscriptionsWithPrice(userId)

        if (subscriptions.isEmpty()) {
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                "ℹ Вы не подписаны ни на один спектакль.\nИспользуйте /perfs чтобы выбрать спектакли."
            )
        } else {
            val perfUrls = getUserSubscribedPerformances(userId).associate { it.first to it.third }
            val list = subscriptions.joinToString("\n") { (id, title, price) ->
                val priceStr = if (price != null) " — до $price ₽" else ""
                val url = perfUrls[id] ?: ""
                "🎭 <a href=\"$url\">$title</a>$priceStr"
            }
            bot.sendMessage(
                ChatId.fromId(message.chat.id),
                "✅ Ваши подписки:\n$list",
                parseMode = HTML
            )
        }
    }
}

fun Dispatcher.priceCommands() {
    command("setprice") {
        val userId = message.from?.id ?: return@command
        val chatId = ChatId.fromId(message.chat.id)
        val args = message.text?.removePrefix("/setprice")?.trim()?.split("\\s+".toRegex()) ?: emptyList()

        if (args.isEmpty() || args.all { it.isBlank() }) {
            val subs = getUserSubscriptionsWithPrice(userId)
            if (subs.isEmpty()) {
                bot.sendMessage(chatId, "ℹ Вы не подписаны ни на один спектакль.\nИспользуйте /perfs чтобы выбрать спектакли.")
                return@command
            }
            val list = subs.joinToString("\n") { (id, title, price) ->
                val priceStr = if (price != null) "до $price ₽" else "без лимита"
                "🎭 $title (ID: $id) — $priceStr"
            }
            bot.sendMessage(
                chatId,
                "💰 <b>Лимиты цен на ваши подписки:</b>\n\n$list\n\n" +
                        "Чтобы установить лимит: /setprice ID ЦЕНА\n" +
                        "Чтобы снять лимит: /setprice ID 0",
                parseMode = HTML
            )
            return@command
        }

        val perfId = args.getOrNull(0)?.toIntOrNull()
        val priceArg = args.getOrNull(1)?.toIntOrNull()

        if (perfId == null || priceArg == null || priceArg < 0) {
            bot.sendMessage(chatId, "⚠️ Формат: /setprice ID ЦЕНА\nПример: /setprice 12 3000\nСнять лимит: /setprice 12 0")
            return@command
        }

        val subs = getUserSubscriptionsWithPrice(userId)
        val sub = subs.find { it.first == perfId }
        if (sub == null) {
            bot.sendMessage(chatId, "⚠️ Вы не подписаны на спектакль с ID $perfId.\nПосмотрите свои подписки: /setprice")
            return@command
        }

        val newPrice = if (priceArg == 0) null else priceArg
        setSubscriptionMaxPrice(userId, perfId, newPrice)

        if (newPrice != null) {
            bot.sendMessage(chatId, "✅ Лимит для <b>${sub.second}</b>: до <b>$newPrice ₽</b>.\n\nЕсли цена не указана на сайте — уведомлю в любом случае.", parseMode = HTML)
        } else {
            bot.sendMessage(chatId, "✅ Лимит для <b>${sub.second}</b> снят. Буду уведомлять о любых доступных билетах.", parseMode = HTML)
        }
    }
}

fun Dispatcher.adminCommands() {
    command("subs") {
        val details = getPerformanceSubscriptionDetails()
        if (details.isEmpty()) {
            bot.sendMessage(ChatId.fromId(message.chat.id), "ℹ Пока нет подписок на спектакли.")
            return@command
        }

        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val grouped = details.groupBy { it.performanceTitle }

        val text = buildString {
            append("📊 <b>Подписки на спектакли:</b>\n")
            grouped.forEach { (title, subscribers) ->
                append("\n🎭 <b>$title</b> (${subscribers.size}):\n")
                subscribers.forEach { sub ->
                    val userRef = if (sub.username != null) "@${sub.username}" else sub.subscriberName
                    append("  • $userRef — с ${sub.subscriptionDate.format(dateFormatter)} [${sub.notificationCount} увед.]\n")
                }
            }
        }

        bot.sendMessage(
            ChatId.fromId(message.chat.id),
            text,
            parseMode = HTML
        )
    }
}

fun Dispatcher.statsCommands() {
    command("stats") {
        val stats = getSubscriptionStats()
        val allSubscribers = getAllSubscribersForStats()
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

        val statsText = buildString {
            append("📊 <b>Статистика подписчиков:</b>\n\n")
            append("👥 Всего пользователей: ${stats.totalSubscribers}\n")
            append("✅ Активных: ${stats.activeSubscribers}\n")
            append("❌ Отписавшихся: ${stats.inactiveSubscribers}\n\n")

            if (stats.inactiveSubscribers > 0) {
                append("<b>Отписавшиеся пользователи:</b>\n")
                allSubscribers.filter { !it.isActive }.forEach { sub ->
                    val unsubDate = sub.unsubscriptionDate?.format(dateFormatter) ?: "неизвестно"
                    append("• ${sub.fullName} - отписан $unsubDate\n")
                }
            }
        }

        bot.sendMessage(
            ChatId.fromId(message.chat.id),
            statsText,
            parseMode = HTML
        )
    }
}
