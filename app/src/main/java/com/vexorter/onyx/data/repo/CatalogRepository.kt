package com.vexorter.onyx.data.repo

import com.vexorter.onyx.data.local.BranchEntity
import com.vexorter.onyx.data.local.CatalogDao
import com.vexorter.onyx.data.local.GroupEntity
import com.vexorter.onyx.data.local.TeacherEntity
import com.vexorter.onyx.data.local.YearEntity
import com.vexorter.onyx.data.remote.ScheduleApi
import com.vexorter.onyx.domain.Branch
import com.vexorter.onyx.domain.Group
import com.vexorter.onyx.domain.SyncResult
import com.vexorter.onyx.domain.Teacher
import com.vexorter.onyx.domain.Year
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Справочники (филиалы / наборы / группы). Источник правды — Room,
 * сеть только обновляет кэш, поэтому выбор профиля работает и без интернета.
 */
class CatalogRepository(
    private val api: ScheduleApi,
    private val dao: CatalogDao,
) {

    fun observeBranches(): Flow<List<Branch>> =
        dao.observeBranches().map { list -> list.map { it.toDomain() } }

    fun observeYears(): Flow<List<Year>> =
        dao.observeYears().map { list -> list.map { it.toDomain() } }

    fun observeGroups(branchGuid: String, yearGuid: String): Flow<List<Group>> =
        dao.observeGroups(branchGuid, yearGuid).map { list -> list.map { it.toDomain() } }

    fun observeTeachers(branchGuid: String): Flow<List<Teacher>> =
        dao.observeTeachers(branchGuid).map { list -> list.map { it.toDomain() } }

    suspend fun hasTeachers(branchGuid: String): Boolean = dao.teacherCount(branchGuid) > 0

    suspend fun refreshTeachers(branchGuid: String): SyncResult = runSync {
        val remote = api.getEmployees(branchGuid)
        dao.replaceTeachers(
            branchGuid,
            remote.mapIndexed { index, dto ->
                TeacherEntity(
                    guid = dto.guid,
                    branchGuid = branchGuid,
                    name = dto.name.trim(),
                    position = dto.position.trim(),
                    sortIndex = index,
                )
            }
        )
    }

    suspend fun hasBranches(): Boolean = dao.branchCount() > 0

    suspend fun hasYears(): Boolean = dao.yearCount() > 0

    suspend fun hasGroups(branchGuid: String, yearGuid: String): Boolean =
        dao.groupCount(branchGuid, yearGuid) > 0

    suspend fun refreshBranches(): SyncResult = runSync {
        val remote = api.getBranches()
        dao.replaceBranches(
            remote.mapIndexed { index, dto ->
                BranchEntity(
                    guid = dto.guid,
                    name = dto.name.trim(),
                    code = dto.code.trim(),
                    sortIndex = index,
                )
            }
        )
    }

    suspend fun refreshYears(): SyncResult = runSync {
        val remote = api.getYears()
        dao.replaceYears(
            remote.mapIndexed { index, dto ->
                YearEntity(
                    guid = dto.guid,
                    name = dto.name.trim(),
                    code = dto.code.trim(),
                    sortIndex = index,
                )
            }
        )
    }

    suspend fun refreshGroups(branchGuid: String, yearGuid: String): SyncResult = runSync {
        val remote = api.getGroups(branchGuid, yearGuid)
        var index = 0
        val entities = remote.flatMap { category ->
            category.groups.map { group ->
                GroupEntity(
                    guid = group.guid,
                    branchGuid = branchGuid,
                    yearGuid = yearGuid,
                    name = group.name.trim(),
                    category = category.name.trim(),
                    owner = group.owner.trim(),
                    sortIndex = index++,
                )
            }
        }
        dao.replaceGroups(branchGuid, yearGuid, entities)
    }
}
