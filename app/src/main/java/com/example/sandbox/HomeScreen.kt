package com.example.sandbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider

@Composable
fun HomeScreen(navController: NavHostController) {
    LazyColumn {
        item {
            Box(modifier = Modifier
                .clickable { navController.navigate("peripheral") }
                .fillParentMaxWidth()
            ) {
                Text(
                    text = "Bluetooth BLE Peripheral",
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 32.dp)
                )
            }
            HorizontalDivider()
        }
    }
}
