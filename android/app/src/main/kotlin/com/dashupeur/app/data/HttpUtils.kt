package com.dashupeur.app.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

object HttpUtils {
  fun getText(
    url: String,
    params: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    timeoutMs: Int = 15000,
  ): String {
    val fullUrl = appendParams(url, params)
    val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = timeoutMs
      readTimeout = timeoutMs
      setRequestProperty("User-Agent", Config.USER_AGENT)
      setRequestProperty("Accept", "application/json, application/xml, text/xml, */*")
      headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    return readResponse(conn)
  }

  fun postForm(
    url: String,
    params: Map<String, String> = emptyMap(),
    timeoutMs: Int = 15000,
  ): String {
    val fullUrl = appendParams(url, params)
    val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = timeoutMs
      readTimeout = timeoutMs
      doOutput = true
      setRequestProperty("User-Agent", Config.USER_AGENT)
      setRequestProperty("Accept", "application/json")
    }
    return readResponse(conn)
  }

  private fun appendParams(url: String, params: Map<String, String>): String {
    if (params.isEmpty()) return url
    val qs = params.entries.joinToString("&") { (k, v) ->
      "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
    }
    return if (url.contains("?")) "$url&$qs" else "$url?$qs"
  }

  private fun readResponse(conn: HttpURLConnection): String {
    try {
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
      if (code !in 200..299) {
        throw IllegalStateException("HTTP $code")
      }
      return body
    } finally {
      conn.disconnect()
    }
  }

  fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }
}
