package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.db.AppDatabase
import com.example.data.network.FileTransferManager
import com.example.data.network.NsdHelper
import com.example.data.repository.TransferRepository
import com.example.ui.screens.MainDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TransferViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TransferViewModel by lazy {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TransferRepository(database.transferDao())
        val nsdHelper = NsdHelper(applicationContext)
        val fileTransferManager = FileTransferManager(applicationContext)
        
        ViewModelProvider(
            this,
            TransferViewModel.Factory(
                repository,
                nsdHelper,
                fileTransferManager,
                applicationContext
            )
        )[TransferViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainDashboard(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNetworkInfo()
        viewModel.startPeerDiscovery()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopPeerDiscovery()
    }
}
