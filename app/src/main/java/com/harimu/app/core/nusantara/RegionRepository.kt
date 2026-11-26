package com.harimu.app.core.nusantara

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException

/**
 * Repository wilayah Indonesia: provinsi → kabupaten/kota → kecamatan → desa/kelurahan.
 * Id dari layanan ini hierarkis, sehingga kode desa bisa langsung dipakai sebagai `adm4` BMKG.
 */
class RegionRepository(
    private val api: RegionApi = HttpRegionApi()
) {

    suspend fun provinces(): Result<List<Region>> =
        NusantaraHttp.run(SERVICE) { api.provinces().sortedBy { it.name } }

    suspend fun regencies(provinceId: String): Result<List<Region>> =
        NusantaraHttp.run(SERVICE) { api.regencies(provinceId.trim()).sortedBy { it.name } }

    suspend fun districts(regencyId: String): Result<List<Region>> =
        NusantaraHttp.run(SERVICE) { api.districts(regencyId.trim()).sortedBy { it.name } }

    suspend fun villages(districtId: String): Result<List<Region>> =
        NusantaraHttp.run(SERVICE) { api.villages(districtId.trim()).sortedBy { it.name } }

    /** Daftar desa dimuat bertahap lewat Paging karena jumlahnya bisa ratusan per kecamatan. */
    fun villagePagingSource(districtId: String): PagingSource<Int, Region> =
        VillagePagingSource(api, districtId.trim())

    companion object {
        private const val SERVICE = HttpRegionApi.SERVICE
    }
}

class VillagePagingSource(
    private val api: RegionApi,
    private val districtId: String
) : PagingSource<Int, Region>() {

    override fun getRefreshKey(state: PagingState<Int, Region>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Region> {
        val page = params.key ?: 0
        return try {
            val all = api.villages(districtId).sortedBy { it.name }
            val start = page * PAGE_SIZE
            val items = all.drop(start).take(PAGE_SIZE)
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (start + items.size < all.size) page + 1 else null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}
