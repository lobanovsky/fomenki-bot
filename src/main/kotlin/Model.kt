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
    val ticketsAvailable: Boolean
)
