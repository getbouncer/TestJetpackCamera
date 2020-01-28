package com.getbouncer.cardscan.base

import java.lang.Exception

class HashMismatchException(val algorithm: String, val expected: String, val actual: String?) : Exception()
