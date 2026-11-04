package com.geowoof.core.data.session

import com.geowoof.core.common.Dispatcher
import com.geowoof.core.common.GeoWoofDispatchers
import com.geowoof.core.database.GeoWoofDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Removes the data of the account from the device when its session ends (docs/decisions/0009). */
interface UserDataCleaner {
    suspend fun clear()
}

internal class DatabaseUserDataCleaner @Inject constructor(
    private val database: GeoWoofDatabase,
    @Dispatcher(GeoWoofDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : UserDataCleaner {
    override suspend fun clear() = withContext(ioDispatcher) { database.clearAllTables() }
}
