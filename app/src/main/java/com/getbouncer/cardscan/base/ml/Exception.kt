package com.getbouncer.cardscan.base.ml

import java.lang.Exception

class HashMismatchException(val algorithm: String, val expected: String, val actual: String?) : Exception()
