package com.verazial.core.model

import java.io.Serializable

/**
 * Biographic information of a user.
 */
@kotlinx.serialization.Serializable
data class BiographicInfo(
    /**
     * User's first id.
     */
    val id: String,
    /**
     * User's first name.
     */
    val firstName: String,
    /**
     * User's second name.
     */
    val secondName: String,
    /**
     * User's first surname.
     */
    val firstLastName: String,
    /**
     * User's second surname.
     */
    val secondLastName: String,
    /**
     * User's identification number.
     */
    val nif: String
) : Serializable {

    val hasMandatoryFields
        get() = id.isNotEmpty()
                && (firstName + secondName).isNotBlank()
                && (firstLastName + secondLastName).isNotBlank()
                && nif.isNotBlank()

    /**
     * Returns the full name of the user.
     */
    val fullName: String
        get() {
            val name = "$firstName $secondName".trim()
            val lastName = "$firstLastName $secondLastName".trim()
            return "$name $lastName".trim()
        }
}