import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions

object FomenkiWebScraper {

    private const val BASE_URL = "https://fomenki.ru"
    private const val REPERTOIRE_URL = "$BASE_URL/performance/"

    private fun parseRepertoire(): List<Performance> {
        val performances = mutableListOf<Performance>()

        try {
            val doc: Document = Jsoup.connect(REPERTOIRE_URL)
                .userAgent("Mozilla/5.0 (compatible; bot)")
                .get()

            // Performance cards are <a href="slug/" title="Full Title"> anchors.
            // Filter out navigation/service links: must have a non-empty title and
            // an href that looks like a relative slug (no scheme, no empty string).
            val cards = doc.select("a[href][title]").filter { el ->
                val href = el.attr("href")
                val title = el.attr("title").trim()
                title.isNotEmpty() && !href.startsWith("http") && !href.startsWith("#") && href.isNotBlank()
            }

            // Deduplicate by URL
            val seen = mutableSetOf<String>()
            for (card in cards) {
                val title = card.attr("title").trim()
                val href = card.attr("href").trim()

                // Resolve relative href against the repertoire base URL
                val url = if (href.startsWith("/")) "$BASE_URL$href" else "$REPERTOIRE_URL$href"

                if (seen.add(url)) {
                    performances.add(Performance(title = title, url = url))
                }
            }

            logger().info("Найдено ${performances.size} спектаклей на fomenki.ru")
        } catch (e: Exception) {
            logger().error("Ошибка при парсинге страницы репертуара: ${e.message}")
        }

        return performances
    }

    fun updatePerformancesDatabase() {
        val performances = parseRepertoire()

        if (performances.isNotEmpty()) {
            for (perf in performances) {
                upsertPerformance(perf.title, perf.url)
            }
            logger().info("База данных спектаклей обновлена")
        } else {
            logger().warn("Не удалось получить список спектаклей")
        }
    }

    private fun fetchHtmlWithSelenium(url: String): String? {
        val chromedriverPath = System.getenv("CHROMEDRIVER_PATH")
        if (!chromedriverPath.isNullOrBlank()) {
            System.setProperty("webdriver.chrome.driver", chromedriverPath)
        }

        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
        }
        val driver: WebDriver = ChromeDriver(options)
        return try {
            driver[url]
            driver.pageSource
        } finally {
            driver.quit()
        }
    }

    fun scrapeSchedule(performance: Performance): List<Schedule> {
        val schedules = mutableListOf<Schedule>()

        try {
            val html = fetchHtmlWithSelenium(performance.url)
            if (html == null) {
                logger().error("Не удалось загрузить страницу ${performance.url}")
                return schedules
            }

            val doc: Document = Jsoup.parse(html)

            // Log a short HTML snippet on first call to aid selector debugging
            logger().debug("HTML snippet [${performance.title}]: ${html.take(2000)}")

            // fomenki.ru schedule structure (observed):
            //   Each show date is in a <p> with text like "30 марта, 19:00"
            //   A "Купить билет" anchor (<a href="/boxoffice/#XXXXXXXX">) in the same
            //   parent block indicates tickets are available.
            //
            // Strategy: find all elements that contain a date/time pattern, then check
            // whether a sibling or nearby element contains "Купить билет".
            //
            // We look for the closest common container that groups date + buy-link.
            // Try several selectors in order of specificity.

            val scheduleBlocks = doc.select(".schedule__item, .afisha__item, .performance-schedule__item, .concert-item")

            if (scheduleBlocks.isNotEmpty()) {
                // Structured blocks found — extract date and ticket link from each
                for (block in scheduleBlocks) {
                    val dateText = block.select("p, .date, .schedule-date, time").firstOrNull()?.text()?.trim() ?: continue
                    val ticketsAvailable = block.select("a:contains(Купить билет), a:contains(Билет)").isNotEmpty()
                    schedules.add(Schedule(
                        date = dateText,
                        time = "",
                        author = "",
                        scene = "",
                        ageRestriction = "",
                        ticketsAvailable = ticketsAvailable
                    ))
                }
            } else {
                // Fallback: scan all <p> tags for date/time pattern and check adjacent siblings
                // for a buy-link within the same parent
                val paragraphs = doc.select("p")
                val dateTimePattern = Regex("""\d{1,2}\s+\w+,\s*\d{2}:\d{2}""")

                for (p in paragraphs) {
                    val text = p.text().trim()
                    if (!dateTimePattern.containsMatchIn(text)) continue

                    // Check the parent container for a "Купить билет" link
                    val parent = p.parent() ?: continue
                    val ticketsAvailable = parent.select("a:contains(Купить билет), a:contains(Билет)").isNotEmpty()

                    schedules.add(Schedule(
                        date = text,
                        time = "",
                        author = "",
                        scene = "",
                        ageRestriction = "",
                        ticketsAvailable = ticketsAvailable
                    ))
                }
            }

            if (schedules.isEmpty()) {
                logger().warn("[${performance.title}] Расписание не найдено. Возможно, нужно скорректировать CSS-селекторы.")
            }

        } catch (e: Exception) {
            logger().error("Ошибка при парсинге расписания [${performance.title}]: ${e.message}")
        }

        schedules.forEach {
            logger().info("[${performance.title}] Дата: ${it.date}, Билеты: ${it.ticketsAvailable}")
        }

        return schedules
    }
}
