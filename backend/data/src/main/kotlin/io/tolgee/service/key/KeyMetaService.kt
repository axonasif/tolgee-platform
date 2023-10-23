package io.tolgee.service.key

import io.tolgee.model.Project
import io.tolgee.model.dataImport.Import
import io.tolgee.model.key.Key
import io.tolgee.model.key.KeyCodeReference
import io.tolgee.model.key.KeyComment
import io.tolgee.model.key.KeyMeta
import io.tolgee.model.key.WithKeyMetaReference
import io.tolgee.repository.KeyCodeReferenceRepository
import io.tolgee.repository.KeyCommentRepository
import io.tolgee.repository.KeyMetaRepository
import io.tolgee.util.Logging
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

@Service
class KeyMetaService(
  private val keyMetaRepository: KeyMetaRepository,
  private val keyCodeReferenceRepository: KeyCodeReferenceRepository,
  private val keyCommentRepository: KeyCommentRepository,
  private val entityManager: EntityManager,
) : Logging {
  @set:Autowired
  @set:Lazy
  lateinit var tagService: TagService

  fun saveAll(entities: Iterable<KeyMeta>): MutableList<KeyMeta> = keyMetaRepository.saveAll(entities)

  fun saveAllComments(entities: Iterable<KeyComment>): MutableList<KeyComment> =
    keyCommentRepository.saveAll(entities)

  fun saveAllCodeReferences(entities: Iterable<KeyCodeReference>): MutableList<KeyCodeReference> =
    keyCodeReferenceRepository.saveAll(entities)

  fun import(target: KeyMeta, source: KeyMeta) {
    target.comments.import(target, source.comments.toList()) { a, b ->
      a.text == b.text && a.fromImport == b.fromImport
    }
    target.codeReferences.import(target, source.codeReferences.toList()) { a, b ->
      a.line == b.line && a.path == b.path
    }
  }

  private inline fun <T : WithKeyMetaReference> List<T>.import(
    target: KeyMeta,
    source: Collection<T>,
    equalsFn: (a: T, b: T) -> Boolean
  ) {
    source.forEach { otherItem ->
      if (!this.any { equalsFn(it, otherItem) }) {
        otherItem.keyMeta = target
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun getWithFetchedData(import: Import): List<KeyMeta> {
    var result: List<KeyMeta> = entityManager.createQuery(
      """
            select distinct ikm from KeyMeta ikm
            join fetch ikm.importKey ik
            left join fetch ikm.comments ikc
            join ik.file if
            where if.import = :import 
            """
    )
      .setParameter("import", import)
      .resultList as List<KeyMeta>

    result = entityManager.createQuery(
      """
            select distinct ikm from KeyMeta ikm
            join ikm.importKey ik
            left join fetch ikm.codeReferences ikc
            join ik.file if
            where ikm in :metas 
        """
    ).setParameter("metas", result)
      .resultList as List<KeyMeta>

    return result
  }

  fun getWithFetchedData(project: Project): List<KeyMeta> {
    var result: List<KeyMeta> = entityManager.createQuery(
      """
            select distinct ikm from KeyMeta ikm
            join fetch ikm.key k
            left join fetch ikm.comments ikc
            where k.project = :project 
            """
    )
      .setParameter("project", project)
      .resultList as List<KeyMeta>

    result = entityManager.createQuery(
      """
            select distinct ikm from KeyMeta ikm
            join ikm.key k
            left join fetch ikm.codeReferences ikc
            where ikm in :metas 
        """
    ).setParameter("metas", result)
      .resultList as List<KeyMeta>

    return result
  }

  fun getOrCreateForKey(key: Key): KeyMeta {
    var keyMeta = key.keyMeta
    if (keyMeta == null) {
      keyMeta = KeyMeta(key)
      key.keyMeta = keyMeta
      keyMetaRepository.save(keyMeta)
    }
    return keyMeta
  }

  fun save(meta: KeyMeta): KeyMeta = this.keyMetaRepository.save(meta)

  fun deleteAllByImportKeyIdIn(importKeyIds: List<Long>) {
    tagService.deleteAllByImportKeyIdIn(importKeyIds)
    keyCommentRepository.deleteAllByImportKeyIds(importKeyIds)
    keyCodeReferenceRepository.deleteAllByImportKeyIds(importKeyIds)
    this.keyMetaRepository.deleteAllByImportKeyIdIn(importKeyIds)
  }

  fun deleteAllByKeyIdIn(ids: Collection<Long>) {
    tagService.deleteAllByKeyIdIn(ids)
    keyCommentRepository.deleteAllByKeyIds(ids)
    keyCodeReferenceRepository.deleteAllByKeyIds(ids)
    this.keyMetaRepository.deleteAllByKeyIds(ids)
  }

  fun deleteAllByKeys(keys: Collection<Key>) {
    traceLogMeasureTime("keyMetaService: deleteAllByKeyIdIn: tags") {
      tagService.deleteAllByKeyIn(keys)
    }

    val keyIds = keys.map { it.id }
    keyCommentRepository.deleteAllByKeyIds(keyIds)
    keyCodeReferenceRepository.deleteAllByKeyIds(keys)

    traceLogMeasureTime("keyMetaService: delete the metas") {
      this.keyMetaRepository.deleteAllByKeyIds(keyIds)
    }
  }

  fun deleteAllByKeyId(id: Long) {
    tagService.deleteAllByKeyIdIn(listOf(id))
    keyCommentRepository.deleteAllByKeyId(id)
    keyCodeReferenceRepository.deleteAllByKeyId(id)
    this.keyMetaRepository.deleteAllByKeyId(id)
  }
}
