package com.smartstudybuddy.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.smartstudybuddy.app.R

@Composable
actual fun AppBarLogo(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = "Smart Study",
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(18.dp)),
        // Foreground assets include adaptive safe-zone padding; crop fills the slot so the book reads larger.
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
    )
}
