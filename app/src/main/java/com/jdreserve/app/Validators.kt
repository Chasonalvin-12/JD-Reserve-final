package com.jdreserve.app

object Validators {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PH_MOBILE = Regex("^(09|\\+639)\\d{9}$")

    fun isEmail(s: String): Boolean = EMAIL.matches(s)

    /** Philippine mobile: 09xxxxxxxxx or +639xxxxxxxxx */
    fun isPhMobile(s: String): Boolean = PH_MOBILE.matches(s)
}
