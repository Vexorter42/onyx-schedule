package com.vexorter.onyx.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vexorter.onyx.appContainer
import com.vexorter.onyx.data.prefs.ThemeMode
import com.vexorter.onyx.domain.Profile
import com.vexorter.onyx.ui.common.FullScreenLoader
import com.vexorter.onyx.ui.schedule.ScheduleScreen
import com.vexorter.onyx.ui.settings.SettingsScreen
import com.vexorter.onyx.ui.setup.BranchPickerScreen
import com.vexorter.onyx.ui.setup.GroupPickerScreen
import com.vexorter.onyx.ui.setup.YearPickerScreen
import com.vexorter.onyx.ui.theme.RucScheduleTheme

private object Routes {
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
    const val SETUP_BRANCH = "setup/branch"
    const val SETUP_YEAR = "setup/year"
    const val SETUP_GROUP = "setup/group"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RucScheduleApp() }
    }
}

@Composable
private fun RucScheduleApp() {
    val container = LocalContext.current.appContainer
    val themeMode by container.prefs.themeMode
        .collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)

    // Пока профиль не прочитан из хранилища, держим заставку — иначе на долю секунды
    // мелькнёт экран первичной настройки у пользователя, который группу давно выбрал.
    val profile: Profile? by produceState<Profile?>(initialValue = null, container) {
        container.prefs.profile.collect { value = it }
    }

    RucScheduleTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val loaded = profile
            if (loaded == null) {
                FullScreenLoader()
            } else {
                AppNavHost(startWithSetup = !loaded.isComplete)
            }
        }
    }
}

@Composable
private fun AppNavHost(startWithSetup: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (startWithSetup) Routes.SETUP_BRANCH else Routes.SCHEDULE,
    ) {
        composable(Routes.SETUP_BRANCH) {
            BranchPickerScreen(
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onSelected = { navController.navigate(Routes.SETUP_YEAR) },
            )
        }

        composable(Routes.SETUP_YEAR) {
            YearPickerScreen(
                onBack = { navController.popBackStack() },
                onSelected = { navController.navigate(Routes.SETUP_GROUP) },
            )
        }

        composable(Routes.SETUP_GROUP) {
            GroupPickerScreen(
                onBack = { navController.popBackStack() },
                onSelected = {
                    navController.navigate(Routes.SCHEDULE) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.SCHEDULE) {
            ScheduleScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onChangeBranch = { navController.navigate(Routes.SETUP_BRANCH) },
                onChangeGroup = { navController.navigate(Routes.SETUP_GROUP) },
            )
        }
    }
}
