package com.moneyplanner.data.sms

/**
 * Which inbox senders are worth reading at all.
 *
 * Indian banks send alerts from headers registered on the DLT platform — two letters for
 * the operator and region, a hyphen, and the bank's own ID, as in `VM-HDFCBK` or
 * `JD-ICICIB` — now often followed by a category suffix, as in `AD-SBIUPI-S`. Some phones
 * show the bare ID, and a few banks still send from short numeric codes. People send from
 * phone numbers.
 *
 * So the rule is about the shape of the sender, decided before a message's text is looked
 * at: a personal conversation is never parsed, however much it talks about money.
 */
object SmsSenderFilter {

    /** Two letters, a hyphen, the header, and an optional one-letter category suffix. */
    private val DLT_HEADER = Regex("""^[A-Za-z]{2}-([A-Za-z0-9]{3,9})(?:-([A-Za-z]))?$""")

    /** A bare alphanumeric sender ID, which the SMS standard caps at eleven characters. */
    private val PLAIN_ID = Regex("""^[A-Za-z0-9]{3,11}$""")

    /** Short codes are a handful of digits; anything longer is somebody's phone. */
    const val MAX_SHORT_CODE_DIGITS = 6

    /**
     * The `-P` suffix marks a promotional header. Transaction alerts are sent as service
     * or transactional messages, so a promotional sender only ever brings offers.
     */
    private const val PROMOTIONAL_SUFFIX = "P"

    fun isLikelyBankSender(address: String?): Boolean {
        val sender = address?.trim().orEmpty()
        if (sender.isEmpty()) return false

        if (sender.none { it.isLetter() }) {
            // A number. "+91…" and ten-digit mobiles are people; only a short code is a
            // service, and it carries no country prefix.
            if (sender.startsWith("+")) return false
            if (!sender.all { it.isDigit() || it == ' ' || it == '-' }) return false
            val digits = sender.count { it.isDigit() }
            return digits in 1..MAX_SHORT_CODE_DIGITS
        }

        DLT_HEADER.matchEntire(sender)?.let { header ->
            val suffix = header.groupValues[2]
            return !suffix.equals(PROMOTIONAL_SUFFIX, ignoreCase = true)
        }

        return PLAIN_ID.matches(sender)
    }
}
