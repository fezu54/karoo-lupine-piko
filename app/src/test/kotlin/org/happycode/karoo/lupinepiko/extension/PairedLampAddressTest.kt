package org.happycode.karoo.lupinepiko.extension

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class PairedLampAddressTest : ShouldSpec({

    should("return the lamp address when Karoo has a saved Lupine sensor") {
        // given
        val savedDeviceIds = listOf("other-extension::sensor-1", "karoo-lupine-piko::piko-11:22:33:44:55:66")

        // when
        val address = PairedLampAddress.from(savedDeviceIds)

        // then
        address shouldBe "11:22:33:44:55:66"
    }

    should("return null when Karoo has no saved Lupine sensor") {
        // given
        val savedDeviceIds = listOf("other-extension::sensor-1", "ant-plus-device")

        // when
        val address = PairedLampAddress.from(savedDeviceIds)

        // then
        address shouldBe null
    }

    should("return null when Karoo has no saved sensors at all") {
        PairedLampAddress.from(emptyList()) shouldBe null
    }
})
