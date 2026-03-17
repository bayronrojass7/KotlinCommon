package com.verazial.core.model

/**
 * Represents the state of the identify process.
 */
sealed class IdentifyState {

    /**
     * Step: The user is asked to provide his/her biometric data
     * using the biometric device.
     */
    object GettingBiometricSample : IdentifyState()

    /**
     * Step: The biometric data is being sent to the server
     * to search for a match.
     */
    object SearchingUserBySample : IdentifyState()

    /**
     * Result: The user was found.
     *
     * @param user The user that matched with the biometric data
     * provided.
     */
    data class UserFound(val user: User) : IdentifyState()

    /**
     * Result: An error occurred while trying to identify the user.
     */
    class IdentifyError(val error: Throwable) : IdentifyState()
}