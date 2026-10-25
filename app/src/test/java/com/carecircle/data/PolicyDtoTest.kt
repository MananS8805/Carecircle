package com.carecircle.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyDtoTest {
    @Test fun parsesServerResponseWithCitations() {
        val body = """
            {"summary":{"sumInsured":{"value":"Rs. 5,00,000","page":1,"clause":"2.1","quote":"The Sum Insured is Rs. 5,00,000"},
             "coPay":null,"waitingSpecific":[{"illness":"cataract","period":"24 months","page":2,"clause":null,"quote":"Cataract surgery has a waiting period of 24 months"}],
             "exclusions":[],"extraFieldFromNewServer":1},
             "unverified":["tpa"],"mode":"llm","disclaimer":"Informational only. Verify with your insurer."}
        """.trimIndent()
        val r = ApiFactory.json.decodeFromString<PolicyResponse>(body)
        assertEquals("Rs. 5,00,000", r.summary.sumInsured?.value)
        assertEquals(1, r.summary.sumInsured?.page)
        assertEquals("2.1", r.summary.sumInsured?.clause)
        assertNull(r.summary.coPay)
        assertEquals("cataract", r.summary.waitingSpecific.single().illness)
        assertNull(r.summary.waitingSpecific.single().clause)
        assertEquals(listOf("tpa"), r.unverified)
        assertTrue(!r.summary.isEmpty)
    }

    @Test fun emptySummaryIsEmpty() {
        assertTrue(ApiFactory.json.decodeFromString<PolicyResponse>("{\"summary\":{}}").summary.isEmpty)
    }

    @Test fun requestEncodesPages() {
        val s = ApiFactory.json.encodeToString(PolicyRequest.serializer(), PolicyRequest(listOf(PolicyPageText(1, "abc"))))
        assertEquals("{\"pages\":[{\"page\":1,\"text\":\"abc\"}]}", s)
    }
}
