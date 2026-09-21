package ir.pardava.mobile.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.pardava.mobile.core.ApiClient

class LoginViewModelFactory(private val client: ApiClient) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when (modelClass) {
            LoginViewModel::class.java -> LoginViewModel(client) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
        }
}
