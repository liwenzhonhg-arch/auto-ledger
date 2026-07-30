package com.liwenzhong.autoledger.repository

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.liwenzhong.autoledger.notification.NotificationSample
import com.liwenzhong.autoledger.parser.MAX_TRANSACTION_AMOUNT_CENTS
import com.liwenzhong.autoledger.parser.TransactionCandidate
import com.liwenzhong.autoledger.parser.TransactionDirection
import com.liwenzhong.autoledger.parser.WeChatNotificationParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class IngestResult {
    ADDED,
    DUPLICATE,
    IGNORED,
}

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["fingerprint"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAtMillis: Long,
    val amountCents: Long?,
    val direction: String,
    val counterparty: String,
    val source: String,
    val category: String,
    val classificationSource: String,
    val fingerprint: String,
    val status: String,
)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query(
        "SELECT * FROM transactions WHERE status = 'PENDING' " +
            "ORDER BY occurredAtMillis DESC, id DESC",
    )
    fun observePending(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE status = 'CONFIRMED' " +
            "ORDER BY occurredAtMillis DESC, id DESC",
    )
    fun observeConfirmed(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): TransactionEntity?

    @Query(
        """
        UPDATE transactions
        SET amountCents = :amountCents,
            counterparty = :counterparty,
            category = :category,
            classificationSource = 'MANUAL',
            status = 'CONFIRMED'
        WHERE fingerprint = :fingerprint AND status = 'PENDING'
        """,
    )
    suspend fun confirm(
        fingerprint: String,
        amountCents: Long,
        counterparty: String,
        category: String,
    ): Int
}

@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AutoLedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var instance: AutoLedgerDatabase? = null

        fun get(context: Context): AutoLedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutoLedgerDatabase::class.java,
                    "auto_ledger.db",
                ).build().also { instance = it }
            }
    }
}

class LedgerRepository(context: Context) {
    private val dao = AutoLedgerDatabase.get(context).transactionDao()

    val pending: Flow<List<TransactionCandidate>> =
        dao.observePending().map { rows -> rows.map(TransactionEntity::toCandidate) }
    val confirmed: Flow<List<TransactionCandidate>> =
        dao.observeConfirmed().map { rows -> rows.map(TransactionEntity::toCandidate) }

    suspend fun ingest(sample: NotificationSample): IngestResult {
        val candidate = WeChatNotificationParser.parse(sample) ?: return IngestResult.IGNORED
        return if (dao.insert(candidate.toEntity()) == -1L) {
            IngestResult.DUPLICATE
        } else {
            IngestResult.ADDED
        }
    }

    suspend fun confirm(
        fingerprint: String,
        amountCents: Long?,
        counterparty: String,
        category: String,
    ): Boolean {
        if (amountCents == null || amountCents !in 1L..MAX_TRANSACTION_AMOUNT_CENTS) return false
        return dao.confirm(
            fingerprint = fingerprint,
            amountCents = amountCents,
            counterparty = counterparty.trim().ifEmpty { "未确定" },
            category = category.trim().ifEmpty { "未分类" },
        ) == 1
    }
}

private fun TransactionCandidate.toEntity() = TransactionEntity(
    occurredAtMillis = occurredAtMillis,
    amountCents = amountCents,
    direction = direction.name,
    counterparty = counterparty,
    source = source,
    category = category,
    classificationSource = "UNCLASSIFIED",
    fingerprint = fingerprint,
    status = "PENDING",
)

private fun TransactionEntity.toCandidate() = TransactionCandidate(
    fingerprint = fingerprint,
    occurredAtMillis = occurredAtMillis,
    amountCents = amountCents,
    direction = TransactionDirection.valueOf(direction),
    counterparty = counterparty,
    source = source,
    category = category,
)
