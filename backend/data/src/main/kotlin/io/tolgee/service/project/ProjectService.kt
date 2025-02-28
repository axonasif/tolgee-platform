package io.tolgee.service.project

import io.tolgee.activity.ActivityHolder
import io.tolgee.batch.BatchJobService
import io.tolgee.constants.Caches
import io.tolgee.constants.Message
import io.tolgee.dtos.cacheable.ProjectDto
import io.tolgee.dtos.request.project.CreateProjectDTO
import io.tolgee.dtos.request.project.EditProjectDTO
import io.tolgee.dtos.response.ProjectDTO
import io.tolgee.dtos.response.ProjectDTO.Companion.fromEntityAndPermission
import io.tolgee.exceptions.BadRequestException
import io.tolgee.exceptions.NotFoundException
import io.tolgee.model.Language
import io.tolgee.model.Organization
import io.tolgee.model.OrganizationRole
import io.tolgee.model.Permission
import io.tolgee.model.Project
import io.tolgee.model.UserAccount
import io.tolgee.model.views.ProjectView
import io.tolgee.model.views.ProjectWithLanguagesView
import io.tolgee.repository.ProjectRepository
import io.tolgee.security.ProjectHolder
import io.tolgee.security.ProjectNotSelectedException
import io.tolgee.security.authentication.AuthenticationFacade
import io.tolgee.service.AvatarService
import io.tolgee.service.LanguageService
import io.tolgee.service.bigMeta.BigMetaService
import io.tolgee.service.dataImport.ImportService
import io.tolgee.service.key.KeyService
import io.tolgee.service.key.ScreenshotService
import io.tolgee.service.machineTranslation.MtServiceConfigService
import io.tolgee.service.organization.OrganizationService
import io.tolgee.service.security.ApiKeyService
import io.tolgee.service.security.PermissionService
import io.tolgee.service.security.SecurityService
import io.tolgee.service.translation.TranslationService
import io.tolgee.util.SlugGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import javax.persistence.EntityManager

