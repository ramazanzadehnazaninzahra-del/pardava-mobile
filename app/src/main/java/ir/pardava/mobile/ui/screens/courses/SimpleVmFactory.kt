package ir.pardava.mobile.ui.screens.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.pardava.mobile.core.ApiClient

/**
 * Generic factory for viewmodels that only need the [ApiClient]:
 * `viewModel(factory = SimpleVmFactory(app.api) { CoursesViewModel(it) })`
 */
class SimpleVmFactory(
    private val client: ApiClient,
    private val create: (ApiClient) -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(client) as T
}
