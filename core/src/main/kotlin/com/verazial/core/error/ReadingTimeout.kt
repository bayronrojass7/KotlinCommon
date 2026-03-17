package com.verazial.core.error

/**
 * Represents that a timeout occurred while performing a reading operation on the biometric device
 */
object ReadingTimeout : Exception("Biometric device reading timed out")