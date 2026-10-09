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
import androidx.room.withTransaction
import com.liwenzhong.autoledger.notification.NotificationSample
import com.liwenzhong.autoledger.parser.MAX_TRANSACTION_AMOUNT_CENTS
import com.liwenzhong.autoledger.parser.TransactionCandidate
import com.liwenzhong.autoledger.parser.TransactionDirection
import com.liwenzhong.autoledger.parser.WECHAT_SCREEN_SOURCE
import com.liwenzhong.autoledger.parser.WeChatNotificationParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import com.liwenzhong.autoledger.capture.CaptureDiagnostics
import com.liwenzhong.autoledger.capture.CaptureOutcome
import com.liwenzhong.autoledger.capture.CaptureSource

enum class IngestResult {
    ADDED,
    DUPLICATE,
    IGNORED,
    FAILED,
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
        "SELECT * FROM transactions WHERE source = :source " +
            "AND (fingerprint = :contentFingerprint OR fingerprint GLOB :occurrencePattern) " +
            "AND occurredAtMillis > :windowStartMillis AND occurredAtMillis < :windowEndMillis " +
            "ORDER BY occurredAtMillis DESC, id DESC LIMIT 1",
    )
    suspend fun findLatestScreenObservation(
        source: String,
        contentFingerprint: String,
        occurrencePattern: String,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): TransactionEntity?

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

    @Query("DELETE FROM transactions WHERE counterparty = '测试商户'")
    suspend fun deleteSyntheticTransactions(): Int
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

class LedgerRepository internal constructor(private val database: AutoLedgerDatabase) {
    constructor(context: Context) : this(AutoLedgerDatabase.get(context))

    private val dao = database.transactionDao()

    val pending: Flow<List<TransactionCandidate>> =
        dao.observePending().map { rows -> rows.map(TransactionEntity::toCandidate) }
    val confirmed: Flow<List<TransactionCandidate>> =
        dao.observeConfirmed().map { rows -> rows.map(TransactionEntity::toCandidate) }

    suspend fun ingest(sample: NotificationSample): IngestResult {
        return recordIngestion(CaptureSource.NOTIFICATION) {
            val candidate = WeChatNotificationParser.parse(sample) ?: return@recordIngestion IngestResult.IGNORED
            insertCandidate(candidate)
        }
    }

    suspend fun ingest(candidate: TransactionCandidate): IngestResult {
        val source = if (candidate.source == WECHAT_SCREEN_SOURCE) CaptureSource.SCREEN else CaptureSource.NOTIFICATION
        return recordIngestion(source) { insertCandidate(candidate) }
    }

    private suspend fun insertCandidate(candidate: TransactionCandidate): IngestResult {
        val entity = candidate.toEntity()
        val insertedId = if (candidate.source == WECHAT_SCREEN_SOURCE) {
            database.withTransaction {
                val contentFingerprint = candidate.fingerprint.substringBefore(':')
                val previous = dao.findLatestScreenObservation(
                    source = WECHAT_SCREEN_SOURCE,
                    contentFingerprint = contentFingerprint,
                    occurrencePattern = "$contentFingerprint:*",
                    windowStartMillis = candidate.occurredAtMillis - SCREEN_DUPLICATE_WINDOW_MILLIS,
                    windowEndMillis = candidate.occurredAtMillis + SCREEN_DUPLICATE_WINDOW_MILLIS,
                )
                dao.insert(screenObservationForInsert(entity, previous))
            }
        } else {
            dao.insert(entity)
        }
        return if (insertedId == -1L) {
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

    suspend fun deleteSyntheticTransactions(): Int = dao.deleteSyntheticTransactions()
}

internal suspend fun recordIngestion(source: CaptureSource, write: suspend () -> IngestResult): IngestResult {
    val result = try {
        write()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Do not expose exception text: SQLite errors can contain bound values.
        IngestResult.FAILED
    }
    CaptureDiagnostics.report(source, when (result) {
        IngestResult.ADDED -> CaptureOutcome.SAVED
        IngestResult.DUPLICATE -> CaptureOutcome.DUPLICATE
        IngestResult.IGNORED -> CaptureOutcome.NOT_TRANSACTION
        IngestResult.FAILED -> CaptureOutcome.SAVE_FAILED
    })
    return result
}

internal const val SCREEN_DUPLICATE_WINDOW_MILLIS = 90_000L

internal fun screenObservationForInsert(
    candidate: TransactionEntity,
    previous: TransactionEntity?,
): TransactionEntity {
    val contentFingerprint = candidate.fingerprint.substringBefore(':')
    // ponytail: no payment ID is available; merge only a short capture burst. Reopened old bills
    // and distinct identical payments within this window need manual verification.
    val sameObservation = previous != null &&
        previous.source == candidate.source &&
        previous.fingerprint.substringBefore(':') == contentFingerprint &&
        candidate.occurredAtMillis - previous.occurredAtMillis in
            -(SCREEN_DUPLICATE_WINDOW_MILLIS - 1)..(SCREEN_DUPLICATE_WINDOW_MILLIS - 1)
    return candidate.copy(
        fingerprint = if (sameObservation) previous.fingerprint else "$contentFingerprint:${candidate.occurredAtMillis}",
    )
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
