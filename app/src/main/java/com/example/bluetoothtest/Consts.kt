package com.example.bluetoothtest

class Consts {

    companion object{
        const val REQUEST_ENABLE_BT = 1
        const val REQUEST_COARCH_LOCATION = 3
        const val SCAN_PERIOD: Long = 5000
        const val OFFS_SOF = 0
        const val OFFS_VERSION = 1
        const val OFFS_FLAGS = 2
        const val OFFS_PAYLOAD_LENGTH = 3
        const val OFFS_VENDOR_ID = 0
        const val OFFS_VENDOR_ID_H = OFFS_VENDOR_ID
        const val OFFS_VENDOR_ID_L = OFFS_VENDOR_ID + 1
        const val OFFS_COMMAND_ID = 2
        const val OFFS_COMMAND_ID_H = OFFS_COMMAND_ID
        const val OFFS_COMMAND_ID_L = OFFS_COMMAND_ID + 1
        const val OFFS_PAYLOAD = 4

        const val GATT_UUID = "-0000-1000-8000-00805f9b34fb"

        const val CSR_UUID = "-D102-11E1-9B23-00025B00A5A5"

        const val DESCRIPTOR_UUID = "00002902$GATT_UUID"

        const val SERVICE_ID = "00001100$CSR_UUID"

        const val COMMAND_ID = "00001101$CSR_UUID"

        const val RESPONSE_ID = "00001102$CSR_UUID"

        const val DATA_ID = "00001103$CSR_UUID"
    }
}