/*
 * Copyright (c) 2020. Tolgee
 */

package io.tolgee.api.v2.controllers.organization

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.tolgee.api.v2.hateoas.invitation.OrganizationInvitationModel
import io.tolgee.api.v2.hateoas.invitation.OrganizationInvitationModelAssembler
import io.tolgee.api.v2.hateoas.organization.OrganizationModel
import io.tolgee.api.v2.hateoas.organization.OrganizationModelAssembler
import io.tolgee.api.v2.hateoas.organization.UsageModel
import io.tolgee.api.v2.hateoas.organization.UserAccountWithOrganizationRoleModel
import io.tolgee.api.v2.hateoas.organization.UserAccountWithOrganizationRoleModelAssembler
import io.tolgee.component.translationsLimitProvider.TranslationsLimitProvider
import io.tolgee.configuration.tolgee.TolgeeProperties
import io.tolgee.constants.Message
import io.tolgee.dtos.misc.CreateOrganizationInvitationParams
import io.tolgee.dtos.request.organization.OrganizationDto
import io.tolgee.dtos.request.organization.OrganizationInviteUserDto
import io.tolgee.dtos.request.organization.OrganizationRequestParamsDto
import io.tolgee.dtos.request.organization.SetOrganizationRoleDto
import io.tolgee.dtos.request.validators.exceptions.ValidationException
import io.tolgee.exceptions.BadRequestException
import io.tolgee.exceptions.NotFoundException
import io.tolgee.exceptions.PermissionException
import io.tolgee.model.Project
import io.tolgee.model.UserAccount
import io.tolgee.model.enums.OrganizationRoleType
import io.tolgee.model.enums.ProjectPermissionType
import io.tolgee.model.views.OrganizationView
import io.tolgee.model.views.UserAccountWithOrganizationRoleView
import io.tolgee.security.AuthenticationFacade
import io.tolgee.security.NeedsSuperJwtToken
import io.tolgee.security.patAuth.DenyPatAccess
import io.tolgee.service.ImageUploadService
import io.tolgee.service.InvitationService
import io.tolgee.service.machineTranslation.MtCreditBucketService
import io.tolgee.service.organization.OrganizationRoleService
import io.tolgee.service.organization.OrganizationService
import io.tolgee.service.organization.OrganizationStatsService
import io.tolgee.service.project.ProjectService
import io.tolgee.service.security.UserAccountService
import org.springdoc.api.annotations.ParameterObject
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PagedResourcesAssembler
import org.springframework.data.web.SortDefault
import org.springframework.hateoas.CollectionModel
import org.springframework.hateoas.MediaTypes
import org.springframework.hateoas.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import javax.validation.Valid

