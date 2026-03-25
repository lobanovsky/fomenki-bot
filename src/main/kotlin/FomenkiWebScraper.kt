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

            // Performance cards are <a href="slug/" title="Full Title"> anchors inside <main>.
            // Navigation links have absolute hrefs starting with "/" (e.g. "/timetable/"),
            // while performance slugs are relative (e.g. "4-25/", "aboutlove/").
            val cards = doc.select("main a[href][title]").filter { el ->
                val href = el.attr("href")
                val title = el.attr("title").trim()
                title.isNotEmpty() && !href.startsWith("/") && !href.startsWith("http") && !href.startsWith("#") && href.isNotBlank()
            }

            // Deduplicate by URL
            val seen = mutableSetOf<String>()
            for (card in cards) {
                val title = card.attr("title")
                    .replace("\u00AD", "")
                    .replace("\u00A0", " ")
                    .trim()
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
            addArguments("--headless=new")
            addArguments("--disable-gpu")
            addArguments("--no-sandbox")
            addArguments("--disable-setuid-sandbox")
            addArguments("--disable-dev-shm-usage")
            addArguments("--no-zygote")
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

            // fomenki.ru schedule structure (confirmed from live site):
            //   <div class="events">
            //     <div class="event">
            //       <p class="date">7 мая, 19:00</p>
            //       <p class="tickets">
            //         <a href="/boxoffice/#XXXXXXXX" title="Купить билет" class="btn lot-of">Купить билет</a>
            //       </p>
            //     </div>
            //     ...
            //   </div>
            // When no tickets: the <a title="Купить билет"> is absent from <p class="tickets">.

            val scheduleBlocks = doc.select("div.event")

            for (block in scheduleBlocks) {
                val dateText = block.selectFirst("p.date")?.text()?.trim() ?: continue
                val ticketsAvailable = block.select("a[title=Купить билет], a[href*=/boxoffice/]").isNotEmpty()
                schedules.add(Schedule(
                    date = dateText,
                    time = "",
                    author = "",
                    scene = "",
                    ageRestriction = "",
                    ticketsAvailable = ticketsAvailable
                ))
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
