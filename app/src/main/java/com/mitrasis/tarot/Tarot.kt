package com.mitrasis.tarot

/**
 * Карта — это метка классификатора. Вопрос пользователя подаётся модели,
 * модель возвращает распределение вероятностей по 78 меткам, верхняя — ответ.
 * Никакого «гадания»: только argmax и уверенность.
 */
data class TarotCard(
    val id: Int,
    val title: String,
    val titleEn: String,
    val arcana: String,
    val light: List<String>,
    val shadow: List<String>
)

data class CardAnswer(
    val card: TarotCard,
    val reversed: Boolean,
    val probability: Float,
    val runnerUp: Pair<TarotCard, Float>?,
    val topic: String = ""
)

object Deck {

    private val majors: List<Triple<String, String, String>> = listOf(
        Triple("Шут", "The Fool", "начало без гарантий|прыжок в неизвестное|свобода от плана"),
        Triple("Маг", "The Magician", "воля|инструменты в руках|слово работает"),
        Triple("Верховная Жрица", "The High Priestess", "тишина|интуиция|то, что не сказано вслух"),
        Triple("Императрица", "The Empress", "рост|забота|тело и достаток"),
        Triple("Император", "The Emperor", "структура|границы|ответственность"),
        Triple("Иерофант", "The Hierophant", "правила|традиция|наставник"),
        Triple("Влюблённые", "The Lovers", "выбор|союз|честный обмен"),
        Triple("Колесница", "The Chariot", "рывок|контроль|движение вперёд"),
        Triple("Сила", "Strength", "терпение|мягкая мощь|самообладание"),
        Triple("Отшельник", "The Hermit", "пауза|свой свет|честность с собой"),
        Triple("Колесо Фортуны", "Wheel of Fortune", "смена цикла|шанс|не ты решаешь"),
        Triple("Справедливость", "Justice", "баланс|расчёт|по заслугам"),
        Triple("Повешенный", "The Hanged Man", "смена угла|жертва|остановка по делу"),
        Triple("Смерть", "Death", "финал этапа|расчистка|необратимость"),
        Triple("Умеренность", "Temperance", "дозировка|смешение|ровный ход"),
        Triple("Дьявол", "The Devil", "зависимость|сделка|то, что держит"),
        Triple("Башня", "The Tower", "обвал|внезапная правда|снос старого"),
        Triple("Звезда", "The Star", "надежда|ясная цель|восстановление"),
        Triple("Луна", "The Moon", "туман|страхи|не всё видно"),
        Triple("Солнце", "The Sun", "ясность|успех|тепло"),
        Triple("Суд", "Judgement", "пробуждение|зов|итоговый расчёт"),
        Triple("Мир", "The World", "завершение|целостность|выход на новый круг")
    )

    private val suits = listOf(
        Triple("Жезлы", "Wands", "воля и действие"),
        Triple("Кубки", "Cups", "чувства и связи"),
        Triple("Мечи", "Swords", "мысли и конфликт"),
        Triple("Пентакли", "Pentacles", "деньги, тело, быт")
    )

    private val ranks = listOf(
        Triple("Туз", "Ace", "импульс, начало"),
        Triple("Двойка", "Two", "выбор из двух"),
        Triple("Тройка", "Three", "первый рост"),
        Triple("Четвёрка", "Four", "устойчивость"),
        Triple("Пятёрка", "Five", "трение и потеря"),
        Triple("Шестёрка", "Six", "движение, обмен"),
        Triple("Семёрка", "Seven", "испытание"),
        Triple("Восьмёрка", "Eight", "усилие, повтор"),
        Triple("Девятка", "Nine", "почти итог"),
        Triple("Десятка", "Ten", "предел, результат"),
        Triple("Паж", "Page", "вестник, ученик"),
        Triple("Рыцарь", "Knight", "рывок, напор"),
        Triple("Королева", "Queen", "внутреннее владение"),
        Triple("Король", "King", "внешняя власть")
    )

    val cards: List<TarotCard> by lazy {
        val list = mutableListOf<TarotCard>()
        var id = 0
        majors.forEach { (ru, en, light) ->
            list += TarotCard(
                id = id++,
                title = ru,
                titleEn = en,
                arcana = "Старший аркан",
                light = light.split("|"),
                shadow = listOf("блок в: $ru", "тень: $ru", "то же, но против тебя")
            )
        }
        suits.forEach { (suitRu, suitEn, domain) ->
            ranks.forEach { (rankRu, rankEn, stage) ->
                list += TarotCard(
                    id = id++,
                    title = "$rankRu $suitRu",
                    titleEn = "$rankEn of $suitEn",
                    arcana = "Младший аркан",
                    light = listOf(domain, stage),
                    shadow = listOf("блок в: $domain", "тень: $stage")
                )
            }
        }
        list
    }

    /** 78 меток для классификатора. Порядок фиксирован — он же индекс в logits. */
    val labelsRu: List<String> by lazy { cards.map { it.title } }
    val labelsEn: List<String> by lazy { cards.map { it.titleEn } }

    fun byId(id: Int): TarotCard = cards[id]
}
