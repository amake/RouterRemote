package com.madlonkay.routerremote

fun String.unwrap(c: Char): String {
    return if (this[0] == c && this[length - 1] == c) {
        substring(1, length - 1)
    } else {
        this
    }
}