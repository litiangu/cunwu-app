package com.litiangu.wuji.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WujiDatabaseTest {

    private lateinit var db: WujiDatabase
    private lateinit var itemDao: ItemDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 内存数据库：测试结束即销毁，不落盘
        db = Room.inMemoryDatabaseBuilder(context, WujiDatabase::class.java)
            .allowMainThreadQueries() // 测试便利，生产勿用
            .build()
        itemDao = db.itemDao()
        categoryDao = db.categoryDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insertAndQueryItem() = runBlocking {
        val catId = categoryDao.insert(
            CategoryEntity(name = "测试", colorKey = "custom", isPreset = false, sortOrder = 99)
        )
        val id = itemDao.insert(
            ItemEntity(
                name = "旧手机", categoryId = catId, status = ItemStatus.UNUSED,
                purchaseDate = System.currentTimeMillis(), purchasePrice = 8999.0,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
        )
        val fetched = itemDao.getById(id)
        assertEquals("旧手机", fetched?.name)
        assertEquals(catId, fetched?.categoryId)
    }

    @Test
    fun filterByCategoryAndSortByValue() = runBlocking {
        val catA = categoryDao.insert(
            CategoryEntity(name = "A", colorKey = "digital", isPreset = false, sortOrder = 1)
        )
        val catB = categoryDao.insert(
            CategoryEntity(name = "B", colorKey = "appliance", isPreset = false, sortOrder = 2)
        )
        itemDao.insert(ItemEntity(name = "a1", categoryId = catA, status = ItemStatus.IN_USE, purchaseDate = 1, purchasePrice = 10.0, createdAt = 1, updatedAt = 1))
        itemDao.insert(ItemEntity(name = "a2", categoryId = catA, status = ItemStatus.IN_USE, purchaseDate = 1, purchasePrice = 20.0, createdAt = 2, updatedAt = 2))
        itemDao.insert(ItemEntity(name = "b1", categoryId = catB, status = ItemStatus.SOLD, purchaseDate = 1, purchasePrice = 30.0, createdAt = 3, updatedAt = 3))

        val aItems = itemDao.getByCategory(catA)
        assertEquals(2, aItems.size)
        assertTrue(aItems.all { it.categoryId == catA })

        val byValue = itemDao.getAllByValueDesc()
        assertEquals("b1", byValue.first().name) // 30 最高，排在最前
    }
}
