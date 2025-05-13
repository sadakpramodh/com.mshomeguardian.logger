package com.mshomeguardian.logger.utils

fun String.capitalize(): String {
    return if (isNotEmpty()) {
        this[0].uppercaseChar() + substring(1)
    } else {
        this
    }
}