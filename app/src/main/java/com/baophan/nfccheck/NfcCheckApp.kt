package com.baophan.nfccheck

import android.app.Application
import java.security.Security

class NfcCheckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Đăng ký nhà cung cấp mật mã SpongyCastle cho JMRTD (đọc chip CCCD).
        try {
            Security.insertProviderAt(org.spongycastle.jce.provider.BouncyCastleProvider(), 1)
        } catch (_: Throwable) {
        }
    }
}
