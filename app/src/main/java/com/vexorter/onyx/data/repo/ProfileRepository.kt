package com.vexorter.onyx.data.repo

import com.vexorter.onyx.data.local.SavedProfileDao
import com.vexorter.onyx.data.local.SavedProfileEntity
import com.vexorter.onyx.data.prefs.UserPrefs
import com.vexorter.onyx.domain.Group
import com.vexorter.onyx.domain.Profile
import com.vexorter.onyx.domain.ScheduleKind
import com.vexorter.onyx.domain.Teacher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Профили: активный хранится в настройках (его читает всё приложение),
 * список сохранённых — в базе. Так переключение группы не требует
 * менять ничего, кроме одной записи в настройках.
 */
class ProfileRepository(
    private val prefs: UserPrefs,
    private val dao: SavedProfileDao,
) {

    val saved: Flow<List<Profile>> =
        dao.observeAll().map { list -> list.map(SavedProfileEntity::toProfile) }

    val active: Flow<Profile> = prefs.profile

    /** Завершение мастера: черновик + выбранная группа становятся профилем. */
    suspend fun selectGroup(group: Group) {
        val draft = prefs.draft.first()
        save(
            Profile(
                kind = ScheduleKind.GROUP,
                branchGuid = draft.branchGuid,
                branchName = draft.branchName,
                yearGuid = draft.yearGuid,
                yearName = draft.yearName,
                ownerGuid = group.guid,
                ownerName = group.name,
            )
        )
    }

    /** То же для преподавателя: года набора у него нет. */
    suspend fun selectTeacher(teacher: Teacher) {
        val draft = prefs.draft.first()
        save(
            Profile(
                kind = ScheduleKind.EMPLOYEE,
                branchGuid = draft.branchGuid,
                branchName = draft.branchName,
                ownerGuid = teacher.guid,
                ownerName = teacher.name,
            )
        )
    }

    suspend fun save(profile: Profile) {
        dao.upsert(profile.toEntity())
        prefs.setActiveProfile(profile)
    }

    suspend fun activate(profile: Profile) {
        prefs.setActiveProfile(profile)
    }

    /**
     * @return true, если после удаления остался хоть один профиль.
     *         Если удалили активный — активным становится первый из оставшихся.
     */
    suspend fun remove(profile: Profile): Boolean {
        dao.delete(profile.ownerGuid)
        val active = prefs.profile.first()
        if (active.ownerGuid != profile.ownerGuid) return true

        val next = dao.first()
        return if (next != null) {
            prefs.setActiveProfile(next.toProfile())
            true
        } else {
            prefs.clearProfile()
            false
        }
    }

    suspend fun count(): Int = dao.count()

    /**
     * Перенос для тех, кто обновился с версии с единственным профилем:
     * активный лежит в настройках, а таблица со списком ещё пуста.
     */
    suspend fun ensureMigrated() {
        val active = prefs.profile.first()
        if (active.isComplete && dao.count() == 0) {
            dao.upsert(active.toEntity())
        }
    }
}

private fun SavedProfileEntity.toProfile() = Profile(
    kind = runCatching { ScheduleKind.valueOf(kind) }.getOrDefault(ScheduleKind.GROUP),
    branchGuid = branchGuid,
    branchName = branchName,
    yearGuid = yearGuid,
    yearName = yearName,
    ownerGuid = ownerGuid,
    ownerName = ownerName,
)

private fun Profile.toEntity() = SavedProfileEntity(
    ownerGuid = ownerGuid,
    kind = kind.name,
    branchGuid = branchGuid,
    branchName = branchName,
    yearGuid = yearGuid,
    yearName = yearName,
    ownerName = ownerName,
    addedAt = System.currentTimeMillis(),
)
