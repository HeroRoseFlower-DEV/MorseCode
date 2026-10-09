package com.morsetranslator.app.daily

import java.time.LocalDate

/**
 * Offline daily motivational message.
 *
 * - The message is selected deterministically from the *local calendar date*
 *   ([LocalDate.now] in the device time zone): it stays the same all day and
 *   changes on the next local day. Reopening the app never reshuffles it.
 * - Documented behavior on time-zone changes: the selection follows whatever
 *   the device reports as "today"; crossing midnight (or changing zones)
 *   simply selects the message for the new local date.
 * - All messages are original to this app; no quotations, no attributions.
 * - Fully offline: bundled locally, no server, no account.
 */
object DailyMessages {

    data class Message(val id: Int, val fa: String, val en: String)

    val MESSAGES: List<Message> = listOf(
        Message(1, "هر قدم کوچک، تو را به مقصد نزدیک‌تر می‌کند.", "Every small step brings you closer to the goal."),
        Message(2, "امروز بهترین روز برای شروع است.", "Today is the best day to begin."),
        Message(3, "صبر، کلید درهای بسته است.", "Patience is the key to closed doors."),
        Message(4, "نور همیشه از دل تاریکی می‌آید.", "Light always comes from within darkness."),
        Message(5, "به خودت ایمان داشته باش.", "Believe in yourself."),
        Message(6, "شکست، پله‌ای به سوی پیروزی است.", "Failure is a step toward victory."),
        Message(7, "آرام باش و ادامه بده.", "Stay calm and carry on."),
        Message(8, "رویاهایت را جدی بگیر.", "Take your dreams seriously."),
        Message(9, "هر روز فرصتی تازه است.", "Every day is a new opportunity."),
        Message(10, "تلاش امروز، افتخار فرداست.", "Today's effort is tomorrow's pride."),
        Message(11, "قوی بمان، حتی وقتی سخت است.", "Stay strong, even when it's hard."),
        Message(12, "لبخند بزن، جهان لبخند می‌زند.", "Smile, and the world smiles back."),
        Message(13, "مسیر مهم است، نه فقط مقصد.", "The journey matters, not just the destination."),
        Message(14, "از اشتباهاتت درس بگیر.", "Learn from your mistakes."),
        Message(15, "هیچ‌وقت برای یادگیری دیر نیست.", "It's never too late to learn."),
        Message(16, "قلبت را دنبال کن.", "Follow your heart."),
        Message(17, "امید، چراغ راه است.", "Hope is the light of the way."),
        Message(18, "با هر نفس، شروعی تازه.", "With every breath, a fresh start."),
        Message(19, "تو از آنچه فکر می‌کنی قوی‌تری.", "You are stronger than you think."),
        Message(20, "کار نیک، هرگز فراموش نمی‌شود.", "A good deed is never forgotten."),
        Message(21, "بذر امروز، درخت فرداست.", "Today's seed is tomorrow's tree."),
        Message(22, "شجاعت یعنی ادامه دادن.", "Courage means going on."),
        Message(23, "سپاسگزاری، دل را آرام می‌کند.", "Gratitude calms the heart."),
        Message(24, "بهترین نسخهٔ خودت باش.", "Be the best version of yourself."),
        Message(25, "تاریکی، پایان شب نیست.", "Darkness is not the end of night."),
        Message(26, "با مهربانی جهان را عوض کن.", "Change the world with kindness."),
        Message(27, "استقامت، پیروزی می‌آورد.", "Perseverance brings victory."),
        Message(28, "ذهنت را از افکار خوب پر کن.", "Fill your mind with good thoughts."),
        Message(29, "فردا از امروز تو ممنون خواهد بود.", "Tomorrow will thank today's you."),
        Message(30, "آغاز هر تغییر بزرگ، یک تصمیم کوچک است.", "Every big change begins with a small decision.")
    )

    /** Deterministic selection for a local date. Pure and unit-testable. */
    fun forDate(date: LocalDate): Message {
        val index = (date.toEpochDay() % MESSAGES.size).toInt().let {
            if (it < 0) it + MESSAGES.size else it
        }
        return MESSAGES[index]
    }

    fun today(): Message = forDate(LocalDate.now())
}
