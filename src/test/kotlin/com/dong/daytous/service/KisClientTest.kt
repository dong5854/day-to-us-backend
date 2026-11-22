package com.dong.daytous.service

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import org.mockito.kotlin.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class KisClientTest {
    private val client = KisClient()
    private val mapper = JsonMapper.builder().build()
    @Test fun `잔고 요약의 객체와 배열 형식을 읽는다`() {
        for (output in listOf("""{"tot_evlu_amt":"1000000","dnca_tot_amt":"50000"}""", """[{"tot_evlu_amt":"1000000","dnca_tot_amt":"50000"}]""")) {
            assertThat(client.parseBalance(mapper.readTree("""{"rt_cd":"0","output2":$output}"""))).isEqualTo(KisBalance(1000000, 50000))
        }
    }
    @Test fun `조회 실패나 누락 금액을 영원으로 덮어쓰지 않는다`() {
        for (json in listOf("""{"rt_cd":"1","msg1":"secret"}""", """{"rt_cd":"0","output2":{}}""", """{"rt_cd":"0","output2":{"tot_evlu_amt":"0.5","dnca_tot_amt":"0"}}""")) {
            assertThatThrownBy { client.parseBalance(mapper.readTree(json)) }.isInstanceOf(KisUnavailable::class.java).hasMessageNotContaining("secret")
        }
    }
    @Test fun `계좌 종류별 공식 조회 경로와 파라미터를 사용한다`() {
        val http = mock<HttpClient>()
        val response = mock<HttpResponse<String>>()
        whenever(response.statusCode()).thenReturn(200)
        whenever(response.body()).thenReturn("""{"rt_cd":"0","output2":{"tot_evlu_amt":"1000","dnca_tot_amt":"100"}}""")
        whenever(http.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(response)
        val client = KisClient(http)
        client.balance("key", "secret", "token", "12345678", "29")
        client.balance("key", "secret", "token", "12345678", "22")
        val requests = argumentCaptor<HttpRequest>()
        verify(http, times(2)).send(requests.capture(), any<HttpResponse.BodyHandler<String>>())
        assertThat(requests.allValues[0].uri().path).endsWith("/pension/inquire-balance")
        assertThat(requests.allValues[0].headers().firstValue("tr_id").orElseThrow()).isEqualTo("TTTC2208R")
        assertThat(requests.allValues[1].uri().path).endsWith("/trading/inquire-balance")
        assertThat(requests.allValues[1].uri().query).contains("ACNT_PRDT_CD=22", "INQR_DVSN=02")
        assertThat(requests.allValues[1].headers().firstValue("tr_id").orElseThrow()).isEqualTo("TTTC8434R")
        assertThat(requests.allValues.map { it.method() }).containsOnly("GET")
    }
}
