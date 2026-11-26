package com.harimu.app.core.area

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Repository pencarian wilayah & kode pos: satu request ke `api/cari/index/`, lalu hasilnya
 * dibersihkan (baris kembar dibuang, jumlah dibatasi) dan setiap kegagalan diubah menjadi
 * [AreaLookupException] dengan pesan yang bisa ditampilkan. Hasil kosong bukan error —
 * artinya memang tidak ada wilayah yang cocok.
 */
class AreaLookupRepository(
    private val api: AreaLookupApi = HttpAreaLookupApi()
) {

    suspend fun load(keyword: String): Result<List<AreaEntry>> {
        val query = keyword.trim()
        if (query.isEmpty()) {
            return Result.failure(AreaLookupException(AreaErrorKind.EMPTY_QUERY))
        }
        if (query.length < MIN_QUERY_LENGTH) {
            return Result.failure(AreaLookupException(AreaErrorKind.TOO_SHORT))
        }
        return try {
            Result.success(api.search(query).dedupeEntries().take(MAX_RESULTS))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Result.failure(t as? AreaLookupException ?: AreaLookupException(kindOf(t), cause = t))
        }
    }

    private fun kindOf(t: Throwable): AreaErrorKind = when (t) {
        is IOException -> AreaErrorKind.NETWORK
        is SerializationException -> AreaErrorKind.INVALID_RESPONSE
        else -> AreaErrorKind.INVALID_RESPONSE
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2

        /** Batas baris yang ditampilkan; API bisa mengembalikan ratusan baris untuk kata kunci umum. */
        const val MAX_RESULTS = 200
    }
}

/** Buang baris yang benar-benar sama (desa/kecamatan/kabkota/provinsi/kodepos identik). */
internal fun List<AreaEntry>.dedupeEntries(): List<AreaEntry> = distinctBy {
    listOf(it.negara, it.provinsi, it.kabkota, it.kecamatan, it.desakel, it.kodepos)
}
