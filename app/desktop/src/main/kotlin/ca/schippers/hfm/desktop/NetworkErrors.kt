package ca.schippers.hfm.desktop

import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages

/** A web service answered with something other than 200. */
class HttpStatusException(val status: Int) : java.io.IOException("HTTP $status")

/** A web service's answer was larger than any real answer, so it was refused. */
class AnswerTooLargeException : java.io.IOException("Answer too large")

/**
 * What went wrong reaching a web service, in the user's language, instead of Java's own English
 * message: the HTTP status, no connection, a timeout, a failed secure connection, or an answer too
 * large. Anything else keeps its own message, or the kind of error when it has none.
 */
fun networkError(e: Throwable, language: Language): String {
    val chain = generateSequence(e) { it.cause.takeIf { c -> c !== it } }.take(5).toList()
    fun t(key: String, vararg args: Any) = Messages.get(language, key, *args)
    chain.firstNotNullOfOrNull { it as? HttpStatusException }?.let { return t("network.http", it.status.toString()) }
    return when {
        chain.any { it is AnswerTooLargeException } -> t("network.tooLarge")
        chain.any { it is java.net.http.HttpTimeoutException || it is java.net.SocketTimeoutException } -> t("network.timeout")
        chain.any { it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.NoRouteToHostException || it is java.nio.channels.UnresolvedAddressException } ->
            t("network.noConnection")
        chain.any { it is javax.net.ssl.SSLException } -> t("network.secure")
        chain.any { it is java.io.IOException } -> t("network.other", e.javaClass.simpleName)
        else -> e.message ?: e.javaClass.simpleName
    }
}
