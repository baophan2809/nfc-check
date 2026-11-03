package com.baophan.nfccheck

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.baophan.nfccheck.ui.HistoryScreen
import com.baophan.nfccheck.ui.HomeScreen
import com.baophan.nfccheck.ui.MainViewModel
import com.baophan.nfccheck.ui.MrzScanScreen
import com.baophan.nfccheck.ui.NfcCheckTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Chặn chụp/quay màn hình và ẩn nội dung trong danh sách app gần đây.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            NfcCheckTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm = vm,
                            nfcSupported = nfcAdapter != null,
                            nfcEnabled = nfcAdapter?.isEnabled == true,
                            onOpenHistory = { navController.navigate("history") },
                            onOpenMrz = { navController.navigate("mrz") }
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("mrz") {
                        MrzScanScreen(
                            onDone = { key ->
                                vm.setKey(key)
                                vm.selectMode(com.baophan.nfccheck.ui.ScanMode.IDCARD)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableReaderMode()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    private fun enableReaderMode() {
        val adapter = nfcAdapter ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000)
        }
        adapter.enableReaderMode(this, { tag -> vm.onTag(tag) }, flags, extras)
    }
}