@Transactional
@Service
class ProjectService(
  private val projectRepository: ProjectRepository,
  private val entityManager: EntityManager,
  private val screenshotService: ScreenshotService,
  private val authenticationFacade: AuthenticationFacade,
  private val slugGenerator: SlugGenerator,
  private val avatarService: AvatarService,
  private val activityHolder: ActivityHolder,
  @Lazy
  private val projectHolder: ProjectHolder,
  @Lazy
  private val batchJobService: BatchJobService
) {
  @set:Autowired
  @set:Lazy
  lateinit var keyService: KeyService

  @set:Autowired
  @set:Lazy
  lateinit var organizationService: OrganizationService

  @set:Autowired
  @set:Lazy
  lateinit var languageService: LanguageService

  @set:Autowired
  @set:Lazy
  lateinit var translationService: TranslationService

  @set:Autowired
  @set:Lazy
  lateinit var importService: ImportService

  @set:Autowired
  @set:Lazy
  lateinit var mtServiceConfigService: MtServiceConfigService

  @set:Autowired
  @set:Lazy
  lateinit var securityService: SecurityService

  @set:Autowired
  @set:Lazy
  lateinit var permissionService: PermissionService

  @set:Autowired
  @set:Lazy
  lateinit var apiKeyService: ApiKeyService

  @set:Autowired
  @set:Lazy
  lateinit var bigMetaService: BigMetaService

  @Transactional
  @Cacheable(cacheNames = [Caches.PROJECTS], key = "#id")
  fun findDto(id: Long): ProjectDto? {
    return projectRepository.findById(id).orElse(null)?.let {
      ProjectDto.fromEntity(it)
    }
  }

  @Transactional
  @Cacheable(cacheNames = [Caches.PROJECTS], key = "#id")
  fun getDto(id: Long): ProjectDto {
    return findDto(id) ?: throw NotFoundException(Message.PROJECT_NOT_FOUND)
  }

  fun get(id: Long): Project {
    return projectRepository.findByIdOrNull(id) ?: throw NotFoundException(Message.PROJECT_NOT_FOUND)
  }

  fun find(id: Long): Project? {
    return projectRepository.findByIdOrNull(id)
  }

  @Transactional
  fun getView(id: Long): ProjectWithLanguagesView {
    val perms = permissionService.getProjectPermissionData(id, authenticationFacade.authenticatedUser.id)
    val withoutPermittedLanguages = projectRepository.findViewById(authenticationFacade.authenticatedUser.id, id)
      ?: throw NotFoundException(Message.PROJECT_NOT_FOUND)
    return ProjectWithLanguagesView.fromProjectView(
      withoutPermittedLanguages,
      perms.directPermissions?.translateLanguageIds?.toList()
    )
  }

  @Transactional
  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#result.id")
  fun createProject(dto: CreateProjectDTO): Project {
    val project = Project()
    project.name = dto.name

    project.organizationOwner = organizationService.get(dto.organizationId)

    if (dto.slug == null) {
      project.slug = generateSlug(dto.name, null)
    }

    save(project)

    val createdLanguages = dto.languages!!.map { languageService.createLanguage(it, project) }
    project.baseLanguage = getOrCreateBaseLanguage(dto, createdLanguages)

    return project
  }

  @Transactional
  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#result.id")
  fun editProject(id: Long, dto: EditProjectDTO): Project {
    val project = projectRepository.findById(id)
      .orElseThrow { NotFoundException() }!!
    project.name = dto.name
    project.description = dto.description

    dto.baseLanguageId?.let {
      val language = project.languages.find { it.id == dto.baseLanguageId }
        ?: throw BadRequestException(Message.LANGUAGE_NOT_FROM_PROJECT)
      project.baseLanguage = language
    }

    val newSlug = dto.slug
    if (newSlug != null && newSlug != project.slug) {
      validateSlugUniqueness(newSlug)
      project.slug = newSlug
    }

    // if project has null slag, generate it
    if (project.slug == null) {
      project.slug = generateSlug(project.name, null)
    }

    entityManager.persist(project)
    return project
  }

  fun findAllPermitted(userAccount: UserAccount): List<ProjectDTO> {
    return projectRepository.findAllPermitted(userAccount.id).asSequence()
      .map { result ->
        val project = result[0] as Project
        val permission = result[1] as Permission?
        val organization = result[2] as Organization
        val organizationRole = result[3] as OrganizationRole?
        val scopes = permissionService.computeProjectPermission(
          organizationRole?.type,
          organization.basePermission,
          permission,
          userAccount.role ?: UserAccount.Role.USER
        ).scopes
        fromEntityAndPermission(project, scopes)
      }.toList()
  }

  fun findAllInOrganization(organizationId: Long): List<Project> {
    return this.projectRepository.findAllByOrganizationOwnerId(organizationId)
  }

  fun addPermittedLanguagesToProjects(projectsPage: Page<ProjectView>): Page<ProjectWithLanguagesView> {
    val projectLanguageMap = permissionService.getPermittedTranslateLanguagesForProjectIds(
      projectsPage.content.map { it.id },
      authenticationFacade.authenticatedUser.id
    )
    val newContent = projectsPage.content.map {
      ProjectWithLanguagesView.fromProjectView(it, projectLanguageMap[it.id])
    }

    return PageImpl(newContent, projectsPage.pageable, projectsPage.totalElements)
  }

  fun getProjectsWithFetchedLanguages(projectIds: Iterable<Long>): List<Project> {
    return projectRepository.getWithLanguages(projectIds)
  }

  @Transactional
  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#id")
  fun deleteProject(id: Long) {
    val project = get(id)

    try {
      projectHolder.project
    } catch (e: ProjectNotSelectedException) {
      projectHolder.project = ProjectDto.fromEntity(project)
    }

    importService.getAllByProject(id).forEach {
      importService.deleteImport(it)
    }

    // otherwise we cannot delete the languages
    project.baseLanguage = null
    projectRepository.saveAndFlush(project)
    apiKeyService.deleteAllByProject(project.id)
    permissionService.deleteAllByProject(project.id)
    screenshotService.deleteAllByProject(project.id)
    languageService.deleteAllByProject(project.id)
    keyService.deleteAllByProject(project.id)
    avatarService.unlinkAvatarFiles(project)
    batchJobService.deleteAllByProjectId(project.id)
    bigMetaService.deleteAllByProjectId(project.id)
    projectRepository.delete(project)
  }

  /**
   * If base language is missing on project it selects language with lowest id
   * It saves updated project and returns project's new baseLanguage
   */
  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#projectId")
  fun getOrCreateBaseLanguage(projectId: Long): Language? {
    val project = this.get(projectId)
    return project.baseLanguage ?: project.languages.toList().firstOrNull()?.let {
      project.baseLanguage = it
      projectRepository.save(project)
      it
    }
  }

  /**
   * If base language is missing on project it selects language with lowest id
   * It saves updated project and returns project's new baseLanguage
   */
  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#projectId")
  fun getOrCreateBaseLanguageOrThrow(projectId: Long): Language {
    return getOrCreateBaseLanguage(projectId) ?: throw IllegalStateException("Project has no languages")
  }

  @CacheEvict(cacheNames = [Caches.PROJECTS], allEntries = true)
  fun deleteAllByName(name: String) {
    projectRepository.findAllByName(name).forEach {
      this.deleteProject(it.id)
    }
  }

  @Transactional
  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#project.id")
  fun removeAvatar(project: Project) {
    avatarService.removeAvatar(project)
  }

  @Transactional
  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#project.id")
  fun setAvatar(project: Project, avatar: InputStream) {
    avatarService.setAvatar(project, avatar)
  }

  fun validateSlugUniqueness(slug: String): Boolean {
    return projectRepository.countAllBySlug(slug) < 1
  }

  fun generateSlug(name: String, oldSlug: String? = null): String {
    return slugGenerator.generate(name, 3, 60) {
      if (oldSlug == it) {
        return@generate true
      }
      this.validateSlugUniqueness(it)
    }
  }

  fun findPermittedInOrganizationPaged(
    pageable: Pageable,
    search: String?,
    organizationId: Long? = null
  ): Page<ProjectWithLanguagesView> {
    val withoutPermittedLanguages = projectRepository.findAllPermitted(
      authenticationFacade.authenticatedUser.id,
      pageable,
      search,
      organizationId
    )
    return addPermittedLanguagesToProjects(withoutPermittedLanguages)
  }

  @CacheEvict(cacheNames = [Caches.PROJECTS], allEntries = true)
  fun saveAll(projects: Collection<Project>): MutableList<Project> =
    projectRepository.saveAll(projects)

  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#result.id")
  fun save(project: Project): Project {
    val isCreating = project.id == 0L
    projectRepository.save(project)
    if (isCreating) {
      projectHolder.project = ProjectDto.fromEntity(project)
      activityHolder.activityRevision.projectId = projectHolder.project.id
    }
    return project
  }

  fun refresh(project: Project): Project {
    if (project.id == 0L) {
      return project
    }
    return this.projectRepository.findById(project.id).orElseThrow { NotFoundException() }
  }

  private fun getOrCreateBaseLanguage(dto: CreateProjectDTO, createdLanguages: List<Language>): Language {
    if (dto.baseLanguageTag != null) {
      return createdLanguages.find { it.tag == dto.baseLanguageTag }
        ?: throw BadRequestException(Message.LANGUAGE_WITH_BASE_LANGUAGE_TAG_NOT_FOUND)
    }
    return createdLanguages[0]
  }

  @CacheEvict(cacheNames = [Caches.PROJECTS], key = "#projectId")
  fun transferToOrganization(projectId: Long, organizationId: Long) {
    val project = get(projectId)
    val organization = organizationService.find(organizationId) ?: throw NotFoundException()
    project.organizationOwner = organization
    save(project)
  }

  fun findAllByNameAndOrganizationOwner(name: String, organization: Organization): List<Project> {
    return projectRepository.findAllByNameAndOrganizationOwner(name, organization)
  }

  fun getProjectsWithDirectPermissions(id: Long, userIds: List<Long>): Map<Long, List<Project>> {
    val result = projectRepository.getProjectsWithDirectPermissions(id, userIds)
    return result
      .map { it[0] as Long to it[1] as Project }
      .groupBy { it.first }
      .mapValues { it.value.map { it.second } }
  }
}
