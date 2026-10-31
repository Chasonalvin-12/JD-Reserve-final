package com.jdreserve.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import java.util.Locale

/** Screens L1 / L2: one login screen, two roles. Staff must match a known account. */
class LoginActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val roleGroup = findViewById<RadioGroup>(R.id.roleGroup)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val showPassword = findViewById<CheckBox>(R.id.showPassword)
        val signIn = findViewById<Button>(R.id.signInButton)

        showPassword.setOnCheckedChangeListener { _, checked ->
            passwordInput.transformationMethod =
                if (checked) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            passwordInput.setSelection(passwordInput.text.length)
        }

        signIn.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val isStaff = roleGroup.checkedRadioButtonId == R.id.radioStaff

            emailInput.error = null
            passwordInput.error = null

            if (!Validators.isEmail(email)) {
                emailInput.error = "Enter a valid email, e.g. you@example.com"
                emailInput.requestFocus()
                return@setOnClickListener
            }
            if (password.length < 6) {
                passwordInput.error = "Password must be at least 6 characters"
                passwordInput.requestFocus()
                return@setOnClickListener
            }

            if (isStaff) {
                val staffName = Repository.staffLogin(email, password)
                if (staffName == null) {
                    toast("Invalid staff email or password.")
                    return@setOnClickListener
                }
                Session.role = Role.STAFF
                Session.displayName = staffName
                Session.email = email.lowercase(Locale.ROOT)
                startActivity(Intent(this, StaffActivity::class.java))
            } else {
                Session.role = Role.GUEST
                Session.displayName = email.substringBefore('@').replaceFirstChar { it.uppercase() }
                Session.email = email
                startActivity(Intent(this, RoomsActivity::class.java))
            }
            finish()
        }
    }
}