@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping(value = ["/v2/organizations"])
@Tag(name = "Organizations")
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
class OrganizationController(
  private val organizationService: OrganizationService,
  private val arrayResourcesAssembler: PagedResourcesAssembler<OrganizationView>,
  private val arrayUserResourcesAssembler: PagedResourcesAssembler<
    Pair<UserAccountWithOrganizationRoleView, List<Project>>
    >,
  private val organizationModelAssembler: OrganizationModelAssembler,
  private val userAccountWithOrganizationRoleModelAssembler: UserAccountWithOrganizationRoleModelAssembler,
  private val tolgeeProperties: TolgeeProperties,
  private val authenticationFacade: AuthenticationFacade,
  private val organizationRoleService: OrganizationRoleService,
  private val userAccountService: UserAccountService,
  private val invitationService: InvitationService,
  private val organizationInvitationModelAssembler: OrganizationInvitationModelAssembler,
  private val imageUploadService: ImageUploadService,
  private val mtCreditBucketService: MtCreditBucketService,
  private val organizationStatsService: OrganizationStatsService,
  private val translationsLimitProvider: TranslationsLimitProvider,
  private val projectService: ProjectService
) {
  @PostMapping
  @Transactional
  @Operation(summary = "Creates organization")
  fun create(@RequestBody @Valid dto: OrganizationDto): ResponseEntity<OrganizationModel> {
    if (!this.tolgeeProperties.authentication.userCanCreateOrganizations &&
      authenticationFacade.userAccount.role != UserAccount.Role.ADMIN
    ) {
      throw PermissionException()
    }
    this.organizationService.create(dto).let {
      return ResponseEntity(
        organizationModelAssembler.toModel(OrganizationView.of(it, OrganizationRoleType.OWNER)), HttpStatus.CREATED
      )
    }
  }

  @GetMapping("/{id:[0-9]+}")
  @Operation(summary = "Returns organization by ID")
  fun get(@PathVariable("id") id: Long): OrganizationModel? {
    val organization = organizationService.get(id)
    organizationRoleService.checkUserCanView(organization.id)
    val roleType = organizationRoleService.findType(id)
    return OrganizationView.of(organization, roleType).toModel()
  }

  @GetMapping("/{slug:.*[a-z].*}")
  @Operation(summary = "Returns organization by address part")
  fun get(@PathVariable("slug") slug: String): OrganizationModel {
    val organization = organizationService.get(slug)
    organizationRoleService.checkUserCanView(organization.id)
    val roleType = organizationRoleService.findType(organization.id)
    return OrganizationView.of(organization, roleType).toModel()
  }

  @GetMapping("", produces = [MediaTypes.HAL_JSON_VALUE])
  @Operation(summary = "Returns all organizations, which is current user allowed to view")
  fun getAll(
    @ParameterObject @SortDefault(sort = ["id"]) pageable: Pageable,
    params: OrganizationRequestParamsDto
  ): PagedModel<OrganizationModel>? {
    val organizations = organizationService.findPermittedPaged(pageable, params)
    return arrayResourcesAssembler.toModel(organizations, organizationModelAssembler)
  }

  @PutMapping("/{id:[0-9]+}")
  @Operation(summary = "Updates organization data")
  @NeedsSuperJwtToken
  @DenyPatAccess
  fun update(@PathVariable("id") id: Long, @RequestBody @Valid dto: OrganizationDto): OrganizationModel {
    organizationRoleService.checkUserIsOwner(id)
    return this.organizationService.edit(id, editDto = dto).toModel()
  }

  @DeleteMapping("/{id:[0-9]+}")
  @Operation(summary = "Deletes organization and all its projects")
  @NeedsSuperJwtToken
  fun delete(@PathVariable("id") id: Long) {
    organizationRoleService.checkUserIsOwner(id)
    organizationService.delete(id)
  }

  @GetMapping("/{id:[0-9]+}/users")
  @Operation(summary = "Returns all users in organization")
  @NeedsSuperJwtToken
  @DenyPatAccess
  fun getAllUsers(
    @PathVariable("id") id: Long,
    @ParameterObject @SortDefault(sort = ["name", "username"], direction = Sort.Direction.ASC) pageable: Pageable,
    @RequestParam("search") search: String?
  ): PagedModel<UserAccountWithOrganizationRoleModel> {
    organizationRoleService.checkUserIsMemberOrOwner(id)
    val allInOrganization = userAccountService.getAllInOrganization(id, pageable, search)
    val userIds = allInOrganization.content.map { it.id }
    val projectsWithDirectPermission = projectService.getProjectsWithDirectPermissions(id, userIds)
    val pairs = allInOrganization.content.map { user ->
      user to (projectsWithDirectPermission[user.id] ?: emptyList())
    }

    val data = PageImpl(pairs, allInOrganization.pageable, allInOrganization.totalElements)

    return arrayUserResourcesAssembler.toModel(data, userAccountWithOrganizationRoleModelAssembler)
  }

  @PutMapping("/{id:[0-9]+}/leave")
  @Operation(summary = "Removes current user from organization")
  @NeedsSuperJwtToken
  fun leaveOrganization(@PathVariable("id") id: Long) {
    organizationService.find(id)?.let {
      if (!organizationService.isThereAnotherOwner(id)) {
        throw ValidationException(Message.ORGANIZATION_HAS_NO_OTHER_OWNER)
      }
      organizationRoleService.leave(id)
    } ?: throw NotFoundException()
  }

  @PutMapping("/{organizationId:[0-9]+}/users/{userId:[0-9]+}/set-role")
  @Operation(summary = "Sets user role (Owner or Member)")
  @NeedsSuperJwtToken
  fun setUserRole(
    @PathVariable("organizationId") organizationId: Long,
    @PathVariable("userId") userId: Long,
    @RequestBody dto: SetOrganizationRoleDto
  ) {
    if (authenticationFacade.userAccount.id == userId) {
      throw BadRequestException(Message.CANNOT_SET_YOUR_OWN_ROLE)
    }
    organizationRoleService.checkUserIsOwner(organizationId)
    organizationRoleService.setMemberRole(organizationId, userId, dto)
  }

  @DeleteMapping("/{organizationId:[0-9]+}/users/{userId:[0-9]+}")
  @Operation(summary = "Removes user from organization")
  @NeedsSuperJwtToken
  fun removeUser(
    @PathVariable("organizationId") organizationId: Long,
    @PathVariable("userId") userId: Long
  ) {
    organizationRoleService.checkUserIsOwner(organizationId)
    organizationRoleService.removeUser(organizationId, userId)
  }

  @PutMapping("/{id:[0-9]+}/invite")
  @Operation(summary = "Generates user invitation link for organization")
  @NeedsSuperJwtToken
  fun inviteUser(
    @RequestBody @Valid dto: OrganizationInviteUserDto,
    @PathVariable("id") id: Long
  ): OrganizationInvitationModel {
    organizationRoleService.checkUserIsOwner(id)

    val organization = organizationService.get(id)

    val invitation = invitationService.create(
      CreateOrganizationInvitationParams(
        organization = organization,
        type = dto.roleType,
        email = dto.email,
        name = dto.name
      )
    )

    return organizationInvitationModelAssembler.toModel(invitation)
  }

  @GetMapping("/{organizationId}/invitations")
  @Operation(summary = "Returns all invitations to organization")
  @NeedsSuperJwtToken
  fun getInvitations(@PathVariable("organizationId") id: Long):
    CollectionModel<OrganizationInvitationModel> {
    val organization = organizationService.find(id) ?: throw NotFoundException()
    organizationRoleService.checkUserIsOwner(id)
    return invitationService.getForOrganization(organization).let {
      organizationInvitationModelAssembler.toCollectionModel(it)
    }
  }

  @PutMapping("/{id:[0-9]+}/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  @Operation(summary = "Uploads organizations avatar")
  @ResponseStatus(HttpStatus.OK)
  fun uploadAvatar(
    @RequestParam("avatar") avatar: MultipartFile,
    @PathVariable id: Long
  ): OrganizationModel {
    organizationRoleService.checkUserIsOwner(id)
    imageUploadService.validateIsImage(avatar)
    val organization = organizationService.get(id)
    val roleType = organizationRoleService.getType(organization.id)
    organizationService.setAvatar(organization, avatar.inputStream)
    return organizationModelAssembler.toModel(OrganizationView.of(organization, roleType))
  }

  @DeleteMapping("/{id:[0-9]+}/avatar")
  @Operation(summary = "Deletes organization avatar")
  @ResponseStatus(HttpStatus.OK)
  fun removeAvatar(
    @PathVariable id: Long
  ): OrganizationModel {
    organizationRoleService.checkUserIsOwner(id)
    val organization = organizationService.get(id)
    val roleType = organizationRoleService.getType(organization.id)
    organizationService.removeAvatar(organization)
    return organizationModelAssembler.toModel(OrganizationView.of(organization, roleType))
  }

  @PutMapping("/{organizationId:[0-9]+}/set-base-permissions/{permissionType}")
  @Operation(summary = "Sets organization base permission")
  fun setBasePermissions(
    @PathVariable organizationId: Long,
    @PathVariable permissionType: ProjectPermissionType,
  ) {
    organizationRoleService.checkUserIsOwner(organizationId)
    organizationService.setBasePermission(organizationId, permissionType)
  }

  @GetMapping(value = ["/{organizationId:[0-9]+}/usage"])
  @Operation(description = "Returns current organization usage")
  fun getUsage(
    @PathVariable organizationId: Long
  ): UsageModel {
    val organization = organizationService.get(organizationId)
    organizationRoleService.checkUserIsMemberOrOwner(organizationId)
    val creditBalances = mtCreditBucketService.getCreditBalances(organization)
    val currentTranslations = organizationStatsService.getCurrentTranslationCount(organizationId)
    return UsageModel(
      organizationId = organizationId,
      creditBalance = creditBalances.creditBalance,
      includedMtCredits = creditBalances.bucketSize,
      extraCreditBalance = creditBalances.extraCreditBalance,
      creditBalanceRefilledAt = creditBalances.refilledAt.time,
      creditBalanceNextRefillAt = creditBalances.nextRefillAt.time,
      currentTranslations = currentTranslations,
      translationLimit = translationsLimitProvider.get(organization)
    )
  }

  private fun OrganizationView.toModel(): OrganizationModel {
    return this@OrganizationController.organizationModelAssembler.toModel(this)
  }
}
