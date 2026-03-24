package com.smartstudybuddy.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** App mark shown left of the title in the top bar (real icon on Android). */
@Composable
expect fun AppBarLogo(modifier: Modifier = Modifier)
