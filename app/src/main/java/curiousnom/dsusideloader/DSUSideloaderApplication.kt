package curiousnom.dsusideloader

import android.app.Application
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import org.lsposed.hiddenapibypass.HiddenApiBypass

@HiltAndroidApp
class DSUSideloaderApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

    override fun onCreate() {
        super.onCreate()
    }
}
