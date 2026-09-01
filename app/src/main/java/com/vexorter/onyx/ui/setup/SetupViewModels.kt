package com.vexorter.onyx.ui.setup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vexorter.onyx.AppContainer
import com.vexorter.onyx.appContainer
import com.vexorter.onyx.domain.Branch
import com.vexorter.onyx.domain.Group
import com.vexorter.onyx.domain.SyncResult
import com.vexorter.onyx.domain.Year
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickerUiState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class BranchPickerViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PickerUiState<Branch>())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.catalogRepository.observeBranches().collect { branches ->
                _state.update { it.copy(items = branches, isLoading = it.isLoading && branches.isEmpty()) }
            }
        }
        refresh(silent = true)
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            val hadCache = container.catalogRepository.hasBranches()
            when (val result = container.catalogRepository.refreshBranches()) {
                is SyncResult.Success -> _state.update { it.copy(isLoading = false, error = null) }
                is SyncResult.Error -> _state.update {
                    it.copy(isLoading = false, error = if (hadCache) null else result.message)
                }
            }
        }
    }

    fun select(branch: Branch, onDone: () -> Unit) {
        viewModelScope.launch {
            container.prefs.setBranch(branch.guid, branch.name)
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                BranchPickerViewModel(app.appContainer)
            }
        }
    }
}

class YearPickerViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PickerUiState<Year>())
    val state = _state.asStateFlow()

    val branchName = container.prefs.profile.map { it.branchName }

    init {
        viewModelScope.launch {
            container.catalogRepository.observeYears().collect { years ->
                _state.update { it.copy(items = years, isLoading = it.isLoading && years.isEmpty()) }
            }
        }
        refresh(silent = true)
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            val hadCache = container.catalogRepository.hasYears()
            when (val result = container.catalogRepository.refreshYears()) {
                is SyncResult.Success -> _state.update { it.copy(isLoading = false, error = null) }
                is SyncResult.Error -> _state.update {
                    it.copy(isLoading = false, error = if (hadCache) null else result.message)
                }
            }
        }
    }

    fun select(year: Year, onDone: () -> Unit) {
        viewModelScope.launch {
            container.prefs.setYear(year.guid, year.name)
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                YearPickerViewModel(app.appContainer)
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GroupPickerViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PickerUiState<Group>())
    val state = _state.asStateFlow()

    val subtitle = container.prefs.profile.map { profile ->
        listOf(profile.branchName, profile.yearName).filter { it.isNotBlank() }.joinToString(" · ")
    }

    init {
        viewModelScope.launch {
            container.prefs.profile
                .map { it.branchGuid to it.yearGuid }
                .distinctUntilChanged()
                .flatMapLatest { (branch, year) ->
                    container.catalogRepository.observeGroups(branch, year)
                }
                .collect { groups ->
                    _state.update { it.copy(items = groups, isLoading = it.isLoading && groups.isEmpty()) }
                }
        }
        refresh(silent = true)
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            val profile = container.prefs.profile.first()
            if (profile.branchGuid.isBlank() || profile.yearGuid.isBlank()) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            val hadCache = container.catalogRepository.hasGroups(profile.branchGuid, profile.yearGuid)
            val result = container.catalogRepository.refreshGroups(profile.branchGuid, profile.yearGuid)
            when (result) {
                is SyncResult.Success -> _state.update { it.copy(isLoading = false, error = null) }
                is SyncResult.Error -> _state.update {
                    it.copy(isLoading = false, error = if (hadCache) null else result.message)
                }
            }
        }
    }

    fun select(group: Group, onDone: () -> Unit) {
        viewModelScope.launch {
            container.prefs.setGroup(group.guid, group.name)
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                GroupPickerViewModel(app.appContainer)
            }
        }
    }
}
