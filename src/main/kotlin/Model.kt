data class Performance(
    val id: Int = 0,
    val title: String,
    val url: String,
)

data class Schedule(
    val date: String,
    val time: String,
    val author: String,
    val scene: String,
    val ageRestriction: String,
    val ticketsAvailable: Boolean,
    val minPrice: Int? = null  // минимальная цена билета в рублях, null если не удалось определить
)
