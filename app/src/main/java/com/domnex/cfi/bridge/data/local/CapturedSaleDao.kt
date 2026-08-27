package com.domnex.cfi.bridge.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A UI NUNCA acessa o DAO diretamente — apenas via SaleHistoryRepository.
 */
@Dao
interface CapturedSaleDao {

    @Query("SELECT * FROM captured_sales ORDER BY capturadoEm DESC, id DESC")
    fun observeAll(): Flow<List<CapturedSaleEntity>>

    @Query("SELECT * FROM captured_sales WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CapturedSaleEntity?

    /** Deduplicação do histórico pela identityKey (índice único). */
    @Query("SELECT EXISTS(SELECT 1 FROM captured_sales WHERE identityKey = :identityKey LIMIT 1)")
    suspend fun existsByIdentityKey(identityKey: String): Boolean

    /** Retorna -1 em conflito de índice único (OnConflictStrategy.IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CapturedSaleEntity): Long

    /** Apaga TODAS as vendas do histórico Room local. Não toca em configuração. */
    @Query("DELETE FROM captured_sales")
    suspend fun clearAll(): Int
}
