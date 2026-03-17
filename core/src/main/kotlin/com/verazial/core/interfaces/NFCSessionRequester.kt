package com.verazial.core.interfaces

/**
 * Represents a requester capable of initiating an NFC session.
 *
 * This interface provides an abstraction layer between the
 * biometric library and the host application. The library
 * uses this requester to signal that an NFC session must be
 * started, typically by launching an Activity capable of
 * receiving NFC Tag dispatch events.
 *
 * The host application is responsible for implementing this
 * interface and assigning the instance to the companion object,
 * so the library can invoke it when an NFC session is required.
 */
interface NfcSessionRequester {

    /**
     * Requests the start of an NFC session.
     *
     * Implementations should trigger the mechanism required
     * to begin NFC communication, such as launching an Activity
     * configured to receive NFC Tag intents.
     */
    fun requestNfcSession()

    companion object {
        /**
         * Global reference to the active NFC session requester.
         *
         * Must be assigned by the host application before any
         * NFC-capable device attempts to perform a read operation.
         */
        lateinit var instance: NfcSessionRequester
    }
}
