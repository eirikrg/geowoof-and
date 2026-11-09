package com.geowoof.core.model

/**
 * Reads a value of an enum that the server sends, or that came from it and was kept in Room. The API can add values
 * at any time, and an app installed before them has to keep working: an unknown one is [fallback], never an
 * exception. `valueOf` is only for values that the app itself wrote.
 */
inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T = toEnumOrNull<T>() ?: fallback

/** As [toEnumOr], for when an unknown value is left out, or is the same as having none. */
inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
