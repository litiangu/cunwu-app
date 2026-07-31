package com.litiangu.wuji

import android.app.Application
import com.litiangu.wuji.data.db.WujiDatabase
import com.litiangu.wuji.data.db.WujiDatabaseProvider

class WujiApplication : Application() {
    val database: WujiDatabase by lazy { WujiDatabaseProvider.get(this) }
}