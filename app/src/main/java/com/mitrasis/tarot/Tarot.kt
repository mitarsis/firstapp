package com.mitrasis.tarot

/**
 * Колода. Порядок карт фиксирован: сначала 22 Старших, затем масти
 * (Жезлы, Кубки, Мечи, Пентакли) по 14 рангов. Ровно в этом порядке
 * посчитана матрица векторов в assets/cards.bin — менять нельзя.
 */
data class TarotCard(
    val id: Int,
    val title: String,
    val titleEn: String,
    val arcana: String,
    val light: List<String>,
    val shadow: List<String>
)

data class CardLine(
    val title: String,
    val keyword: String,
    val reversed: Boolean
)

data class CardAnswer(
    val card: TarotCard,
    val reversed: Boolean,
    val probability: Float,
    val runnerUp: Pair<TarotCard, Float>?,
    val topic: String = "",
    val alternatives: List<Pair<TarotCard, Float>> = emptyList(),
    val noAnswer: Boolean = false,
    val variant: Int = 0,
    val summary: String = "",
    val lines: List<CardLine> = emptyList(),
    val epithet: String = "",
    val shared: List<String> = emptyList(),
    val agreement: Float = 0f,
    val genre: Genre = Genre.FORECAST,
    val axis: Axis = Axis.MOVEMENT
)

object Deck {

    private class Major(
        val ru: String,
        val en: String,
        val light: String,
        val shadow: String
    )

    private val majors: List<Major> = listOf(
        Major("Шут", "The Fool", "начало, риск, свобода", "глупость, безрассудство, бегство"),
        Major("Маг", "The Magician", "воля, умение, слово", "манипуляция, пустая поза, обман"),
        Major("Верховная Жрица", "The High Priestess", "тайна, интуиция, молчание", "самообман, скрытность, отрыв от реальности"),
        Major("Императрица", "The Empress", "рост, забота, достаток", "застой, гиперопека, пустая трата"),
        Major("Император", "The Emperor", "порядок, власть, границы", "самодурство, слабость власти, потеря опоры"),
        Major("Иерофант", "The Hierophant", "традиция, правила, наставник", "догма, лицемерие, чужие правила"),
        Major("Влюблённые", "The Lovers", "союз, выбор, притяжение", "разлад, соблазн, навязанный выбор"),
        Major("Колесница", "The Chariot", "победа, напор, контроль", "разгон, потеря руля, спешка"),
        Major("Сила", "Strength", "терпение, мягкая сила, самообладание", "срыв, страх, сила без опоры"),
        Major("Отшельник", "The Hermit", "уединение, поиск, честность", "изоляция, затворничество, бегство от людей"),
        Major("Колесо Фортуны", "Wheel of Fortune", "перемена, цикл, судьба", "застревание, дурная петля, полоса неудач"),
        Major("Справедливость", "Justice", "баланс, расчёт, по заслугам", "перекос, двойные стандарты, долг"),
        Major("Повешенный", "The Hanged Man", "пауза, жертва, смена угла", "бессмысленное терпение, подвешенность, самооправдание"),
        Major("Смерть", "Death", "финал, расчистка, необратимость", "цепляние, затянутый конец, страх перемен"),
        Major("Умеренность", "Temperance", "дозировка, смешение, середина", "перебор, разброд, отсутствие меры"),
        Major("Дьявол", "The Devil", "соблазн, зависимость, сделка", "освобождение, осознание цены, разрыв цепей"),
        Major("Башня", "The Tower", "обвал, правда, снос", "отложенный крах, страх обвала, аварийный ремонт"),
        Major("Звезда", "The Star", "надежда, исцеление, ориентир", "пустые мечты, разочарование, сбитый ориентир"),
        Major("Луна", "The Moon", "туман, страх, искажение", "рассеивание, крах иллюзий, обнажение"),
        Major("Солнце", "The Sun", "ясность, успех, тепло", "самодовольство, выгорание, показуха"),
        Major("Суд", "Judgement", "пробуждение, зов, расплата", "глухота, самобичевание, опоздание"),
        Major("Мир", "The World", "завершение, целостность, круг", "незакрытый гештальт, финиш без радости, застревание")
    )

    private class Suit(
        val ru: String,
        val en: String,
        val domain: String,
        val domainShadow: String
    )

    private val suits: List<Suit> = listOf(
        Suit("Жезлов", "Wands", "воля и действие", "пробуксовка, рассеянная энергия"),
        Suit("Кубков", "Cups", "чувства и связи", "помутнение чувств"),
        Suit("Мечей", "Swords", "мысли и конфликт", "саморазрушение, спор с собой"),
        Suit("Пентаклей", "Pentacles", "деньги, тело, быт", "утечка ресурса")
    )

    private class Rank(
        val ru: String,
        val en: String,
        val stage: String,
        val stageShadow: String
    )

    private val ranks: List<Rank> = listOf(
        Rank("Туз", "Ace", "импульс, начало", "застрявшее начало"),
        Rank("Двойка", "Two", "выбор из двух", "застревание в выборе"),
        Rank("Тройка", "Three", "первый рост", "перекос, кривой рост"),
        Rank("Четвёрка", "Four", "устойчивость", "застой, мнимая стабильность"),
        Rank("Пятёрка", "Five", "трение и потеря", "затяжное трение"),
        Rank("Шестёрка", "Six", "движение, обмен", "откат, движение вспять"),
        Rank("Семёрка", "Seven", "испытание", "бессмысленное испытание"),
        Rank("Восьмёрка", "Eight", "усилие, повтор", "пустое усилие"),
        Rank("Девятка", "Nine", "почти итог", "отложенный итог"),
        Rank("Десятка", "Ten", "предел, результат", "предел терпения"),
        Rank("Паж", "Page", "вестник, ученик", "слухи, неверные вести"),
        Rank("Рыцарь", "Knight", "рывок, напор", "топтание, пробуксовка"),
        Rank("Королева", "Queen", "внутреннее владение", "надлом"),
        Rank("Король", "King", "внешняя власть", "шаткая власть")
    )

    val cards: List<TarotCard> by lazy {
        val list = mutableListOf<TarotCard>()
        var id = 0
        majors.forEach { major ->
            list += TarotCard(
                id = id++,
                title = major.ru,
                titleEn = major.en,
                arcana = "Старший аркан",
                light = major.light.split(", "),
                shadow = major.shadow.split(", ")
            )
        }
        suits.forEach { suit ->
            ranks.forEach { rank ->
                list += TarotCard(
                    id = id++,
                    title = "${rank.ru} ${suit.ru}",
                    titleEn = "${rank.en} of ${suit.en}",
                    arcana = "Младший аркан",
                    light = listOf(suit.domain, rank.stage),
                    shadow = listOf(suit.domainShadow, rank.stageShadow)
                )
            }
        }
        list
    }

    /** 78 меток в порядке, совпадающем с cards.bin. */
    val labelsRu: List<String> by lazy { cards.map { it.title } }

    fun byId(id: Int): TarotCard = cards[id]
}
