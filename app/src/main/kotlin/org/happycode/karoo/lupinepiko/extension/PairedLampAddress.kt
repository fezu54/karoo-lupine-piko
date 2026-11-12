package org.happycode.karoo.lupinepiko.extension

import io.hammerhead.karooext.models.Device

object PairedLampAddress {
    fun from(savedDeviceIds: List<String>): String? = savedDeviceIds
        .mapNotNull { Device.fromDeviceUid(it) }
        .firstOrNull { (extensionId, _) -> extensionId == LupinePikoExtension.EXTENSION_ID }
        ?.let { (_, uid) -> LupinePikoExtension.extractMacAddress(uid) }
}
