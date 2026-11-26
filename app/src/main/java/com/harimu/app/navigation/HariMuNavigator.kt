package com.harimu.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Switches to a primary destination without stacking duplicates.
 *
 * The back stack is flattened to the graph's start destination while each tab keeps its own saved
 * state, so moving between tabs never grows the stack and Back always lands on the start
 * destination. Re-selecting the active tab stays in place, while re-selecting a tab that is showing
 * a nested state (for example the catalog filtered to one group) returns it to its root arguments.
 */
fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
