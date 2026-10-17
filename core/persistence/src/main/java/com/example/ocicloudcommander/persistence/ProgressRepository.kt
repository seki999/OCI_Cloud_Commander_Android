package com.example.ocicloudcommander.persistence

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import commander.SaveGame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Entity(tableName = "sessions")
data class SessionRow(@PrimaryKey val slot: String = "current", val data: String)
@Entity(tableName = "progress")
data class ProgressRow(@PrimaryKey val levelId: String, val stars: Int)
@Dao
interface GameDao {
    @Query("SELECT * FROM sessions WHERE slot = 'current'") suspend fun current(): SessionRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(row: SessionRow)
    @Query("SELECT * FROM progress") suspend fun progress(): List<ProgressRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProgress(row: ProgressRow)
    @Transaction suspend fun record(row: SessionRow, result: ProgressRow?) { save(row); if(result != null) { val old=progress().find { it.levelId == result.levelId }; if(result.stars > (old?.stars ?: 0)) saveProgress(result) } }
}
@Database(entities=[SessionRow::class, ProgressRow::class], version=1, exportSchema=false)
abstract class GameDatabase: RoomDatabase() { abstract fun dao(): GameDao }
private val Context.settings by preferencesDataStore(name="settings")
data class Preferences(val highContrast: Boolean = false, val keepAwake: Boolean = false, val speech: Boolean = false, val dark: Boolean = true)
class ProgressRepository(context: Context) {
    private val app=context.applicationContext
    private val db=Room.databaseBuilder(app,GameDatabase::class.java,"commander.db").build()
    private val json=Json { encodeDefaults=true }
    suspend fun load(): SaveGame? = db.dao().current()?.let { json.decodeFromString<SaveGame>(it.data) }
    suspend fun save(save: SaveGame, stars: Int) = db.dao().record(SessionRow(data=json.encodeToString(save)), if(stars > 0) ProgressRow(save.levelId,stars) else null)
    suspend fun progress()=db.dao().progress().associate { it.levelId to it.stars }
    val preferences: Flow<Preferences> = app.settings.data.map { Preferences(it[booleanPreferencesKey("contrast")] ?: false,it[booleanPreferencesKey("awake")] ?: false,it[booleanPreferencesKey("speech")] ?: false,it[booleanPreferencesKey("dark")] ?: true) }
    suspend fun setting(key: String, value: Boolean) { require(key in setOf("contrast","awake","speech","dark")); app.settings.edit { it[booleanPreferencesKey(key)] = value } }
}
