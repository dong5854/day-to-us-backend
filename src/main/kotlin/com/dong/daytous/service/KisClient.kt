package com.dong.daytous.service

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class KisUnavailable : RuntimeException("한국투자증권 조회에 실패했습니다. 계좌 연결 정보와 API 이용 상태를 확인해 주세요.")
data class KisToken(val value: String, val expiresIn: Long)
data class KisBalance(val valuation: Long, val cash: Long)

@Component
class KisClient(private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
    private val mapper = JsonMapper.builder().build()
    private val base = "https://openapi.koreainvestment.com:9443"

    private fun request(builder: HttpRequest.Builder): JsonNode = try {
        val response = http.send(builder.timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw KisUnavailable()
        mapper.readTree(response.body())
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt(); throw KisUnavailable()
    } catch (_: Exception) { throw KisUnavailable() }

    fun token(key: String, secret: String): KisToken {
        val body = mapper.writeValueAsString(mapOf("grant_type" to "client_credentials", "appkey" to key, "appsecret" to secret))
        val json = request(HttpRequest.newBuilder(URI.create("$base/oauth2/tokenP"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)))
        val value = json.path("access_token").asText("")
        val expires = json.path("expires_in").asLong(0)
        if (value.isBlank() || expires < 120) throw KisUnavailable()
        return KisToken(value, expires)
    }

    fun balance(key: String, secret: String, token: String, account: String, product: String): KisBalance {
        val retirement = product == "29"
        require(product == "22" || retirement)
        val params = linkedMapOf("CANO" to account, "ACNT_PRDT_CD" to product,
            "INQR_DVSN" to if (retirement) "00" else "02", "CTX_AREA_FK100" to "", "CTX_AREA_NK100" to "")
        if (retirement) params["ACCA_DVSN_CD"] = "00"
        else params.putAll(mapOf("AFHR_FLPR_YN" to "N", "OFL_YN" to "", "UNPR_DVSN" to "01",
            "FUND_STTL_ICLD_YN" to "N", "FNCG_AMT_AUTO_RDPT_YN" to "N", "PRCS_DVSN" to "00"))
        val query = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8)}" }
        val path = if (retirement) "pension/inquire-balance" else "inquire-balance"
        val json = request(HttpRequest.newBuilder(URI.create("$base/uapi/domestic-stock/v1/trading/$path?$query"))
            .header("authorization", "Bearer $token").header("appkey", key).header("appsecret", secret)
            .header("tr_id", if (retirement) "TTTC2208R" else "TTTC8434R").header("custtype", "P").GET())
        return parseBalance(json)
    }

    internal fun parseBalance(json: JsonNode): KisBalance {
        if (json.path("rt_cd").asText() != "0") throw KisUnavailable()
        // Only the account summary is used; holdings pages must never be summed as deposits.
        val output = json.path("output2")
        val summary = if (output.isArray && output.size() == 1) output[0] else output
        fun amount(field: String): Long = try {
            summary.path(field).asText().toBigDecimal().longValueExact().also { if (field == "tot_evlu_amt" && it < 0) throw KisUnavailable() }
        } catch (_: Exception) { throw KisUnavailable() }
        return KisBalance(amount("tot_evlu_amt"), amount("dnca_tot_amt"))
    }
}
